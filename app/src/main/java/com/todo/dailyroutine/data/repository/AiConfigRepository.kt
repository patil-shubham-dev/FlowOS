package com.todo.dailyroutine.data.repository

import com.todo.dailyroutine.data.model.UserApiConfig
import com.todo.dailyroutine.data.remote.SupabaseRestApi
import com.todo.dailyroutine.data.remote.dto.UserApiConfigDto
import com.todo.dailyroutine.BuildConfig
import com.todo.dailyroutine.data.session.SessionManager

import com.todo.dailyroutine.data.local.dao.AiConfigDao
import com.todo.dailyroutine.data.local.entity.LocalAiConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class AiConfigRepository(
    private val restApi: SupabaseRestApi,
    private val aiConfigDao: AiConfigDao,
    private val sessionManager: SessionManager
) {
    fun getAllConfigsLocal(): Flow<List<UserApiConfig>> = 
        aiConfigDao.getAllConfigs().map { list -> list.map { it.toModel() } }

    suspend fun getActiveConfigLocal(): UserApiConfig? =
        aiConfigDao.getActiveConfig()?.toModel()

    private fun LocalAiConfig.toModel() = UserApiConfig(
        id = id,
        userId = userId,
        providerName = providerName,
        baseUrl = baseUrl,
        apiKey = apiKeyEncrypted,
        headersJson = "",
        model = model,
        isActive = isActive
    )
    suspend fun getConfigs(): Result<List<UserApiConfig>> {
        val userId = sessionManager.getUserId()
            ?: return Result.failure(Exception("User not logged in"))
        val tokenValue = sessionManager.getToken()
            ?: return Result.failure(Exception("Auth token missing"))
        
        val token = "Bearer $tokenValue"
        
        return runCatching {
            restApi.getApiConfigs(BuildConfig.SUPABASE_ANON_KEY, token, userIdFilter = "eq.$userId").map { it.toModel() }
        }
    }

    suspend fun saveConfig(config: UserApiConfig): Result<UserApiConfig> {
        val userId = sessionManager.getUserId()
            ?: return Result.failure(Exception("User not logged in"))
        val tokenValue = sessionManager.getToken()
            ?: return Result.failure(Exception("Auth token missing"))
            
        val token = "Bearer $tokenValue"
        val dto = config.toDto(userId)
        
        return runCatching {
            val response = restApi.createApiConfig(BuildConfig.SUPABASE_ANON_KEY, token, body = dto)
            response.first().toModel()
        }
    }

    suspend fun updateConfig(id: String, updates: Map<String, Any>): Result<Unit> {
        val tokenValue = sessionManager.getToken()
            ?: return Result.failure(Exception("Auth token missing"))
            
        val token = "Bearer $tokenValue"
        
        return runCatching {
            restApi.updateApiConfig(BuildConfig.SUPABASE_ANON_KEY, token, idFilter = "eq.$id", body = updates)
            Unit
        }
    }

    suspend fun deleteConfig(id: String): Result<Unit> {
        val tokenValue = sessionManager.getToken()
            ?: return Result.failure(Exception("Auth token missing"))
            
        val token = "Bearer $tokenValue"
        
        return runCatching {
            restApi.deleteApiConfig(BuildConfig.SUPABASE_ANON_KEY, token, idFilter = "eq.$id")
        }
    }

    private fun UserApiConfigDto.toModel() = UserApiConfig(
        id = id ?: "",
        userId = userId,
        providerName = providerName,
        baseUrl = baseUrl,
        apiKey = apiKeyEncrypted,
        headersJson = headersJson,
        model = model,
        isActive = isActive
    )

    private fun UserApiConfig.toDto(userId: String) = UserApiConfigDto(
        id = if (id.isEmpty()) null else id,
        userId = userId,
        providerName = providerName,
        baseUrl = baseUrl,
        apiKeyEncrypted = apiKey,
        headersJson = headersJson,
        model = model,
        isActive = isActive
    )
}
