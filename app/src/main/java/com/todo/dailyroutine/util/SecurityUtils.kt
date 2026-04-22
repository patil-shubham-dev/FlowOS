package com.todo.dailyroutine.util

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

object SecurityUtils {
    fun getEncryptedPrefs(context: Context) = EncryptedSharedPreferences.create(
        context,
        "secure_flow_prefs",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun saveApiKey(context: Context, providerId: String, apiKey: String) {
        getEncryptedPrefs(context).edit().putString("key_$providerId", apiKey).apply()
    }

    fun getApiKey(context: Context, providerId: String): String? {
        return getEncryptedPrefs(context).getString("key_$providerId", null)
    }
}
