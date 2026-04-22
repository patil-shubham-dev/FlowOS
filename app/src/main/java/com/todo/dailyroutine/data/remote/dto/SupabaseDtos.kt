package com.todo.dailyroutine.data.remote.dto

import com.google.gson.annotations.SerializedName

data class AuthRequest(
    val email: String,
    val password: String
)

data class AuthSession(
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("token_type") val tokenType: String,
    val user: AuthUser
)

data class AuthUser(
    val id: String,
    val email: String
)

data class TaskDto(
    val id: String? = null,
    @SerializedName("user_id") val userId: String,
    val title: String,
    val category: String,
    val completed: Boolean = false,
    val priority: Int = 0
)

data class HabitDto(
    val id: String? = null,
    @SerializedName("user_id") val userId: String,
    val name: String,
    val streak: Int = 0,
    @SerializedName("completed_today") val completedToday: Boolean = false
)

data class UserApiConfigDto(
    val id: String? = null,
    @SerializedName("user_id") val userId: String,
    @SerializedName("provider_name") val providerName: String,
    @SerializedName("base_url") val baseUrl: String,
    @SerializedName("api_key_encrypted") val apiKeyEncrypted: String,
    @SerializedName("headers_json") val headersJson: String? = null,
    val model: String? = null,
    @SerializedName("is_active") val isActive: Boolean = false
)
