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
    suspend fun getOptimizedContext(userId: String, currentQuery: String): List<Map<String, String>> = withContext(Dispatchers.IO) {
        val context = mutableListOf<Map<String, String>>()

        // 1. System Prompt & Intent Classification
        val intent = classifyIntent(currentQuery)
        val basePrompt = "You are FlowOS AI. Intent detected: $intent. "
        context.add(mapOf("role" to "system", "content" to basePrompt))

        // 2. Layer 3: Long-Term Memory (Context-Aware Retrieval)
        val relevantMemories = if (currentQuery.length > 5) {
            val keywords = currentQuery.split(" ").filter { it.length > 3 }.take(3)
            val results = mutableListOf<LocalMemory>()
            keywords.forEach { word ->
                results.addAll(memoryDao.searchMemories("%$word%"))
            }
            results.distinctBy { it.id }.take(3)
        } else {
            memoryDao.getTopMemories().take(3)
        }

        if (relevantMemories.isNotEmpty()) {
            relevantMemories.forEach { memoryDao.reinforceMemory(it.id) }
            val memoryText = relevantMemories.joinToString("\n") { "- ${it.key}: ${it.value}" }
            context.add(mapOf("role" to "system", "content" to "Relevant user context:\n$memoryText"))
        }

        // 3. Layer 2: Rolling Summary
        val summaryObj = summaryDao.getSummary(userId)
        summaryObj?.let {
            context.add(mapOf("role" to "system", "content" to "Rolling History Summary: ${it.currentSummary}"))
        }

        // 4. Layer 1: Short-Term Memory (Recent 6 messages)
        val recent = messageDao.getRecentMessages(6).reversed()
        recent.forEach {
            context.add(mapOf("role" to it.role, "content" to it.content))
        }

        context
    }

    private fun classifyIntent(query: String): String {
        return when {
            query.contains("remind", true) || query.contains("set", true) -> "PLANNING"
            query.contains("how to", true) || query.contains("what is", true) -> "INSTRUCTIONAL"
            query.contains("feel", true) || query.contains("tired", true) -> "EMOTIONAL"
            else -> "GENERAL_CHAT"
        }
    }

    suspend fun processNewMessage(userId: String, role: String, content: String) = withContext(Dispatchers.IO) {
        // Save message
        messageDao.insertMessage(LocalMessage(userId = userId, role = role, content = content))

        // Extraction & Decay Logic
        if (role == "user") {
            tryExtractMemory(userId, content)
            memoryDao.decayMemories() // Decay importance of older memories
        }

        // Summary Trigger
        val currentState = summaryDao.getSummary(userId) ?: ConversationSummary(userId, "", 0, 0)
        val newCount = currentState.messageCountSinceSummary + 1
        if (newCount >= 12) {
            summarizeOldMessages(userId, currentState)
        } else {
            summaryDao.updateSummary(currentState.copy(messageCountSinceSummary = newCount))
        }
    }

    private suspend fun tryExtractMemory(userId: String, content: String) {
        // Advanced extraction logic: check for stable facts
        val factPatterns = listOf("I prefer", "My goal is", "I live in", "My job is", "I wake up at")
        if (factPatterns.any { content.contains(it, ignoreCase = true) }) {
            val key = content.split(" ").take(3).joinToString(" ")
            memoryDao.saveMemory(LocalMemory(
                id = UUID.randomUUID().toString(),
                userId = userId,
                key = key,
                value = content,
                importanceScore = 1.5f // Higher weight for explicit facts
            ))
        }
    }

    private suspend fun summarizeOldMessages(userId: String, state: ConversationSummary) {
        val messages = messageDao.getMessagesAfter(state.lastSummarizedMessageId)
        if (messages.isEmpty()) return

        val conversationText = messages.joinToString("\n") { "${it.role}: ${it.content}" }
        val prompt = "Summarize the following interaction concisely, focusing on key decisions and recurring themes. Keep it under 100 words.\n\n$conversationText"

        val summary = aiRepository.chat(prompt).getOrNull() ?: state.currentSummary
        summaryDao.updateSummary(ConversationSummary(
            userId = userId,
            currentSummary = summary,
            lastSummarizedMessageId = messages.last().id,
            messageCountSinceSummary = 0
        ))
    }
}
