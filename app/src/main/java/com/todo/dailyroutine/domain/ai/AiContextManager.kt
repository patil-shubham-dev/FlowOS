package com.todo.dailyroutine.domain.ai

import com.todo.dailyroutine.data.local.dao.MessageDao
import com.todo.dailyroutine.data.local.dao.MemoryDao
import com.todo.dailyroutine.data.local.dao.SummaryDao
import com.todo.dailyroutine.data.local.entity.ConversationSummary
import com.todo.dailyroutine.data.local.entity.LocalMemory
import com.todo.dailyroutine.data.local.entity.LocalMessage
import com.todo.dailyroutine.data.repository.AiRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

class AiContextManager(
    private val aiRepository: AiRepository,
    private val messageDao: MessageDao,
    private val memoryDao: MemoryDao,
    private val summaryDao: SummaryDao
) {
    
    data class MemoryClassification(
        val shouldStore: Boolean,
        val type: String, // "preference", "fact", "goal", "task", "context"
        val importance: Float, // 0.0 - 1.0
        val summary: String
    )
    
    suspend fun getOptimizedContext(userId: String, currentQuery: String): List<Map<String, String>> = withContext(Dispatchers.IO) {
        val context = mutableListOf<Map<String, String>>()

        // 1. System Prompt & Intent Classification
        val intent = classifyIntent(currentQuery)
        val basePrompt = "You are FlowOS AI, a high-performance personal assistant. Intent detected: $intent. Respond concisely and actionably."
        context.add(mapOf("role" to "system", "content" to basePrompt))

        // 2. Long-Term Memory (Context-Aware Retrieval with Filtering)
        val relevantMemories = retrieveRelevantMemories(userId, currentQuery)
        if (relevantMemories.isNotEmpty()) {
            relevantMemories.forEach { memoryDao.reinforceMemory(it.id) }
            val memoryText = relevantMemories.joinToString("\n") { "- ${it.key}: ${it.value}" }
            context.add(mapOf("role" to "system", "content" to "User Context:\n$memoryText"))
        }

        // 3. Rolling Summary
        val summaryObj = summaryDao.getSummary(userId)
        summaryObj?.let {
            if (it.currentSummary.isNotBlank()) {
                context.add(mapOf("role" to "system", "content" to "Conversation History: ${it.currentSummary}"))
            }
        }

        // 4. Recent Messages (Short-Term Memory)
        val recent = messageDao.getRecentMessages(6).reversed()
        recent.forEach {
            context.add(mapOf("role" to it.role, "content" to it.content))
        }

        context
    }

    private suspend fun retrieveRelevantMemories(userId: String, query: String): List<LocalMemory> {
        val keywords = query.split(" ").filter { it.length > 3 }.take(3)
        val results = mutableListOf<LocalMemory>()
        
        keywords.forEach { word ->
            results.addAll(memoryDao.searchMemories("%$word%"))
        }
        
        // Filter and deduplicate
        return results
            .distinctBy { it.id }
            .filter { it.importanceScore >= 0.5f } // Only keep important memories
            .sortedByDescending { it.importanceScore }
            .take(3)
    }

    private fun classifyIntent(query: String): String {
        return when {
            query.contains("remind", true) || query.contains("set", true) || query.contains("schedule", true) -> "PLANNING"
            query.contains("how to", true) || query.contains("what is", true) || query.contains("explain", true) -> "INSTRUCTIONAL"
            query.contains("feel", true) || query.contains("tired", true) || query.contains("mood", true) -> "EMOTIONAL"
            query.contains("create", true) || query.contains("add", true) -> "CREATION"
            query.contains("complete", true) || query.contains("done", true) || query.contains("finish", true) -> "COMPLETION"
            else -> "GENERAL"
        }
    }

    suspend fun processNewMessage(userId: String, role: String, content: String) = withContext(Dispatchers.IO) {
        // Save message
        messageDao.insertMessage(LocalMessage(userId = userId, role = role, content = content))

        // Extract & Store Memory (only for user messages)
        if (role == "user") {
            val classification = classifyMessageForMemory(content)
            if (classification.shouldStore && classification.importance > 0.5f) {
                tryStoreMemory(userId, classification)
            }
            memoryDao.decayMemories()
        }

        // Trigger Summary if needed
        val currentState = summaryDao.getSummary(userId) ?: ConversationSummary(userId, "", 0, 0)
        val newCount = currentState.messageCountSinceSummary + 1
        if (newCount >= 12) {
            summarizeOldMessages(userId, currentState)
        } else {
            summaryDao.updateSummary(currentState.copy(messageCountSinceSummary = newCount))
        }
    }

    private fun classifyMessageForMemory(content: String): MemoryClassification {
        // Filter out noise
        val noisePatterns = listOf("ok", "thanks", "thank you", "yes", "no", "hello", "hi", "bye")
        if (noisePatterns.any { content.lowercase() == it }) {
            return MemoryClassification(shouldStore = false, type = "noise", importance = 0f, summary = "")
        }

        // Classify by pattern
        val (type, importance) = when {
            content.contains("I prefer", ignoreCase = true) -> Pair("preference", 0.9f)
            content.contains("My goal", ignoreCase = true) -> Pair("goal", 0.95f)
            content.contains("I work", ignoreCase = true) || content.contains("My job", ignoreCase = true) -> Pair("fact", 0.85f)
            content.contains("I wake up", ignoreCase = true) || content.contains("I sleep", ignoreCase = true) -> Pair("fact", 0.8f)
            content.contains("I like", ignoreCase = true) || content.contains("I love", ignoreCase = true) -> Pair("preference", 0.75f)
            content.contains("I hate", ignoreCase = true) || content.contains("I don't like", ignoreCase = true) -> Pair("preference", 0.7f)
            content.length > 100 -> Pair("context", 0.6f)
            else -> Pair("context", 0.4f)
        }

        return MemoryClassification(
            shouldStore = importance > 0.5f,
            type = type,
            importance = importance,
            summary = content.take(200)
        )
    }

    private suspend fun tryStoreMemory(userId: String, classification: MemoryClassification) {
        val key = classification.type
        val value = classification.summary
        
        // Check for duplicates/similar memories
        val existing = memoryDao.searchMemories("%${value.take(50)}%")
        if (existing.isNotEmpty()) {
            // Update existing memory instead of creating new
            val existingMemory = existing.first()
            memoryDao.saveMemory(
                existingMemory.copy(
                    importanceScore = maxOf(existingMemory.importanceScore, classification.importance),
                    timestamp = System.currentTimeMillis()
                )
            )
        } else {
            // Create new memory
            memoryDao.saveMemory(
                LocalMemory(
                    id = UUID.randomUUID().toString(),
                    userId = userId,
                    key = key,
                    value = value,
                    category = classification.type,
                    importanceScore = classification.importance,
                    timestamp = System.currentTimeMillis()
                )
            )
        }
    }

    private suspend fun summarizeOldMessages(userId: String, state: ConversationSummary) {
        val messages = messageDao.getMessagesAfter(state.lastSummarizedMessageId)
        if (messages.isEmpty()) return

        val conversationText = messages.joinToString("\n") { "${it.role}: ${it.content}" }
        val prompt = "Summarize the following interaction concisely, focusing on key decisions, user preferences, and recurring themes. Keep it under 100 words.\n\n$conversationText"

        val summary = aiRepository.chat(prompt).getOrNull() ?: state.currentSummary
        summaryDao.updateSummary(ConversationSummary(
            userId = userId,
            currentSummary = summary,
            lastSummarizedMessageId = messages.last().id,
            messageCountSinceSummary = 0
        ))
    }
}
