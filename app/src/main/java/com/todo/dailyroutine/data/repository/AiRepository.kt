package com.todo.dailyroutine.data.repository

import com.todo.dailyroutine.BuildConfig
import com.google.gson.Gson
import com.todo.dailyroutine.data.model.AiReminderPlan
import com.todo.dailyroutine.data.model.AiRoutineResponse
import com.todo.dailyroutine.data.model.HabitItem
import com.todo.dailyroutine.data.model.TaskItem
import com.todo.dailyroutine.data.remote.AiStudioApi
import com.todo.dailyroutine.data.remote.dto.AiContent
import com.todo.dailyroutine.data.remote.dto.AiPart
import com.todo.dailyroutine.data.remote.dto.AiRequest

import com.todo.dailyroutine.data.model.UserApiConfig
import com.todo.dailyroutine.data.remote.UniversalAiApi
import okhttp3.ResponseBody
import retrofit2.Response

class AiRepository(
    private val aiApi: AiStudioApi,
    private val universalAiApi: UniversalAiApi
) {
    private val gson = Gson()

    suspend fun generateWithDynamicConfig(
        config: UserApiConfig,
        prompt: String
    ): Result<String> = runCatching {
        val headers = mutableMapOf<String, String>()
        
        // Base headers based on provider name hints
        val provider = config.providerName.lowercase()
        when {
            provider.contains("anthropic") || provider.contains("claude") -> {
                headers["x-api-key"] = config.apiKey
                headers["anthropic-version"] = "2023-06-01"
            }
            else -> {
                headers["Authorization"] = "Bearer ${config.apiKey}"
            }
        }

        // Add custom JSON headers
        config.headersJson?.let {
            try {
                val custom: Map<String, String> = gson.fromJson(it, object : com.google.gson.reflect.TypeToken<Map<String, String>>() {}.type)
                headers.putAll(custom)
            } catch (e: Exception) {
                // Ignore malformed custom headers
            }
        }
        headers["Content-Type"] = "application/json"

        // Build a standard chat completion body (most providers like OpenAI, Groq, Together use this)
        val body = mutableMapOf<String, Any>(
            "model" to (config.model ?: "gpt-3.5-turbo"),
            "messages" to listOf(
                mapOf("role" to "user", "content" to prompt)
            )
        )

        val response = universalAiApi.genericChat(config.baseUrl, headers, body)
        if (!response.isSuccessful) {
            throw Exception("AI Provider Error (${response.code()}): ${response.errorBody()?.string()}")
        }

        val rawResponse = response.body()?.string() ?: ""
        extractContentFromRaw(rawResponse)
    }

    private fun extractContentFromRaw(raw: String): String {
        return try {
            // Try OpenAI format: choices[0].message.content
            val json = gson.fromJson(raw, Map::class.java)
            val choices = json["choices"] as? List<*>
            val firstChoice = choices?.firstOrNull() as? Map<*, *>
            val message = firstChoice?.get("message") as? Map<*, *>
            (message?.get("content") as? String) ?: raw
        } catch (e: Exception) {
            raw // Fallback to raw text
        }
    }

    suspend fun generateRoutineAdvice(prompt: String, activeConfig: UserApiConfig? = null): Result<AiRoutineResponse> = runCatching {
        val systemPrompt = """
            You are FlowOS Oracle, a high-performance productivity AI. 
            Format your response as valid JSON:
            {
              "advice": "your concise coaching advice here",
              "reminder": {
                "enabled": true,
                "hour24": 14,
                "minute": 30,
                "title": "Protocol Check",
                "body": "Optimization window active."
              }
            }
        """.trimIndent()
        
        val fullPrompt = "$systemPrompt\n\nUser Input: $prompt"

        val raw = if (activeConfig != null) {
            generateWithDynamicConfig(activeConfig, fullPrompt).getOrThrow()
        } else {
            val response = aiApi.generatePlan(
                apiKey = BuildConfig.AI_STUDIO_API_KEY,
                body = AiRequest(
                    contents = listOf(
                        AiContent(parts = listOf(AiPart(text = fullPrompt)))
                    )
                )
            )
            response.candidates
                ?.firstOrNull()
                ?.content
                ?.parts
                ?.firstOrNull()
                ?.text
                .orEmpty()
        }

        if (raw.isBlank()) {
            return@runCatching AiRoutineResponse(
                advice = "Flow state optimization unavailable. Check network connectivity.",
                reminderPlan = null
            )
        }
        parseAiResponse(raw)
    }

    suspend fun chat(prompt: String, activeConfig: UserApiConfig? = null): Result<String> = runCatching {
        if (activeConfig != null) {
            generateWithDynamicConfig(activeConfig, prompt).getOrThrow()
        } else {
            val response = aiApi.generatePlan(
                apiKey = BuildConfig.AI_STUDIO_API_KEY,
                body = AiRequest(
                    contents = listOf(
                        AiContent(parts = listOf(AiPart(text = prompt)))
                    )
                )
            )
            response.candidates
                ?.firstOrNull()
                ?.content
                ?.parts
                ?.firstOrNull()
                ?.text
                .orEmpty()
        }
    }

    suspend fun chatWithTools(
        prompt: String,
        tools: List<Map<String, Any>>,
        activeConfig: UserApiConfig? = null
    ): Result<String> = runCatching {
        if (activeConfig == null) {
            // Logic for default Gemini if it supported tools, else fallback
            return chat(prompt, null)
        }
        
        val headers = mutableMapOf<String, String>()
        val provider = activeConfig.providerName.lowercase()
        when {
            provider.contains("anthropic") || provider.contains("claude") -> {
                headers["x-api-key"] = activeConfig.apiKey
                headers["anthropic-version"] = "2023-06-01"
            }
            else -> headers["Authorization"] = "Bearer ${activeConfig.apiKey}"
        }
        headers["Content-Type"] = "application/json"

        val body = mutableMapOf<String, Any>(
            "model" to (activeConfig.model ?: "gpt-3.5-turbo"),
            "messages" to listOf(mapOf("role" to "user", "content" to prompt)),
            "tools" to tools,
            "tool_choice" to "auto"
        )

        val response = universalAiApi.genericChat(activeConfig.baseUrl, headers, body)
        if (!response.isSuccessful) throw Exception("AI Tool Error: ${response.code()}")
        
        response.body()?.string() ?: ""
    }

    suspend fun generateNextBestAction(
        tasks: List<TaskItem>,
        habits: List<HabitItem>,
        level: String,
        xp: Int
    ): Result<String> = runCatching {
        val completion = if (tasks.isNotEmpty()) {
            ((tasks.count { it.completed } * 100) / tasks.size)
        } else {
            0
        }
        val prompt = """
            You are an execution-first coach.
            Context:
            - time of day: ${java.time.LocalTime.now()}
            - tasks today: ${tasks.joinToString { "${it.title}:${if (it.completed) "done" else "pending"}" }}
            - habits: ${habits.joinToString { "${it.name}:streak_${it.streak}:${if (it.completedToday) "done" else "pending"}" }}
            - progress: $completion%
            - xp: $xp
            - level: $level

            Give exactly one short mobile-friendly line:
            "Next best action: ..."
        """.trimIndent()
        val response = aiApi.generatePlan(
            apiKey = BuildConfig.AI_STUDIO_API_KEY,
            body = AiRequest(contents = listOf(AiContent(parts = listOf(AiPart(prompt)))))
        )
        response.candidates
            ?.firstOrNull()
            ?.content
            ?.parts
            ?.firstOrNull()
            ?.text
            .orEmpty()
            .ifBlank { "Next best action: Complete your highest-impact pending task now." }
    }

    private fun parseAiResponse(raw: String): AiRoutineResponse {
        return try {
            val parsed = gson.fromJson(raw, AiResponseWrapper::class.java)
            AiRoutineResponse(
                advice = parsed.advice.ifBlank { "Keep momentum with one small win now." },
                reminderPlan = parsed.reminder?.toModel()
            )
        } catch (_: Exception) {
            AiRoutineResponse(advice = raw, reminderPlan = null)
        }
    }

    private data class AiResponseWrapper(
        val advice: String = "",
        val reminder: ReminderWrapper? = null
    )

    private data class ReminderWrapper(
        val enabled: Boolean = false,
        val hour24: Int = 8,
        val minute: Int = 0,
        val title: String = "Daily Routine Check-in",
        val body: String = "Open the app and complete your next action."
    ) {
        fun toModel(): AiReminderPlan {
            return AiReminderPlan(
                enabled = enabled,
                hour24 = hour24.coerceIn(0, 23),
                minute = minute.coerceIn(0, 59),
                title = title,
                body = body
            )
        }
    }
}
