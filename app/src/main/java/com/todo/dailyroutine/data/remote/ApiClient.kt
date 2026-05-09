package com.todo.dailyroutine.data.remote

import com.todo.dailyroutine.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {
    private val logger = HttpLoggingInterceptor().apply {
        // Only log headers to prevent body-related connection issues and memory pressure
        level = if (BuildConfig.DEBUG) {
            HttpLoggingInterceptor.Level.HEADERS
        } else {
            HttpLoggingInterceptor.Level.NONE
        }
    }

    private val okHttp = OkHttpClient.Builder()
        .addInterceptor(logger)
        .connectTimeout(300, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS)
        .writeTimeout(300, TimeUnit.SECONDS)
        .callTimeout(300, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .protocols(listOf(okhttp3.Protocol.HTTP_1_1)) // Force HTTP/1.1 to avoid HTTP/2 stream errors on Android
        .connectionPool(okhttp3.ConnectionPool(10, 5, TimeUnit.MINUTES))
        .build()

    private val aiRetrofit = Retrofit.Builder()
        .baseUrl("https://generativelanguage.googleapis.com/")
        .addConverterFactory(GsonConverterFactory.create())
        .client(okHttp)
        .build()

    val aiStudioApi: AiStudioApi = aiRetrofit.create(AiStudioApi::class.java)
    val universalAiApi: UniversalAiApi = aiRetrofit.create(UniversalAiApi::class.java)
}
