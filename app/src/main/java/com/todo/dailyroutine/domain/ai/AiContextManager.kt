package com.todo.dailyroutine.domain.ai

import com.todo.dailyroutine.data.local.dao.MessageDao
import com.todo.dailyroutine.data.local.dao.SummaryDao
import com.todo.dailyroutine.data.local.entity.ConversationSummary
import com.todo.dailyroutine.data.local.entity.LocalMessage
import com.todo.dailyroutine.data.repository.AiRepository
import com.todo.dailyroutine.domain.vector.VectorMemoryManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class AiContextManager(
    private val aiRepository: AiRepository,
    private val messageDao: MessageDao,
    private val summaryDao: SummaryDao,
    private val vectorMemoryManager: VectorMemoryManager
) {
    
    suspend fun getOptimizedContext(userId: String, currentQuery: String): List<Map<String, String>> = withContext(Dispatchers.IO) {
        val context = mutableListOf<Map<String, String>>()

        // 1. System Prompt
        context.add(mapOf("role" to "system", "content" to "You are FlowOS AI, a production-grade intelligent assistant. Be concise, actionable, and professional."))

        // 2. Vector-Based Memory Retrieval (Top 5)
        val relevantMemories = vectorMemoryManager.retrieveRelevantMemories(userId, currentQuery)
        if (relevantMemories.isNotEmpty()) {
            val memoryText = relevantMemories.joinToString("\n") { "- [${it.type}]: ${it.content}" }
            context.add(mapOf("role" to "system", "content" to "Relevant User Context:\n$memoryText"))
        }

        // 3. Rolling Summary
        summaryDao.getSummary(userId)?.let {
            if (it.currentSummary.isNotBlank()) {
                context.add(mapOf("role" to "system", "content" to "Conversation History: ${it.currentSummary}"))
            }
        }

        // 4. Recent Messages
        val recent = messageDao.getRecentMessages(10).reversed()
        recent.forEach {
            context.add(mapOf("role" to it.role, "content" to it.content))
        }

        context
    }

    /**
     * Strict Memory Pipeline:
     * User Input -> AI Classification -> System Validation -> Storage
     */
    suspend fun processNewMessage(userId: String, role: String, content: String) = withContext(Dispatchers.IO) {
        messageDao.insertMessage(LocalMessage(userId = userId, role = role, content = content))

        if (role == "user") {
            // 1. AI Classification (Ask AI to identify facts/preferences)
            val classificationPrompt = """
                Analyze the user message and extract key facts or preferences for long-term memory.
                Return ONLY a JSON object with:
                {
                  "shouldStore": boolean,
                  "type": "preference" | "fact" | "goal" | "task",
                  "content": "concise summary",
                  "importance": float (0.0-1.0)
                }
                Message: "$content"
            """.trimIndent()
            
            val aiResponse = aiRepository.chat(classificationPrompt).getOrNull()
            
            // 2. System Validation & Storage
            aiResponse?.let {
                try {
                    val json = JSONObject(it)
                    val shouldStore = json.getBoolean("shouldStore")
                    val type = json.getString("type")
                    val memoryContent = json.getString("content")
                    val importance = json.getDouble("importance").toFloat()
                    
                    // Hard rules validation
                    if (shouldStore && memoryContent.length > 5 && importance > 0.4f) {
                        vectorMemoryManager.storeMemory(userId, memoryContent, type, importance)
                    }
                } catch (e: Exception) {
                    // Fail silently or log error
                }
            }
        }
    }
}
