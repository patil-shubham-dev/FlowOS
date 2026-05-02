package com.todo.dailyroutine.domain.ai

import com.todo.dailyroutine.data.local.dao.MessageDao
import com.todo.dailyroutine.data.local.dao.SummaryDao
import com.todo.dailyroutine.data.local.entity.ConversationSummary
import com.todo.dailyroutine.data.local.entity.LocalMessage
import com.todo.dailyroutine.data.repository.AiRepository
import com.todo.dailyroutine.domain.vector.VectorMemoryManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.todo.dailyroutine.domain.vector.MemoryPipeline
import org.json.JSONObject

class AiContextManager(
    private val aiRepository: AiRepository,
    private val messageDao: MessageDao,
    private val summaryDao: SummaryDao,
    private val vectorMemoryManager: VectorMemoryManager,
    private val memoryPipeline: MemoryPipeline
) {
    
    suspend fun getOptimizedContext(userId: String, currentQuery: String): List<Map<String, String>> = withContext(Dispatchers.IO) {
        val context = mutableListOf<Map<String, String>>()

        // 1. System Prompt
        context.add(mapOf("role" to "system", "content" to "You are FlowOS AI, a production-grade intelligent assistant. Be concise, actionable, and professional."))

        // 2. Vector-Based Memory Retrieval (Top 5)
        val relevantMemories = vectorMemoryManager.retrieveRelevantMemories(userId, currentQuery)
        if (relevantMemories.isNotEmpty()) {
            val memoryText = relevantMemories.joinToString("\n") { "- [${it.type}]: ${it.text}" }
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
     * Processes new messages and triggers the Memory Pipeline.
     * Triggers rolling summarization every 12 messages.
     */
    suspend fun processNewMessage(userId: String, role: String, content: String) = withContext(Dispatchers.IO) {
        messageDao.insertMessage(LocalMessage(userId = userId, role = role, content = content))

        if (role == "user") {
            memoryPipeline.processAndStore(userId, content)
        }

        // Rolling Summarization: Trigger every 12 messages
        val messageCount = messageDao.getMessageCount(userId)
        if (messageCount % 12 == 0) {
            triggerSummarization(userId)
        }
    }

    private suspend fun triggerSummarization(userId: String) {
        val messages = messageDao.getRecentMessages(20).reversed()
        if (messages.size < 10) return

        val historyText = messages.joinToString("\n") { "${it.role}: ${it.content}" }
        val currentSummary = summaryDao.getSummary(userId)?.currentSummary ?: ""
        
        val summaryPrompt = """
            Provide a concise, high-density summary of the following conversation history.
            Incorporate relevant details from the previous summary if provided.
            
            Previous Summary: $currentSummary
            
            New History:
            $historyText
            
            Return ONLY the new summary text.
        """.trimIndent()

        val newSummaryContent = aiRepository.chat(summaryPrompt).getOrNull()
        if (newSummaryContent != null) {
            summaryDao.updateSummary(
                ConversationSummary(
                    userId = userId,
                    currentSummary = newSummaryContent,
                    lastUpdated = System.currentTimeMillis()
                )
            )
            // Optional: Prune old messages after summarization to keep DB clean
            // messageDao.pruneOldMessages(userId, 50)
        }
    }
}
