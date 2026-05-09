package com.todo.dailyroutine.domain.vector

import com.todo.dailyroutine.data.local.dao.MemoryDao
import com.todo.dailyroutine.data.local.entity.LocalMemory
import com.todo.dailyroutine.data.repository.AiRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SelfLearningEngine(
    private val memoryDao: MemoryDao,
    private val vectorMemoryManager: VectorMemoryManager,
    private val aiRepository: AiRepository
) {
    
    /**
     * Periodically runs to merge similar memories and detect patterns.
     */
    suspend fun consolidateMemories(userId: String) = withContext(Dispatchers.IO) {
        val memories = memoryDao.getAllMemories(userId)
        if (memories.size < 2) return@withContext

        val processedIds = mutableSetOf<String>()
        
        for (i in memories.indices) {
            val m1 = memories[i]
            if (m1.id in processedIds) continue
            
            val similarGroup = mutableListOf(m1)
            val e1 = vectorMemoryManager.vectorEngine.byteArrayToFloatArray(m1.embedding)
            
            for (j in i + 1 until memories.size) {
                val m2 = memories[j]
                if (m2.id in processedIds) continue
                
                val e2 = vectorMemoryManager.vectorEngine.byteArrayToFloatArray(m2.embedding)
                val similarity = vectorMemoryManager.vectorEngine.calculateCosineSimilarity(e1, e2)
                
                if (similarity > 0.65f) {
                    similarGroup.add(m2)
                    processedIds.add(m2.id)
                }
            }
            
            if (similarGroup.size > 1) {
                mergeMemories(userId, similarGroup)
            }
        }
        
        detectPatterns(userId, memories)
    }

    private suspend fun mergeMemories(userId: String, group: List<LocalMemory>) {
        val combinedText = group.joinToString("\n") { it.text }
        val mergePrompt = """
            The following observations are related. Merge them into a single, high-density insight.
            Observations:
            $combinedText
            
            Return ONLY the merged insight text.
        """.trimIndent()
        
        val mergedContent = aiRepository.chat(mergePrompt).getOrNull()
        if (mergedContent != null) {
            // Delete old memories
            group.forEach { memoryDao.deleteMemory(it.id) }
            
            // Store new merged memory
            vectorMemoryManager.storeMemory(
                userId = userId,
                text = mergedContent,
                type = group[0].type,
                importance = group.maxOf { it.importance }
            )
        }
    }

    /**
     * High-level maintenance task that runs multiple intelligence passes.
     */
    suspend fun triggerMaintenance(userId: String) = withContext(Dispatchers.IO) {
        consolidateMemories(userId)
        pruneOldMemories(userId)
    }

    private suspend fun pruneOldMemories(userId: String) = withContext(Dispatchers.IO) {
        val memories = memoryDao.getAllMemories(userId)
        if (memories.size < 50) return@withContext // Keep at least 50 memories
        
        val thirtyDaysAgo = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000)
        
        // Prune memories that are:
        // 1. Older than 30 days
        // 2. Have importance < 0.4
        // 3. Are not of type 'insight' (keep insights forever)
        val toPrune = memories.filter { 
            it.timestamp < thirtyDaysAgo && 
            it.importance < 0.4f && 
            it.type != "insight" 
        }
        
        toPrune.forEach { 
            memoryDao.deleteMemory(it.id) 
        }
    }

    private suspend fun detectPatterns(userId: String, memories: List<LocalMemory>) {
        if (memories.size < 5) return
        
        val allText = memories.joinToString("\n") { "[${it.type}] ${it.text}" }
        val patternPrompt = """
            Analyze these user memories as a behaviorist. Identify non-obvious patterns, 
            behavioral loops, or subconscious preferences.
            
            Memories:
            $allText
            
            If you find a deep insight (e.g. "User avoids High-Energy tasks on Mondays"), 
            return it as a single line starting with "INSIGHT: ". 
            Otherwise return "NONE".
        """.trimIndent()
        
        val patternResponse = aiRepository.chat(patternPrompt).getOrNull() ?: return
        if (patternResponse.startsWith("INSIGHT:")) {
            val insight = patternResponse.removePrefix("INSIGHT:").trim()
            
            // Check for similar existing insights to avoid duplication
            val existingInsights = memoryDao.getMemoriesByType(userId, "insight")
            val isDuplicate = existingInsights.any { 
                vectorMemoryManager.vectorEngine.calculateCosineSimilarity(
                    vectorMemoryManager.vectorEngine.generateEmbedding(insight),
                    vectorMemoryManager.vectorEngine.byteArrayToFloatArray(it.embedding)
                ) > 0.85f
            }
            
            if (!isDuplicate) {
                vectorMemoryManager.storeMemory(
                    userId = userId,
                    text = insight,
                    type = "insight",
                    importance = 0.9f
                )
            }
        }
    }
}
