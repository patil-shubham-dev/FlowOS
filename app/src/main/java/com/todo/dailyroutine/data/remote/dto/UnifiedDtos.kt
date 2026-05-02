package com.todo.dailyroutine.data.remote.dto

data class UnifiedAiResponse(
    val content: String,
    val model: String,
    val provider: String,
    val raw: String
)

data class AiModelInfo(
    val id: String,
    val name: String,
    val provider: String
)
