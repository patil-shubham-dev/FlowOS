package com.todo.dailyroutine.domain.vector

import com.todo.dailyroutine.data.local.dao.MemoryDao
import com.todo.dailyroutine.data.local.entity.LocalMemory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

class VectorMemoryManager(
    private val memoryDao: MemoryDao,
    private val vectorEngine: VectorEngine
) {
    
    suspend fun storeMemory(userId: String, content: String, type: String, importance: Float) = withContext(Dispatchers.IO) {
        val embedding = vectorEngine.generateEmbedding(content)
        val allMemories = memoryDao.getAllMemories(userId)
        
        // Deduplication: Check for similarity > 0.9
        var existingMemory: LocalMemory? = null
        for (memory in allMemories) {
            val storedEmbedding = vectorEngine.jsonToFloatArray(memory.embedding)
            val similarity = vectorEngine.calculateCosineSimilarity(embedding, storedEmbedding)
            if (similarity > 0.9f) {
                existingMemory = memory
                break
            }
        }

        if (existingMemory != null) {
            // Update existing memory
            val updated = existingMemory.copy(
                content = content,
                importance = maxOf(existingMemory.importance, importance),
                timestamp = System.currentTimeMillis()
            )
            memoryDao.saveMemory(updated)
        } else {
            // Store new memory
            val newMemory = LocalMemory(
                id = UUID.randomUUID().toString(),
                userId = userId,
                content = content,
                embedding = vectorEngine.floatArrayToJson(embedding),
                type = type,
                importance = importance
            )
            memoryDao.saveMemory(newMemory)
        }
    }

    suspend fun retrieveRelevantMemories(userId: String, query: String, limit: Int = 5): List<LocalMemory> = withContext(Dispatchers.IO) {
        val queryEmbedding = vectorEngine.generateEmbedding(query)
        val allMemories = memoryDao.getAllMemories(userId)
        
        return@withContext allMemories
            .map { it to vectorEngine.calculateCosineSimilarity(queryEmbedding, vectorEngine.jsonToFloatArray(it.embedding)) }
            .filter { it.second > 0.3f } // Minimum relevance threshold
            .sortedByDescending { it.second }
            .take(limit)
            .map { it.first }
    }
}
