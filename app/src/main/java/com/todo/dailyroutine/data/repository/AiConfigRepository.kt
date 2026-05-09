package com.todo.dailyroutine.data.repository

import com.todo.dailyroutine.data.model.UserApiConfig
import com.todo.dailyroutine.data.session.SessionManager
import com.todo.dailyroutine.util.SecurityManager

import com.todo.dailyroutine.data.local.dao.AiConfigDao
import com.todo.dailyroutine.data.local.entity.LocalAiConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import java.util.UUID

class AiConfigRepository(
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
        apiKey = try { SecurityManager.decrypt(apiKeyEncrypted) } catch (e: Exception) { apiKeyEncrypted },
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
            Result.success(localConfigs.map { it.toModel() })
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
                apiKeyEncrypted = SecurityManager.encrypt(config.apiKey),
                model = config.model,
                isActive = config.isActive,
                lastUpdated = System.currentTimeMillis(),
                syncStatus = 0
            )
            
            if (config.isActive) {
                aiConfigDao.deactivateAll()
            }
            
            aiConfigDao.insertConfig(localConfig)
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
            Unit
        }
    }

    suspend fun deleteConfig(id: String): Result<Unit> {
        return runCatching {
            aiConfigDao.deleteConfigById(id)
            Unit
        }
    }

}
