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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody

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
            apiKey.startsWith("AIza") -> "Google"
            else -> "OpenAI" // Default fallback
        }
    }

    /**
     * Fetches available models for a given provider.
     */
    suspend fun fetchModels(config: UserApiConfig): List<String> = withContext(Dispatchers.IO) {
        val provider = detectProvider(config.apiKey).lowercase()
        val headers = mutableMapOf<String, String>()
        
        when (provider) {
            "openai" -> headers["Authorization"] = "Bearer ${config.apiKey}"
            "anthropic" -> {
                headers["x-api-key"] = config.apiKey
                headers["anthropic-version"] = "2023-06-01"
            }
            "groq" -> headers["Authorization"] = "Bearer ${config.apiKey}"
            "nvidia" -> headers["Authorization"] = "Bearer ${config.apiKey}"
            "google" -> return@withContext listOf("gemini-1.5-flash", "gemini-1.5-pro")
        }

        val url = when (provider) {
            "openai" -> "https://api.openai.com/v1/models"
            "anthropic" -> "https://api.anthropic.com/v1/models" // Anthropic doesn't have a public models endpoint like OpenAI, usually hardcoded
            "groq" -> "https://api.groq.com/openai/v1/models"
            "nvidia" -> "https://integrate.api.nvidia.com/v1/models"
            else -> ""
        }

        if (url.isEmpty() || provider == "anthropic") {
            return@withContext getHardcodedModels(provider)
        }

        try {
            val response = universalAiApi.getModels(url, headers)
            if (response.isSuccessful) {
                val raw = response.body()?.string() ?: ""
                val json = JSONObject(raw)
                val data = json.getJSONArray("data")
                val models = mutableListOf<String>()
                for (i in 0 until data.length()) {
                    models.add(data.getJSONObject(i).getString("id"))
                }
                return@withContext models.filter { it.contains("gpt") || it.contains("claude") || it.contains("llama") || it.contains("mixtral") }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        return@withContext getHardcodedModels(provider)
    }

    private fun getHardcodedModels(provider: String): List<String> {
        return when (provider) {
            "openai" -> listOf("gpt-4o", "gpt-4-turbo", "gpt-3.5-turbo")
            "anthropic" -> listOf("claude-3-5-sonnet-20240620", "claude-3-opus-20240229", "claude-3-haiku-20240307")
            "groq" -> listOf("llama3-70b-8192", "llama3-8b-8192", "mixtral-8x7b-32768")
            "nvidia" -> listOf("meta/llama3-70b-instruct", "mistralai/mixtral-8x7b-instruct-v0.1")
            "google" -> listOf("gemini-1.5-flash", "gemini-1.5-pro")
            else -> listOf("gpt-3.5-turbo")
        }
    }

    suspend fun generateWithDynamicConfig(
        config: UserApiConfig,
        prompt: String,
        systemPrompt: String? = null,
        tools: List<Map<String, Any>>? = null
    ): Result<String> = runCatching {
        val headers = mutableMapOf<String, String>()
        val provider = detectProvider(config.apiKey).lowercase()
        
        when (provider) {
            "anthropic" -> {
                headers["x-api-key"] = config.apiKey
                headers["anthropic-version"] = "2023-06-01"
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
            "model" to (config.model ?: getHardcodedModels(provider).first()),
            "messages" to messages
        )

        if (tools != null) {
            body["tools"] = tools
            body["tool_choice"] = "auto"
        }

        val baseUrl = if (config.baseUrl.isNotBlank()) config.baseUrl else when(provider) {
            "openai" -> "https://api.openai.com/v1/chat/completions"
            "anthropic" -> "https://api.anthropic.com/v1/messages"
            "groq" -> "https://api.groq.com/openai/v1/chat/completions"
            "nvidia" -> "https://integrate.api.nvidia.com/v1/chat/completions"
            else -> "https://api.openai.com/v1/chat/completions"
        }

        val response = universalAiApi.genericChat(baseUrl, headers, body)
        if (!response.isSuccessful) {
            throw Exception("AI Provider Error (${response.code()}): ${response.errorBody()?.string()}")
        }

        response.body()?.string() ?: ""
    }

    suspend fun chat(
        prompt: String, 
        activeConfig: UserApiConfig? = null, 
        systemPrompt: String? = null,
        tools: List<Map<String, Any>>? = null
    ): Result<String> = runCatching {
        if (activeConfig != null) {
            generateWithDynamicConfig(activeConfig, prompt, systemPrompt, tools).getOrThrow()
        } else {
            // Use Google Gemini as default if no config provided
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
    /**
     * Verifies if the API key and endpoint are functional.
     */
    suspend fun testConnection(config: UserApiConfig): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val models = fetchModels(config)
            Result.success(models.isNotEmpty())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun extractContent(raw: String, apiKey: String): String {
        val provider = detectProvider(apiKey).lowercase()
        return try {
            val json = JSONObject(raw)
            when (provider) {
                "anthropic" -> {
                    val content = json.getJSONArray("content")
                    val sb = StringBuilder()
                    for (i in 0 until content.length()) {
                        val part = content.getJSONObject(i)
                        if (part.getString("type") == "text") {
                            sb.append(part.getString("text"))
                        }
                    }
                    sb.toString()
                }
                else -> {
                    val choices = json.getJSONArray("choices")
                    val message = choices.getJSONObject(0).getJSONObject("message")
                    if (message.has("content") && !message.isNull("content")) {
                        message.getString("content")
                    } else ""
                }
            }
        } catch (e: Exception) {
            raw
        }
    }

    fun extractToolCalls(raw: String, apiKey: String): List<AiToolCall> {
        val provider = detectProvider(apiKey).lowercase()
        val toolCalls = mutableListOf<AiToolCall>()
        try {
            val json = JSONObject(raw)
            
            if (provider == "anthropic") {
                val content = json.getJSONArray("content")
                for (i in 0 until content.length()) {
                    val part = content.getJSONObject(i)
                    if (part.getString("type") == "tool_use") {
                        toolCalls.add(AiToolCall(
                            id = part.getString("id"),
                            type = "function",
                            function = AiFunctionCall(
                                name = part.getString("name"),
                                arguments = part.getJSONObject("input").toString()
                            )
                        ))
                    }
                }
            } else {
                val choices = json.getJSONArray("choices")
                val message = choices.getJSONObject(0).getJSONObject("message")
                
                if (message.has("tool_calls")) {
                    val calls = message.getJSONArray("tool_calls")
                    for (i in 0 until calls.length()) {
                        val call = calls.getJSONObject(i)
                        val function = call.getJSONObject("function")
                        toolCalls.add(AiToolCall(
                            id = call.getString("id"),
                            type = call.getString("type"),
                            function = AiFunctionCall(
                                name = function.getString("name"),
                                arguments = function.getString("arguments")
                            )
                        ))
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return toolCalls
    }

    suspend fun transcribeAudio(file: java.io.File, config: UserApiConfig?): Result<String> = withContext(Dispatchers.IO) {
        val activeConfig = config ?: return@withContext Result.failure(Exception("No API config for transcription"))
        val provider = detectProvider(activeConfig.apiKey).lowercase()
        
        if (provider != "openai" && provider != "groq") {
            return@withContext Result.failure(Exception("Transcription not supported for $provider"))
        }

        val url = when(provider) {
            "openai" -> "https://api.openai.com/v1/audio/transcriptions"
            "groq" -> "https://api.groq.com/openai/v1/audio/transcriptions"
            else -> "https://api.openai.com/v1/audio/transcriptions"
        }

        val headers = mapOf("Authorization" to "Bearer ${activeConfig.apiKey}")
        
        val mediaType = "audio/*".toMediaTypeOrNull()
        val requestFile = file.asRequestBody(mediaType)
        val body = MultipartBody.Part.createFormData("file", file.name, requestFile)
        val model = "whisper-1".toRequestBody("text/plain".toMediaTypeOrNull())

        try {
            val response = universalAiApi.transcribe(url, headers, body, model)
            if (response.isSuccessful) {
                val raw = response.body()?.string() ?: ""
                val json = JSONObject(raw)
                Result.success(json.getString("text"))
            } else {
                Result.failure(Exception("Transcription failed: ${response.errorBody()?.string()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
