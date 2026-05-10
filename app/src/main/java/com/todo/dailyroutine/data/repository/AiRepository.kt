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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.BufferedReader
import java.io.InputStreamReader
import com.todo.dailyroutine.data.ai.ProviderDetector
import com.todo.dailyroutine.data.ai.ProviderFactory
import com.todo.dailyroutine.data.ai.UniversalAIProvider
import kotlinx.coroutines.flow.flowOn

class AiRepository(
    private val aiApi: AiStudioApi,
    private val universalAiApi: UniversalAiApi
) {
    private val gson = Gson()

    /**
     * Detects provider and fetches available models for a given API key.
     */
    private val modelCache = mutableMapOf<String, List<ModelInfo>>()

    suspend fun detectProviderAndModels(apiKey: String): Pair<AiProviderConfig, List<ModelInfo>>? = withContext(Dispatchers.IO) {
        val input = apiKey.trim()
        val providerId = ProviderDetector.detect(input) ?: return@withContext null
        
        val baseUrl = if (input.startsWith("http")) {
            input // The input is the URL itself for local providers
        } else {
            ProviderDetector.getBaseUrl(providerId)
        }
        
        val provider = ProviderFactory.getProvider(providerId, universalAiApi)
        val providerName = provider.name
        
        val config = AiProviderConfig(
            providerId = providerId,
            providerName = providerName,
            apiKey = if (input.startsWith("http")) "" else input,
            baseUrl = baseUrl
        )
        
        val models = fetchModels(config)
        if (models.isNotEmpty()) {
            config to models
        } else {
            null
        }
    }

    /**
     * Fetches available models for a given provider config.
     */
    suspend fun fetchModels(config: AiProviderConfig, forceRefresh: Boolean = false): List<ModelInfo> = withContext(Dispatchers.IO) {
        val cacheKey = "${config.providerId}:${config.apiKey}"
        if (!forceRefresh && modelCache.containsKey(cacheKey)) {
            return@withContext modelCache[cacheKey] ?: emptyList()
        }

        try {
            val provider = ProviderFactory.getProvider(config.providerId, universalAiApi)
            val models = provider.fetchModels(config)
            if (models.isNotEmpty()) {
                modelCache[cacheKey] = models
            }
            models
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * Verifies if the API key and endpoint are functional.
     */
    suspend fun testConnection(config: AiProviderConfig): Result<Boolean> = withContext(Dispatchers.IO) {
        val provider = ProviderFactory.getProvider(config.providerId, universalAiApi)
        try {
            Result.success(provider.testConnection(config))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun generateWithDynamicConfig(
        config: AiProviderConfig,
        prompt: String,
        systemPrompt: String? = null,
        tools: List<Map<String, Any>>? = null,
        jsonMode: Boolean = false
    ): Result<String> = runCatching {
        val provider = ProviderFactory.getProvider(config.providerId, universalAiApi)
        provider.chat(config, prompt, systemPrompt, tools, jsonMode).getOrThrow()
    }

    suspend fun chat(
        prompt: String, 
        activeConfig: AiProviderConfig? = null, 
        systemPrompt: String? = null,
        tools: List<Map<String, Any>>? = null,
        jsonMode: Boolean = false
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val config = getSmartConfig(activeConfig)
            if (config != null) {
                generateWithDynamicConfig(config, prompt, systemPrompt, tools, jsonMode).getOrThrow()
            } else {
                throw Exception("No active AI configuration")
            }
        }
    }

    fun chatStream(
        prompt: String,
        activeConfig: AiProviderConfig? = null,
        systemPrompt: String? = null
    ): Flow<String> {
        val config = getSmartConfig(activeConfig) ?: return flow { emit("Error: No configuration") }
        val provider = ProviderFactory.getProvider(config.providerId, universalAiApi)
        return provider.chatStream(config, prompt, systemPrompt)
            .flowOn(Dispatchers.IO)
    }

    fun chatStreamWithContext(
        prompt: String,
        activeConfig: AiProviderConfig? = null,
        context: List<Map<String, String>> = emptyList()
    ): Flow<String> {
        val config = getSmartConfig(activeConfig) ?: return flow { emit("Error: No configuration") }
        val provider = ProviderFactory.getProvider(config.providerId, universalAiApi)
        return provider.chatStreamWithContext(config, context)
            .flowOn(Dispatchers.IO)
    }

    /**
     * Helpers for Model Tiering
     */
    fun getFastConfig(config: AiProviderConfig?): AiProviderConfig? {
        val base = config ?: return null
        return if (!base.fastModelId.isNullOrBlank()) {
            base.copy(selectedModelId = base.fastModelId)
        } else base
    }

    fun getSmartConfig(config: AiProviderConfig?): AiProviderConfig? {
        val base = config ?: return null
        return if (!base.smartModelId.isNullOrBlank()) {
            base.copy(selectedModelId = base.smartModelId)
        } else base
    }

    suspend fun transcribeAudio(file: java.io.File, config: AiProviderConfig?): Result<String> = withContext(Dispatchers.IO) {
        val activeConfig = config ?: return@withContext Result.failure(Exception("No API config for transcription"))
        
        val url = if (activeConfig.baseUrl.isNotBlank()) "${activeConfig.baseUrl.removeSuffix("/")}/audio/transcriptions" else "https://api.openai.com/v1/audio/transcriptions"
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

    fun formatSystemContext(context: SystemContext): String {
        return """
            SYSTEM CONTEXT (DEEP MEMORY):
            - Current Time: ${context.currentTime}
            - User Level: ${context.userLevel} (Flow Score: ${context.flowScore})
            - Active Tasks: ${context.tasks.filter { !it.completed }.joinToString { it.title }}
            - Habits: ${context.habits.joinToString { "${it.name} (Streak: ${it.streak})" }}
            - Recent Journal Vibes: ${context.recentMoods.joinToString()}
            - Last 3 Journal Insights: ${context.journalEntries.take(3).joinToString { it.aiInsight ?: "None" }}
        """.trimIndent()
    }

    suspend fun parseIntentWithContext(
        input: String, 
        context: SystemContext,
        activeConfig: AiProviderConfig?
    ): Result<ParsedIntent> = runCatching {
        val systemPrompt = """
            You are the Jarvis AI core of FlowOS. 
            You have deep access to the user's current state and history.
            ${formatSystemContext(context)}
            
            Based on this context and the user's input, parse their intent.
        """.trimIndent()
        
        val prompt = """
            Input: "$input"
            Return ONLY a JSON object:
            {
              "type": "task" | "habit" | "search" | "action",
              "title": "Cleaned title",
              "category": "Work" | "Personal" | "Health" | "Finance" | "Social" | "Other",
              "timeBlock": "Morning" | "Deep Work" | "Evening" | "Night" | null,
              "isRecurring": boolean,
              "searchQuery": "if search",
              "actionTargetId": "if referring to an existing item"
            }
        """.trimIndent()
        
        val res = chat(prompt, activeConfig, systemPrompt = systemPrompt, jsonMode = true).getOrThrow()
        val json = JSONObject(res)
        ParsedIntent(
            type = json.getString("type"),
            title = json.optString("title"),
            category = json.optString("category", "Other"),
            timeBlock = if (json.isNull("timeBlock")) null else json.optString("timeBlock"),
            isRecurring = json.optBoolean("isRecurring", false),
            searchQuery = json.optString("searchQuery")
        )
    }

    suspend fun generateNextBestAction(
        tasks: List<TaskItem>,
        habits: List<HabitItem>,
        level: String,
        xp: Int,
        config: AiProviderConfig?
    ): Result<String> = runCatching {
        val prompt = """
            You are an execution-first coach.
            Tasks: ${tasks.joinToString { it.title }}
            Habits: ${habits.joinToString { it.name }}
            Level: $level, XP: $xp
            Give one short mobile-friendly line: "Next best action: ..."
        """.trimIndent()
        chat(prompt, activeConfig = getFastConfig(config)).getOrThrow()
    }

    suspend fun parseIntent(input: String, activeConfig: AiProviderConfig?): Result<ParsedIntent> = runCatching {
        val prompt = """
            Parse input: "$input"
            Return ONLY JSON:
            {
              "type": "task" | "habit" | "search",
              "title": "Title",
              "category": "Work" | "Personal" | "Health" | "Finance" | "Social" | "Other",
              "timeBlock": "Morning" | "Deep Work" | "Evening" | "Night" | null,
              "isRecurring": boolean,
              "searchQuery": "search"
            }
        """.trimIndent()
        
        val res = chat(prompt, activeConfig, jsonMode = true).getOrThrow()
        val json = JSONObject(res)
        ParsedIntent(
            type = json.getString("type"),
            title = json.optString("title"),
            category = json.optString("category", "Other"),
            timeBlock = if (json.isNull("timeBlock")) null else json.optString("timeBlock"),
            isRecurring = json.optBoolean("isRecurring", false),
            searchQuery = json.optString("searchQuery")
        )
    }
}
