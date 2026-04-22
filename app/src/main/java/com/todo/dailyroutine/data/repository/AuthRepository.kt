package com.todo.dailyroutine.data.repository

import com.todo.dailyroutine.BuildConfig
import com.todo.dailyroutine.data.model.AppUser
import com.todo.dailyroutine.data.remote.CustomAuthApi
import com.todo.dailyroutine.data.remote.SupabaseAuthApi
import com.todo.dailyroutine.data.remote.dto.AuthRequest
import com.todo.dailyroutine.data.session.SessionManager

class AuthRepository(
    private val authApi: SupabaseAuthApi,
    private val customAuthApi: CustomAuthApi,
    private val sessionManager: SessionManager
) {
    suspend fun signUp(email: String, password: String): Result<String> {
        return try {
            val response = customAuthApi.authAction(
                BuildConfig.SUPABASE_ANON_KEY,
                mapOf("action" to "signup", "email" to email, "password" to password)
            )
            if (response["success"] == "true" || response["success"] == true.toString() || response.containsKey("message")) {
                Result.success(response["message"] ?: "OTP sent to your email")
            } else {
                Result.failure(Exception(response["error"] ?: "Failed to process request"))
            }
        } catch (e: Exception) {
            Result.failure(Exception(extractErrorMessage(e)))
        }
    }

    suspend fun sendOtp(email: String): Result<String> {
        return try {
            authApi.sendOtp(
                BuildConfig.SUPABASE_ANON_KEY,
                mapOf("email" to email, "create_user" to true)
            )
            Result.success("OTP sent to $email")
        } catch (e: Exception) {
            Result.failure(Exception(extractErrorMessage(e)))
        }
    }

    suspend fun verifyOtp(email: String, otp: String): Result<String> {
        return try {
            val response = customAuthApi.authAction(
                BuildConfig.SUPABASE_ANON_KEY,
                mapOf("action" to "verify", "email" to email, "otp" to otp)
            )
            if (response["success"] == "true" || response["success"] == true.toString()) {
                Result.success(response["message"] ?: "Verified successfully")
            } else {
                Result.failure(Exception(response["error"] ?: "Verification failed"))
            }
        } catch (e: Exception) {
            Result.failure(Exception(extractErrorMessage(e)))
        }
    }

    suspend fun requestPasswordReset(email: String): Result<String> {
        return try {
            val response = customAuthApi.authAction(
                BuildConfig.SUPABASE_ANON_KEY,
                mapOf("action" to "forgot-password", "email" to email)
            )
            if (response["success"] == "true" || response["success"] == true.toString()) {
                Result.success(response["message"] ?: "Reset code sent to your email")
            } else {
                Result.failure(Exception(response["error"] ?: "Failed to send reset code"))
            }
        } catch (e: Exception) {
            Result.failure(Exception(extractErrorMessage(e)))
        }
    }

    suspend fun resetPassword(email: String, otp: String, newPassword: String): Result<String> {
        return try {
            val response = customAuthApi.authAction(
                BuildConfig.SUPABASE_ANON_KEY,
                mapOf(
                    "action" to "reset-password",
                    "email" to email,
                    "otp" to otp,
                    "password" to newPassword
                )
            )
            if (response["success"] == "true" || response["success"] == true.toString()) {
                Result.success(response["message"] ?: "Password reset successfully")
            } else {
                Result.failure(Exception(response["error"] ?: "Failed to reset password"))
            }
        } catch (e: Exception) {
            Result.failure(Exception(extractErrorMessage(e)))
        }
    }

    suspend fun signIn(email: String, password: String): Result<AppUser> {
        return try {
            val session = authApi.signIn(BuildConfig.SUPABASE_ANON_KEY, AuthRequest(email, password))
            sessionManager.saveSession(session.user.id, session.accessToken, session.user.email)
            Result.success(AppUser(session.user.id, session.user.email))
        } catch (e: Exception) {
            Result.failure(Exception(extractErrorMessage(e)))
        }
    }

    private fun extractErrorMessage(e: Exception): String {
        return if (e is retrofit2.HttpException) {
            try {
                val errorBody = e.response()?.errorBody()?.string()
                val map = com.google.gson.Gson().fromJson(errorBody, Map::class.java)
                (map["error"] as? String) ?: e.message()
            } catch (ex: Exception) {
                e.message()
            }
        } else {
            e.message ?: "An unexpected error occurred"
        }
    }

    fun signOut() = sessionManager.clearSession()
    fun isLoggedIn(): Boolean = sessionManager.isLoggedIn()
    
    fun setAppLockEnabled(enabled: Boolean) = sessionManager.setAppLockEnabled(enabled)
    fun isAppLockEnabled(): Boolean = sessionManager.isAppLockEnabled()
    fun getUserEmail(): String? = sessionManager.getEmail()
}
