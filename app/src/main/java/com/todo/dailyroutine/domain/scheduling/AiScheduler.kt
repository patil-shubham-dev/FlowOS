package com.todo.dailyroutine.domain.scheduling

import com.todo.dailyroutine.data.local.entity.LocalTask
import com.todo.dailyroutine.data.repository.AiRepository
import com.todo.dailyroutine.data.model.AiProviderConfig

class AiScheduler(private val aiRepository: AiRepository) {

    suspend fun optimizeSchedule(
        tasks: List<LocalTask>,
        activeConfig: AiProviderConfig?
    ): Result<List<Pair<String, Int>>> = runCatching {
        if (tasks.isEmpty()) return@runCatching emptyList()

        val taskList = tasks.joinToString("\n") { "- [${it.id}] ${it.title} (Energy: ${it.energyRequired})" }
        val prompt = """
            Analyze these tasks and order them for maximum productivity.
            Consider energy required and typical daily rhythm.
            Return ONLY a JSON list of objects with "id" and "priority" (1-100, 100 being highest).
            Tasks:
            $taskList
        """.trimIndent()

        val response = aiRepository.generateWithDynamicConfig(
            config = activeConfig ?: throw Exception("No AI config"),
            prompt = prompt
        ).getOrThrow()

        // Simple parsing for [ {"id": "...", "priority": 90}, ... ]
        // Using regex to be robust against AI chatter
        val regex = "\"id\"\\s*:\\s*\"([^\"]+)\"\\s*,\\s*\"priority\"\\s*:\\s*(\\d+)".toRegex()
        regex.findAll(response).map { match ->
            match.groupValues[1] to match.groupValues[2].toInt()
        }.toList()
    }
}
