package com.todo.dailyroutine.data.model

data class UniversalAiProvider(
    val baseUrl: String,
    val apiKey: String,
    val selectedModel: String,
    val providerName: String
)


data class ToolExecutionResult(
    val toolCallId: String,
    val role: String = "tool",
    val content: String
)
