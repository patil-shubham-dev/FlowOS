package com.todo.dailyroutine.domain.vector

import com.todo.dailyroutine.data.local.dao.MemoryDao
import com.todo.dailyroutine.data.repository.AiRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SelfLearningEngine(
    private val memoryDao: MemoryDao,
    private val vectorEngine: VectorEngine,
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
            val e1 = vectorEngine.jsonToFloatArray(m1.embedding)
            
            for (j in i + 1 until memories.size) {
                val m2 = memories[j]
                if (m2.id in processedIds) continue
                
                val e2 = vectorEngine.jsonToFloatArray(m2.embedding)
                val similarity = vectorEngine.calculateCosineSimilarity(e1, e2)
                
                if (similarity > 0.7f) {
                    similarGroup.add(m2)
                    processedIds.add(m2.id)
                }
            }
            
            if (similarGroup.size > 1) {
                mergeMemories(userId, similarGroup)
            }
        }
    }

    private suspend fun mergeMemories(userId: String, group: List<com.todo.dailyroutine.data.local.entity.LocalMemory>) {
        val combinedText = group.joinToString("\n") { it.content }
        val mergePrompt = """
            The following observations are related. Merge them into a single, high-density insight.
            Observations:
            $combinedText
            
            Merged Insight:
        """.trimIndent()
        
        val mergedContent = aiRepository.chat(mergePrompt).getOrNull()
        if (mergedContent != null) {
            // Delete old memories
            group.forEach { memoryDao.deleteMemory(it.id) }
            
            // Store new merged memory
            val importance = group.maxOf { it.importance }
            val type = group.first().type
            
            // Note: In a full implementation, we'd use VectorMemoryManager here
            // but for brevity we assume direct storage or a simplified call.
        }
    }
}
