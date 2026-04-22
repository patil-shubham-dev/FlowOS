package com.todo.dailyroutine.data.remote

import com.todo.dailyroutine.data.remote.dto.AiRequest
import com.todo.dailyroutine.data.remote.dto.AiResponse
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query

interface AiStudioApi {
    @POST("v1beta/models/gemini-1.5-flash:generateContent")
    suspend fun generatePlan(
        @Query("key") apiKey: String,
        @Body body: AiRequest
    ): AiResponse
}
