package com.todo.dailyroutine.data.remote.dto

data class AiRequest(
    val contents: List<AiContent>
)

data class AiContent(
    val parts: List<AiPart>
)

data class AiPart(
    val text: String
)

data class AiResponse(
    val candidates: List<AiCandidate>?
)

data class AiCandidate(
    val content: AiContent?
)
