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
import kotlinx.coroutines.flow.first
import java.util.UUID

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

    suspend fun getActiveConfig(): UserApiConfig? {
        return getActiveConfigLocal()
    }

    suspend fun getConfigs(): Result<List<UserApiConfig>> {
        return try {
            val localConfigs = aiConfigDao.getAllConfigs().first()
            if (localConfigs.isNotEmpty()) {
                Result.success(localConfigs.map { it.toModel() })
            } else {
                val userId = sessionManager.getUserId()
                    ?: return Result.failure(Exception("User not logged in"))
                val tokenValue = sessionManager.getToken()
                    ?: return Result.failure(Exception("Auth token missing"))
                
                val token = "Bearer $tokenValue"
                
                runCatching {
                    restApi.getApiConfigs(BuildConfig.SUPABASE_ANON_KEY, token, userIdFilter = "eq.$userId").map { it.toModel() }
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun saveConfig(config: UserApiConfig): Result<UserApiConfig> {
        val userId = sessionManager.getUserId() ?: "local_user"
        
        return runCatching {
            val configId = if (config.id.isEmpty()) UUID.randomUUID().toString() else config.id
            val localConfig = LocalAiConfig(
                id = configId,
                userId = userId,
                providerName = config.providerName,
                baseUrl = config.baseUrl,
                apiKeyEncrypted = config.apiKey,
                model = config.model,
                isActive = config.isActive,
                lastUpdated = System.currentTimeMillis(),
                syncStatus = 0
            )
            
            if (config.isActive) {
                aiConfigDao.deactivateAll()
            }
            
            aiConfigDao.insertConfig(localConfig)
            
            val tokenValue = sessionManager.getToken()
            if (tokenValue != null) {
                val token = "Bearer $tokenValue"
                val dto = config.toDto(userId)
                try {
                    restApi.createApiConfig(BuildConfig.SUPABASE_ANON_KEY, token, body = dto)
                } catch (e: Exception) {
                }
            }
            
            localConfig.toModel()
        }
    }

    suspend fun updateConfig(id: String, updates: Map<String, Any>): Result<Unit> {
        return runCatching {
            val config = aiConfigDao.getConfigById(id)
            if (config != null) {
                val updated = config.copy(
                    lastUpdated = System.currentTimeMillis(),
                    syncStatus = 0
                )
                aiConfigDao.insertConfig(updated)
            }
            
            val tokenValue = sessionManager.getToken()
            if (tokenValue != null) {
                val token = "Bearer $tokenValue"
                try {
                    restApi.updateApiConfig(BuildConfig.SUPABASE_ANON_KEY, token, idFilter = "eq.$id", body = updates)
                } catch (e: Exception) {
                }
            }
            Unit
        }
    }

    suspend fun deleteConfig(id: String): Result<Unit> {
        return runCatching {
            aiConfigDao.deleteConfigById(id)
            
            val tokenValue = sessionManager.getToken()
            if (tokenValue != null) {
                val token = "Bearer $tokenValue"
                try {
                    restApi.deleteApiConfig(BuildConfig.SUPABASE_ANON_KEY, token, idFilter = "eq.$id")
                } catch (e: Exception) {
                }
            }
            Unit
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
