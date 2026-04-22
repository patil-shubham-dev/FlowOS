package com.todo.dailyroutine.data.remote

import com.todo.dailyroutine.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {
    private val logger = HttpLoggingInterceptor().apply {
        level = if (BuildConfig.DEBUG) {
            HttpLoggingInterceptor.Level.BODY
        } else {
            HttpLoggingInterceptor.Level.NONE
        }
    }

    private val okHttp = OkHttpClient.Builder()
        .addInterceptor(logger)
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val supabaseRetrofit = Retrofit.Builder()
        .baseUrl("${BuildConfig.SUPABASE_URL}/")
        .addConverterFactory(GsonConverterFactory.create())
        .client(okHttp)
        .build()

    private val aiRetrofit = Retrofit.Builder()
        .baseUrl("https://generativelanguage.googleapis.com/")
        .addConverterFactory(GsonConverterFactory.create())
        .client(okHttp)
        .build()

    val supabaseAuthApi: SupabaseAuthApi = supabaseRetrofit.create(SupabaseAuthApi::class.java)
    val customAuthApi: CustomAuthApi = supabaseRetrofit.create(CustomAuthApi::class.java)
    val supabaseRestApi: SupabaseRestApi = supabaseRetrofit.create(SupabaseRestApi::class.java)
    val aiStudioApi: AiStudioApi = aiRetrofit.create(AiStudioApi::class.java)
    val universalAiApi: UniversalAiApi = supabaseRetrofit.create(UniversalAiApi::class.java) // Uses the same base okHttp
}
