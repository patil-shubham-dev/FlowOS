package com.todo.dailyroutine.data.remote

import com.todo.dailyroutine.data.remote.dto.AuthRequest
import com.todo.dailyroutine.data.remote.dto.AuthSession
import com.todo.dailyroutine.data.remote.dto.HabitDto
import com.todo.dailyroutine.data.remote.dto.TaskDto
import com.todo.dailyroutine.data.remote.dto.UserApiConfigDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Query

interface SupabaseAuthApi {
    @POST("auth/v1/signup")
    suspend fun signUp(
        @Header("apikey") apiKey: String,
        @Body body: AuthRequest
    ): AuthSession

    @POST("auth/v1/token?grant_type=password")
    suspend fun signIn(
        @Header("apikey") apiKey: String,
        @Body body: AuthRequest
    ): AuthSession

    @POST("auth/v1/verify")
    suspend fun verifyOtp(
        @Header("apikey") apiKey: String,
        @Body body: Map<String, String>
    ): AuthSession

    @POST("auth/v1/otp")
    suspend fun sendOtp(
        @Header("apikey") apiKey: String,
        @Body body: Map<String, Any>
    ): okhttp3.ResponseBody
}

interface CustomAuthApi {
    @POST("functions/v1/auth-handler")
    suspend fun authAction(
        @Header("apikey") apiKey: String,
        @Body body: Map<String, String>
    ): Map<String, String>
}

interface SupabaseRestApi {
    @GET("rest/v1/tasks")
    suspend fun getTasks(
        @Header("apikey") apiKey: String,
        @Header("Authorization") bearer: String,
        @Query("select") select: String = "*",
        @Query("user_id") userIdFilter: String
    ): List<TaskDto>

    @POST("rest/v1/tasks")
    suspend fun createTask(
        @Header("apikey") apiKey: String,
        @Header("Authorization") bearer: String,
        @Header("Prefer") prefer: String = "return=representation",
        @Body body: TaskDto
    ): List<TaskDto>

    @PATCH("rest/v1/tasks")
    suspend fun updateTask(
        @Header("apikey") apiKey: String,
        @Header("Authorization") bearer: String,
        @Header("Prefer") prefer: String = "return=representation",
        @Query("id") idFilter: String,
        @Body body: Map<String, @JvmSuppressWildcards Any>
    ): List<TaskDto>

    @GET("rest/v1/habits")
    suspend fun getHabits(
        @Header("apikey") apiKey: String,
        @Header("Authorization") bearer: String,
        @Query("select") select: String = "*",
        @Query("user_id") userIdFilter: String
    ): List<HabitDto>

    @POST("rest/v1/habits")
    suspend fun createHabit(
        @Header("apikey") apiKey: String,
        @Header("Authorization") bearer: String,
        @Header("Prefer") prefer: String = "return=representation",
        @Body body: HabitDto
    ): List<HabitDto>

    @PATCH("rest/v1/habits")
    suspend fun updateHabit(
        @Header("apikey") apiKey: String,
        @Header("Authorization") bearer: String,
        @Header("Prefer") prefer: String = "return=representation",
        @Query("id") idFilter: String,
        @Body body: Map<String, @JvmSuppressWildcards Any>
    ): List<HabitDto>

    @retrofit2.http.DELETE("rest/v1/tasks")
    suspend fun deleteTask(
        @Header("apikey") apiKey: String,
        @Header("Authorization") bearer: String,
        @Query("id") idFilter: String
    )

    @retrofit2.http.DELETE("rest/v1/habits")
    suspend fun deleteHabit(
        @Header("apikey") apiKey: String,
        @Header("Authorization") bearer: String,
        @Query("id") idFilter: String
    )

    @GET("rest/v1/user_api_configs")
    suspend fun getApiConfigs(
        @Header("apikey") apiKey: String,
        @Header("Authorization") bearer: String,
        @Query("select") select: String = "*",
        @Query("user_id") userIdFilter: String
    ): List<UserApiConfigDto>

    @POST("rest/v1/user_api_configs")
    suspend fun createApiConfig(
        @Header("apikey") apiKey: String,
        @Header("Authorization") bearer: String,
        @Header("Prefer") prefer: String = "return=representation",
        @Body body: UserApiConfigDto
    ): List<UserApiConfigDto>

    @PATCH("rest/v1/user_api_configs")
    suspend fun updateApiConfig(
        @Header("apikey") apiKey: String,
        @Header("Authorization") bearer: String,
        @Header("Prefer") prefer: String = "return=representation",
        @Query("id") idFilter: String,
        @Body body: Map<String, @JvmSuppressWildcards Any>
    ): List<UserApiConfigDto>

    @retrofit2.http.DELETE("rest/v1/user_api_configs")
    suspend fun deleteApiConfig(
        @Header("apikey") apiKey: String,
        @Header("Authorization") bearer: String,
        @Query("id") idFilter: String
    )
}
