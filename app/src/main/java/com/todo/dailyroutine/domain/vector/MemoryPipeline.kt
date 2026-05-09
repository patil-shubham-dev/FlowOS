package com.todo.dailyroutine.domain.vector

import com.todo.dailyroutine.data.repository.AiRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class MemoryPipeline(
    private val aiRepository: AiRepository,
    private val aiConfigRepository: AiConfigRepository,
    private val vectorMemoryManager: VectorMemoryManager
) {
    
    /**
     * Processes user input through AI classification and system validation.
     */
    suspend fun processAndStore(userId: String, input: String) = withContext(Dispatchers.IO) {
        val config = aiConfigRepository.getActiveConfig() ?: return@withContext
        
        // 1. AI Classification with Structured Output
        val classificationPrompt = """
            Analyze the following user input and extract a structured memory insight.
            Input: "$input"
            
            Rules:
            - Store if it's a fact about the user, a preference, or a clear goal.
            - Do not store casual conversation or greetings.
            - Return ONLY a JSON object.
            
            JSON Structure:
            {
                "text": "The core fact or preference to remember",
                "type": "fact|preference|goal|context",
                "importance": 0.1 to 1.0,
                "shouldStore": true|false
            }
        """.trimIndent()
        
        val aiResponse = aiRepository.chat(classificationPrompt, config, jsonMode = true).getOrNull() ?: return@withContext
        
        val classification = try {
            val json = JSONObject(aiResponse)
            MemoryInsight(
                text = json.getString("text"),
                type = json.getString("type"),
                importance = json.getDouble("importance").toFloat(),
                shouldStore = json.getBoolean("shouldStore")
            )
        } catch (e: Exception) {
            // Fallback: handle cases where LLM might still wrap in markdown or return invalid format
            try {
                val cleaned = aiResponse.substringAfter("{").substringBeforeLast("}") + "}"
                val json = JSONObject(cleaned)
                MemoryInsight(
                    text = json.getString("text"),
                    type = json.getString("type"),
                    importance = json.getDouble("importance").toFloat(),
                    shouldStore = json.getBoolean("shouldStore")
                )
            } catch (e2: Exception) {
                null
            }
        } ?: return@withContext

        // 2. System Validation (Hard Rules)
        if (!classification.shouldStore) return@withContext
        if (classification.text.length < 5) return@withContext
        
        // 3. Storage (includes deduplication)
        vectorMemoryManager.storeMemory(
            userId = userId,
            text = classification.text,
            type = classification.type,
            importance = classification.importance
        )
    }

    private data class MemoryInsight(
        val text: String,
        val type: String,
        val importance: Float,
        val shouldStore: Boolean
    )
}
