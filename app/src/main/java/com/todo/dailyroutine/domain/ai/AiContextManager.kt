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
import com.todo.dailyroutine.data.repository.*
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import java.time.LocalDate

class AiContextManager(
    private val aiRepository: AiRepository,
    private val messageDao: MessageDao,
    private val summaryDao: SummaryDao,
    private val vectorMemoryManager: VectorMemoryManager,
    private val memoryPipeline: MemoryPipeline,
    private val taskRepository: TaskRepository,
    private val habitRepository: HabitRepository,
    private val journalRepository: JournalRepository,
    private val flowScoreRepository: FlowScoreRepository
) {
    
    suspend fun getOptimizedContext(userId: String, currentQuery: String): List<Map<String, String>> = withContext(Dispatchers.IO) {
        val context = mutableListOf<Map<String, String>>()

        // 1. System Prompt
        context.add(mapOf("role" to "system", "content" to "You are FlowOS Life Guidance Coach. Your purpose is to understand the user's life patterns, biological rhythms, and habits to guide them towards peak consistency and flow. You provide empathetic coaching and actionable advice based on their data. You stay within the FlowOS app boundaries and do not control external system settings like WiFi or DND."))

        // 2. Operational State (Live Snapshot)
        val activeTasks = taskRepository.tasks.first().filter { !it.completed }
        val rituals = habitRepository.habits.first()
        val latestJournal = journalRepository.getAllEntries().first().firstOrNull()
        val score = flowScoreRepository.getScoreForDate(userId, LocalDate.now().toString())
        val currentHour = java.time.LocalTime.now().hour
        val timeBlock = when (currentHour) {
            in 5..11 -> "Morning"
            in 12..16 -> "Deep Work"
            in 17..21 -> "Evening"
            else -> "Night"
        }

        val stateText = """
            PROTOCOL SNAPSHOT [${java.time.LocalDateTime.now()}]:
            - Phase: $timeBlock
            - Active Objectives: ${activeTasks.joinToString { "[${it.id}] ${it.title} (${it.category})" }}
            - Daily Rituals: ${rituals.joinToString { "${it.name} [${if (it.completedToday) "SYNCED" else "PENDING"}]" }}
            - Last Reflection: ${latestJournal?.content?.take(150)}...
            - Flow Score: ${score?.score ?: "CALIBRATING..."} (Progress: ${score?.tasksCompleted ?: 0}/${score?.totalTasks ?: 0})
        """.trimIndent()
        context.add(mapOf("role" to "system", "content" to stateText))

        // 3. Vector-Based Memory Retrieval (Top 5)
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
