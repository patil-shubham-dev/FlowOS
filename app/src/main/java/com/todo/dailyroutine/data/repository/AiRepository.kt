package com.todo.dailyroutine.data.repository

import com.todo.dailyroutine.BuildConfig
import com.google.gson.Gson
import com.todo.dailyroutine.data.model.*
import com.todo.dailyroutine.data.remote.AiStudioApi
import com.todo.dailyroutine.data.remote.UniversalAiApi
import com.todo.dailyroutine.data.remote.dto.*
import okhttp3.ResponseBody
import retrofit2.Response
import org.json.JSONObject

class AiRepository(
    private val aiApi: AiStudioApi,
    private val universalAiApi: UniversalAiApi
) {
    private val gson = Gson()

    /**
     * Detects the provider based on the API key format.
     */
    fun detectProvider(apiKey: String): String {
        return when {
            apiKey.startsWith("sk-ant-") -> "Anthropic"
            apiKey.startsWith("sk-") -> "OpenAI"
            apiKey.startsWith("gsk_") -> "Groq"
            apiKey.startsWith("nvapi-") -> "Nvidia"
            else -> "Google"
        }
    }

    /**
     * Fetches available models for a given provider.
     */
    suspend fun fetchModels(config: UserApiConfig): List<String> {
        val provider = detectProvider(config.apiKey).lowercase()
        return when (provider) {
            "openai" -> listOf("gpt-4o", "gpt-4-turbo", "gpt-3.5-turbo")
            "anthropic" -> listOf("claude-3-5-sonnet-20240620", "claude-3-opus-20240229", "claude-3-haiku-20240307")
            "groq" -> listOf("llama3-70b-8192", "llama3-8b-8192", "mixtral-8x7b-32768")
            "nvidia" -> listOf("meta/llama3-70b-instruct", "mistralai/mixtral-8x7b-instruct-v0.1")
            else -> listOf("gemini-1.5-flash", "gemini-1.5-pro")
        }
    }

    suspend fun generateWithDynamicConfig(
        config: UserApiConfig,
        prompt: String,
        systemPrompt: String? = null
    ): Result<String> = runCatching {
        val headers = mutableMapOf<String, String>()
        val provider = detectProvider(config.apiKey).lowercase()
        
        when (provider) {
            "anthropic" -> {
                headers["x-api-key"] = config.apiKey
                headers["anthropic-version"] = "2023-06-01"
            }
            "nvidia" -> {
                headers["Authorization"] = "Bearer ${config.apiKey}"
            }
            else -> {
                headers["Authorization"] = "Bearer ${config.apiKey}"
            }
        }

        headers["Content-Type"] = "application/json"

        val messages = mutableListOf<Map<String, String>>()
        if (systemPrompt != null) {
            messages.add(mapOf("role" to "system", "content" to systemPrompt))
        }
        messages.add(mapOf("role" to "user", "content" to prompt))

        val body = mutableMapOf<String, Any>(
            "model" to (config.model ?: "gpt-3.5-turbo"),
            "messages" to messages
        )

        val response = universalAiApi.genericChat(config.baseUrl, headers, body)
        if (!response.isSuccessful) {
            throw Exception("AI Provider Error (${response.code()}): ${response.errorBody()?.string()}")
        }

        val rawResponse = response.body()?.string() ?: ""
        extractContentFromRaw(rawResponse, provider)
    }

    private fun extractContentFromRaw(raw: String, provider: String): String {
        return try {
            val json = JSONObject(raw)
            when (provider) {
                "anthropic" -> {
                    val content = json.getJSONArray("content")
                    content.getJSONObject(0).getString("text")
                }
                else -> {
                    val choices = json.getJSONArray("choices")
                    choices.getJSONObject(0).getJSONObject("message").getString("content")
                }
            }
        } catch (e: Exception) {
            raw
        }
    }

    suspend fun chat(prompt: String, activeConfig: UserApiConfig? = null, systemPrompt: String? = null): Result<String> = runCatching {
        if (activeConfig != null) {
            generateWithDynamicConfig(activeConfig, prompt, systemPrompt).getOrThrow()
        } else {
            val response = aiApi.generatePlan(
                apiKey = BuildConfig.AI_STUDIO_API_KEY,
                body = AiRequest(
                    contents = listOf(
                        AiContent(parts = listOf(AiPart(text = (systemPrompt ?: "") + "\n\n" + prompt)))
                    )
                )
            )
            response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text.orEmpty()
        }
    }

    suspend fun generateNextBestAction(
        tasks: List<TaskItem>,
        habits: List<HabitItem>,
        level: String,
        xp: Int
    ): Result<String> = runCatching {
        val prompt = """
            You are an execution-first coach.
            Tasks: ${tasks.joinToString { it.title }}
            Habits: ${habits.joinToString { it.name }}
            Level: $level, XP: $xp
            Give one short mobile-friendly line: "Next best action: ..."
        """.trimIndent()
        chat(prompt).getOrThrow()
    }
}
