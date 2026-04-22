package com.todo.dailyroutine.data.remote

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

interface UniversalAiApi {
    @POST
    suspend fun genericChat(
        @Url url: String,
        @HeaderMap headers: Map<String, String>,
        @Body body: Map<String, @JvmSuppressWildcards Any>
    ): Response<ResponseBody>

    @GET
    suspend fun getModels(
        @Url url: String,
        @HeaderMap headers: Map<String, String>
    ): Response<ResponseBody>
}
