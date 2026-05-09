package com.todo.dailyroutine.data.ai

import com.todo.dailyroutine.data.model.AiProviderConfig
import com.todo.dailyroutine.data.model.ModelInfo
import com.todo.dailyroutine.data.remote.UniversalAiApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.collect
import org.json.JSONObject

interface UniversalAIProvider {
    val id: String
    val name: String
    val defaultBaseUrl: String

    suspend fun fetchModels(config: AiProviderConfig): List<ModelInfo>
    suspend fun testConnection(config: AiProviderConfig): Boolean
    suspend fun chat(
        config: AiProviderConfig,
        prompt: String,
        systemPrompt: String? = null,
        tools: List<Map<String, Any>>? = null,
        jsonMode: Boolean = false
    ): Result<String>
    
    fun chatStream(
        config: AiProviderConfig,
        prompt: String,
        systemPrompt: String? = null
    ): Flow<String>

    suspend fun supportsTools(): Boolean = false
}

abstract class BaseOpenAICompatibleProvider : UniversalAIProvider {
    override suspend fun testConnection(config: AiProviderConfig): Boolean {
        return try {
            fetchModels(config).isNotEmpty()
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun fetchModels(config: AiProviderConfig): List<ModelInfo> {
        val baseUrl = if (config.baseUrl.isNotBlank()) config.baseUrl.removeSuffix("/") else defaultBaseUrl.removeSuffix("/")
        val url = "$baseUrl/models"
        val response = getApi().getModels(url, mapOf("Authorization" to "Bearer ${config.apiKey}"))
        if (!response.isSuccessful) return emptyList()
        val raw = response.body()?.string() ?: return emptyList()
        val json = JSONObject(raw)
        val data = if (json.has("data")) json.getJSONArray("data") else return emptyList()
        val models = mutableListOf<ModelInfo>()
        for (i in 0 until data.length()) {
            val item = data.getJSONObject(i)
            val modelId = item.getString("id")
            val supportsVision = modelId.contains("vision") || modelId.contains("gpt-4o") || modelId.contains("claude-3-5")
            val supportsTools = !modelId.contains("instruct") && !modelId.contains("vision")
            models.add(ModelInfo(
                id = modelId, 
                displayName = modelId,
                supportsVision = supportsVision,
                supportsTools = supportsTools
            ))
        }
        return models
    }

    override suspend fun chat(config: AiProviderConfig, prompt: String, systemPrompt: String?, tools: List<Map<String, Any>>?, jsonMode: Boolean): Result<String> {
        // Optimization: Use internal streaming to avoid loading massive response bodies at once
        // which often causes "Software caused connection abort" on Android.
        val sb = StringBuilder()
        return try {
            chatStream(config, prompt, systemPrompt).collect { chunk ->
                sb.append(chunk)
            }
            val finalRes = sb.toString()
            if (finalRes.isBlank()) Result.failure(Exception("Empty response from provider"))
            else Result.success(finalRes)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun chatStream(config: AiProviderConfig, prompt: String, systemPrompt: String?): Flow<String> = flow {
        val baseUrl = if (config.baseUrl.isNotBlank()) config.baseUrl.removeSuffix("/") else defaultBaseUrl.removeSuffix("/")
        val url = "$baseUrl/chat/completions"
        val messages = mutableListOf<Map<String, String>>()
        if (systemPrompt != null) messages.add(mapOf("role" to "system", "content" to systemPrompt))
        messages.add(mapOf("role" to "user", "content" to prompt))
        
        val body = mutableMapOf<String, Any>(
            "model" to (config.selectedModelId ?: "gpt-4o"),
            "messages" to messages,
            "stream" to true,
            "temperature" to config.temperature,
            "top_p" to config.topP,
            "max_tokens" to config.maxTokens
        )

        val headers = mapOf(
            "Authorization" to "Bearer ${config.apiKey}",
            "Content-Type" to "application/json",
            "Connection" to "keep-alive"
        )

        try {
            val response = getApi().genericChatStream(url, headers, body)
            if (response.isSuccessful) {
                val reader = response.body()?.byteStream()?.bufferedReader() ?: return@flow
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val currentLine = line ?: continue
                    if (currentLine.startsWith("data: ")) {
                        val data = currentLine.removePrefix("data: ").trim()
                        if (data == "[DONE]") break
                        try {
                            val json = JSONObject(data)
                            val choices = json.optJSONArray("choices") ?: continue
                            if (choices.length() > 0) {
                                val delta = choices.getJSONObject(0).optJSONObject("delta") ?: continue
                                val content = delta.optString("content")
                                if (content.isNotEmpty()) emit(content)
                            }
                        } catch (e: Exception) {}
                    }
                }
            } else {
                emit("Error: ${response.errorBody()?.string() ?: "Stream failed"}")
            }
        } catch (e: Exception) {
            emit("Error: ${e.localizedMessage}")
        }
    }

    abstract fun getApi(): UniversalAiApi
}

// Concrete Implementations
class GenericProvider(override val id: String, override val name: String, override val defaultBaseUrl: String, private val api: UniversalAiApi) : BaseOpenAICompatibleProvider() {
    override fun getApi() = api
}

class AnthropicProvider(private val api: UniversalAiApi) : UniversalAIProvider {
    override val id = "anthropic"
    override val name = "Anthropic"
    override val defaultBaseUrl = "https://api.anthropic.com/v1"
    override suspend fun fetchModels(config: AiProviderConfig) = listOf(ModelInfo("claude-3-5-sonnet-20240620", "Claude 3.5 Sonnet", supportsVision = true, supportsTools = true))
    override suspend fun testConnection(config: AiProviderConfig) = config.apiKey.startsWith("sk-ant-")
    
    override suspend fun chat(config: AiProviderConfig, prompt: String, systemPrompt: String?, tools: List<Map<String, Any>>?, jsonMode: Boolean): Result<String> {
        val sb = StringBuilder()
        return try {
            chatStream(config, prompt, systemPrompt).collect { chunk ->
                sb.append(chunk)
            }
            val finalRes = sb.toString()
            if (finalRes.isBlank()) Result.failure(Exception("Anthropic returned empty response"))
            else Result.success(finalRes)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun chatStream(config: AiProviderConfig, prompt: String, systemPrompt: String?): Flow<String> = flow {
        val url = "$defaultBaseUrl/messages"
        val messages = listOf(mapOf("role" to "user", "content" to prompt))
        val body = mutableMapOf<String, Any>(
            "model" to (config.selectedModelId ?: "claude-3-5-sonnet-20240620"),
            "messages" to messages,
            "max_tokens" to config.maxTokens,
            "stream" to true
        )
        if (systemPrompt != null) body["system"] = systemPrompt

        val headers = mapOf(
            "x-api-key" to config.apiKey,
            "anthropic-version" to "2023-06-01",
            "Connection" to "keep-alive"
        )

        try {
            val response = api.genericChatStream(url, headers, body)
            if (response.isSuccessful) {
                val reader = response.body()?.byteStream()?.bufferedReader() ?: return@flow
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val currentLine = line ?: continue
                    if (currentLine.startsWith("data: ")) {
                        val data = currentLine.removePrefix("data: ").trim()
                        try {
                            val json = JSONObject(data)
                            if (json.optString("type") == "content_block_delta") {
                                val text = json.getJSONObject("delta").optString("text")
                                emit(text)
                            }
                        } catch (e: Exception) {}
                    }
                }
            } else {
                emit("Error: ${response.errorBody()?.string()}")
            }
        } catch (e: Exception) {
            emit("Error: ${e.localizedMessage}")
        }
    }
}

class GoogleGeminiProvider(private val api: UniversalAiApi) : UniversalAIProvider {
    override val id = "google"
    override val name = "Google Gemini"
    override val defaultBaseUrl = "https://generativelanguage.googleapis.com/v1beta"
    override suspend fun fetchModels(config: AiProviderConfig) = listOf(
        ModelInfo("gemini-1.5-flash", "Gemini 1.5 Flash", supportsVision = true),
        ModelInfo("gemini-1.5-pro", "Gemini 1.5 Pro", supportsVision = true)
    )
    override suspend fun testConnection(config: AiProviderConfig) = config.apiKey.startsWith("AIza")

    override suspend fun chat(config: AiProviderConfig, prompt: String, systemPrompt: String?, tools: List<Map<String, Any>>?, jsonMode: Boolean): Result<String> {
        val sb = StringBuilder()
        return try {
            chatStream(config, prompt, systemPrompt).collect { chunk ->
                sb.append(chunk)
            }
            val finalRes = sb.toString()
            if (finalRes.isBlank()) Result.failure(Exception("Gemini returned empty response"))
            else Result.success(finalRes)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun chatStream(config: AiProviderConfig, prompt: String, systemPrompt: String?): Flow<String> = flow {
        val model = config.selectedModelId ?: "gemini-1.5-pro"
        val url = "$defaultBaseUrl/models/$model:streamGenerateContent?key=${config.apiKey}&alt=sse"
        
        val contents = listOf(mapOf("parts" to listOf(mapOf("text" to prompt))))
        val body = mutableMapOf<String, Any>("contents" to contents)
        if (systemPrompt != null) {
            body["system_instruction"] = mapOf("parts" to listOf(mapOf("text" to systemPrompt)))
        }

        val headers = mapOf(
            "Content-Type" to "application/json",
            "Connection" to "keep-alive"
        )

        try {
            val response = api.genericChatStream(url, headers, body)
            if (response.isSuccessful) {
                val reader = response.body()?.byteStream()?.bufferedReader() ?: return@flow
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val currentLine = line ?: continue
                    if (currentLine.startsWith("data: ")) {
                        val data = currentLine.removePrefix("data: ").trim()
                        try {
                            val json = JSONObject(data)
                            val candidates = json.optJSONArray("candidates") ?: continue
                            if (candidates.length() > 0) {
                                val parts = candidates.getJSONObject(0).optJSONObject("content")?.optJSONArray("parts") ?: continue
                                if (parts.length() > 0) {
                                    val text = parts.getJSONObject(0).optString("text")
                                    if (text.isNotEmpty()) emit(text)
                                }
                            }
                        } catch (e: Exception) {}
                    }
                }
            } else {
                emit("Error: ${response.errorBody()?.string() ?: "Gemini Stream Error"}")
            }
        } catch (e: Exception) {
            emit("Error: ${e.localizedMessage}")
        }
    }
}

object ProviderDetector {
    fun detect(apiKey: String): String? {
        val input = apiKey.trim()
        return when {
            input.startsWith("sk-or-v1-") -> "openrouter"
            input.startsWith("sk-ant-") -> "anthropic"
            input.startsWith("gsk_") -> "groq"
            input.startsWith("xai-") -> "xai"
            input.startsWith("nvapi-") -> "nvidia"
            input.startsWith("sk-proj-") -> "openai"
            input.startsWith("sk-") -> "openai"
            input.startsWith("AIza") -> "google"
            input.startsWith("api-") && input.length > 30 -> "mistral"
            input.startsWith("http://") || input.startsWith("https://") -> {
                if (input.contains("11434")) "ollama" else if (input.contains("1234")) "lmstudio" else "custom"
            }
            else -> null
        }
    }

    fun getBaseUrl(providerId: String): String {
        return when (providerId) {
            "openai" -> "https://api.openai.com/v1"
            "openrouter" -> "https://openrouter.ai/api/v1"
            "anthropic" -> "https://api.anthropic.com/v1"
            "groq" -> "https://api.groq.com/openai/v1"
            "google" -> "https://generativelanguage.googleapis.com/v1beta"
            "deepseek" -> "https://api.deepseek.com/v1"
            "together" -> "https://api.together.xyz/v1"
            "fireworks" -> "https://api.fireworks.ai/inference/v1"
            "xai" -> "https://api.x.ai/v1"
            "perplexity" -> "https://api.perplexity.ai"
            "mistral" -> "https://api.mistral.ai/v1"
            "cohere" -> "https://api.cohere.ai/v1"
            "nvidia" -> "https://integrate.api.nvidia.com/v1"
            "cerebras" -> "https://api.cerebras.ai/v1"
            "sambanova" -> "https://api.sambanova.ai/v1"
            "moonshot" -> "https://api.moonshot.cn/v1"
            "qwen" -> "https://dashscope.aliyuncs.com/compatible-mode/v1"
            "ollama" -> "http://localhost:11434/v1"
            "lmstudio" -> "http://localhost:1234/v1"
            else -> ""
        }
    }
}

object ProviderFactory {
    fun getProvider(providerId: String, api: UniversalAiApi): UniversalAIProvider {
        return when (providerId) {
            "openai" -> GenericProvider("openai", "OpenAI", "https://api.openai.com/v1", api)
            "anthropic" -> AnthropicProvider(api)
            "google" -> GoogleGeminiProvider(api)
            "openrouter" -> GenericProvider("openrouter", "OpenRouter", "https://openrouter.ai/api/v1", api)
            "groq" -> GenericProvider("groq", "Groq", "https://api.groq.com/openai/v1", api)
            "deepseek" -> GenericProvider("deepseek", "DeepSeek", "https://api.deepseek.com/v1", api)
            "together" -> GenericProvider("together", "Together AI", "https://api.together.xyz/v1", api)
            "fireworks" -> GenericProvider("fireworks", "Fireworks AI", "https://api.fireworks.ai/inference/v1", api)
            "xai" -> GenericProvider("xai", "xAI (Grok)", "https://api.x.ai/v1", api)
            "perplexity" -> GenericProvider("perplexity", "Perplexity", "https://api.perplexity.ai", api)
            "mistral" -> GenericProvider("mistral", "Mistral AI", "https://api.mistral.ai/v1", api)
            "cohere" -> GenericProvider("cohere", "Cohere", "https://api.cohere.ai/v1", api)
            "nvidia" -> GenericProvider("nvidia", "NVIDIA NIM", "https://integrate.api.nvidia.com/v1", api)
            "cerebras" -> GenericProvider("cerebras", "Cerebras", "https://api.cerebras.ai/v1", api)
            "sambanova" -> GenericProvider("sambanova", "SambaNova", "https://api.sambanova.ai/v1", api)
            "moonshot" -> GenericProvider("moonshot", "Moonshot AI", "https://api.moonshot.cn/v1", api)
            "qwen" -> GenericProvider("qwen", "Alibaba Qwen", "https://dashscope.aliyuncs.com/compatible-mode/v1", api)
            "ollama" -> GenericProvider("ollama", "Ollama", "http://localhost:11434/v1", api)
            "lmstudio" -> GenericProvider("lmstudio", "LM Studio", "http://localhost:1234/v1", api)
            else -> GenericProvider("custom", "Custom Provider", "", api)
        }
    }
    
    val allProviders = listOf(
        "openai" to "OpenAI",
        "anthropic" to "Anthropic",
        "google" to "Google Gemini",
        "openrouter" to "OpenRouter",
        "groq" to "Groq",
        "nvidia" to "NVIDIA NIM",
        "together" to "Together AI",
        "fireworks" to "Fireworks AI",
        "deepseek" to "DeepSeek",
        "mistral" to "Mistral AI",
        "cohere" to "Cohere",
        "perplexity" to "Perplexity",
        "xai" to "xAI (Grok)",
        "cerebras" to "Cerebras",
        "sambanova" to "SambaNova",
        "moonshot" to "Moonshot AI",
        "qwen" to "Alibaba Qwen",
        "ollama" to "Ollama (Local)",
        "lmstudio" to "LM Studio",
        "custom" to "Custom (OpenAI Compatible)"
    )
}
