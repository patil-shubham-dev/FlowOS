package com.todo.dailyroutine.data.session

import android.content.Context
import com.google.gson.Gson
import com.todo.dailyroutine.data.model.AiProviderConfig
import org.json.JSONObject

/**
 * Manages local user session and configuration.
 */
class SessionManager(private val context: Context) {
    private val gson = Gson()
    private val prefs = context.getSharedPreferences("session_prefs", Context.MODE_PRIVATE)

    fun getAiConfig(): AiProviderConfig {
        val jsonString = prefs.getString("ai_config_json", null)
        if (jsonString == null) return fallbackConfig()
        
        return try {
            val jsonObject = JSONObject(jsonString)
            AiProviderConfig(
                providerId = jsonObject.getString("providerId"),
                providerName = jsonObject.getString("providerName"),
                apiKey = jsonObject.getString("apiKey"),
                baseUrl = jsonObject.optString("baseUrl", ""),
                selectedModelId = if (jsonObject.isNull("selectedModelId")) null else jsonObject.getString("selectedModelId"),
                selectedModelName = if (jsonObject.isNull("selectedModelName")) null else jsonObject.getString("selectedModelName"),
                temperature = jsonObject.optDouble("temperature", 0.7).toFloat(),
                maxTokens = jsonObject.optInt("maxTokens", 4096),
                topP = jsonObject.optDouble("topP", 1.0).toFloat(),
                streamingEnabled = jsonObject.optBoolean("streamingEnabled", true)
            )
        } catch (e: Exception) {
            fallbackConfig()
        }
    }

    private fun fallbackConfig() = AiProviderConfig(
        providerId = "google",
        providerName = "Google Gemini",
        apiKey = "••••••••",
        baseUrl = "https://generativelanguage.googleapis.com/v1beta",
        selectedModelId = "gemini-1.5-flash",
        selectedModelName = "Gemini 1.5 Flash",
        temperature = 0.3f,
        maxTokens = 512
    )

    fun setAiConfig(config: AiProviderConfig) {
        val json = gson.toJson(config)
        prefs.edit().putString("ai_config_json", json).apply()
    }

    fun updateAiConfig(update: (AiProviderConfig) -> AiProviderConfig) {
        val current = getAiConfig()
        val updated = update(current)
        setAiConfig(updated)
    }

    fun getUserId(): String = "user"
    fun getToken(): String = "local_token"
    fun getEmail(): String = "local@flowos.ai"
    fun isLoggedIn(): Boolean = true
    fun getDisplayName(): String = prefs.getString("display_name", "Shubham Patil") ?: "Shubham Patil"
    fun setDisplayName(name: String) = prefs.edit().putString("display_name", name).apply()
    fun getCoreGoal(): String = prefs.getString("core_goal", "Master Full-Stack Dev") ?: "Master Full-Stack Dev"
    fun setCoreGoal(goal: String) = prefs.edit().putString("core_goal", goal).apply()
    fun getAppearance(): String = prefs.getString("appearance", "Obsidian Dark") ?: "Obsidian Dark"
    fun setAppearance(value: String) = prefs.edit().putString("appearance", value).apply()
    fun isNotificationsEnabled(): Boolean = prefs.getBoolean("notifications", true)
    fun setNotificationsEnabled(enabled: Boolean) = prefs.edit().putBoolean("notifications", enabled).apply()
    fun setAppLockEnabled(enabled: Boolean) = prefs.edit().putBoolean("app_lock_enabled", enabled).apply()
    fun isAppLockEnabled(): Boolean = prefs.getBoolean("app_lock_enabled", false)
    fun getAiProvider(): String = getAiConfig().providerName
    fun clearSession() { /* No-op */ }
    
    // Compatibility aliases
    fun userId(): String = "user"
    fun token(): String = "local_token"
    fun email(): String = "local@flowos.ai"
}
