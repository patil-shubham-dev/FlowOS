package com.todo.dailyroutine.data.model

data class AiProviderConfig(
    val providerId: String,
    val providerName: String,
    val apiKey: String,
    val baseUrl: String,
    val selectedModelId: String? = null,
    val selectedModelName: String? = null,
    val temperature: Float = 0.7f,
    val maxTokens: Int = 4096,
    val topP: Float = 1.0f,
    val streamingEnabled: Boolean = true,
    val supportsVision: Boolean = false,
    val supportsTools: Boolean = false,
    val isActive: Boolean = false,
    val metadataJson: String? = null // For provider-specific extras like organization ID
)

data class ModelInfo(
    val id: String,
    val displayName: String,
    val contextWindow: Int? = null,
    val supportsVision: Boolean = false,
    val supportsTools: Boolean = false,
    val supportsEmbeddings: Boolean = false,
    val metadataJson: String? = null
)

data class ModelCache(
    val providerId: String,
    val models: List<ModelInfo>,
    val lastFetchedAt: Long = System.currentTimeMillis()
)
