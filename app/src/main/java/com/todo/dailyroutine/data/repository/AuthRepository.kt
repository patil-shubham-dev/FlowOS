package com.todo.dailyroutine.data.repository

import com.todo.dailyroutine.data.model.AppUser
import com.todo.dailyroutine.data.session.SessionManager

class AuthRepository(
    private val sessionManager: SessionManager
) {
    suspend fun signUp(email: String, password: String): Result<String> {
        return try {
            // Local-only: save session immediately
            val userId = java.util.UUID.randomUUID().toString()
            sessionManager.saveSession(userId, "local_token", email)
            Result.success("Account created locally")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun sendOtp(email: String): Result<String> {
        return Result.success("Local mode: OTP not required. Use any code to verify.")
    }

    suspend fun verifyOtp(email: String, otp: String): Result<String> {
        return Result.success("Verified locally")
    }

    suspend fun requestPasswordReset(email: String): Result<String> {
        return Result.success("Local mode: Password reset simulated.")
    }

    suspend fun resetPassword(email: String, otp: String, newPassword: String): Result<String> {
        return Result.success("Password updated locally")
    }

    suspend fun signIn(email: String, password: String): Result<AppUser> {
        return try {
            val userId = "local_user_${email.hashCode()}"
            sessionManager.saveSession(userId, "local_token", email)
            Result.success(AppUser(userId, email))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }


    fun signOut() = sessionManager.clearSession()
    fun isLoggedIn(): Boolean = sessionManager.isLoggedIn()
    
    fun setAppLockEnabled(enabled: Boolean) = sessionManager.setAppLockEnabled(enabled)
    fun isAppLockEnabled(): Boolean = sessionManager.isAppLockEnabled()
    fun getUserEmail(): String? = sessionManager.getEmail()
}
