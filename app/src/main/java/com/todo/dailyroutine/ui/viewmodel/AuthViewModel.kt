package com.todo.dailyroutine.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.todo.dailyroutine.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class AuthMode { LOGIN, SIGNUP, OTP, FORGOT_PASSWORD, RESET_PASSWORD }

data class AuthUiState(
    val loading: Boolean = false,
    val isLoggedIn: Boolean = false,
    val userEmail: String? = null,
    val error: String? = null,
    val mode: AuthMode = AuthMode.LOGIN,
    val pendingEmail: String = "",
    val pendingPassword: String = "",
    val statusMessage: String? = null,
    val isAppLockEnabled: Boolean = false
)

class AuthViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    init {
        checkAuth()
    }

    private fun checkAuth() {
        viewModelScope.launch {
            val loggedIn = authRepository.isLoggedIn()
            val lockEnabled = authRepository.isAppLockEnabled()
            val email = authRepository.getUserEmail()
            _uiState.value = _uiState.value.copy(
                isLoggedIn = loggedIn,
                isAppLockEnabled = lockEnabled,
                userEmail = email
            )
        }
    }

    fun toggleAppLock(enabled: Boolean) {
        authRepository.setAppLockEnabled(enabled)
        _uiState.value = _uiState.value.copy(isAppLockEnabled = enabled)
    }

    fun setMode(mode: AuthMode) {
        _uiState.value = _uiState.value.copy(mode = mode, error = null, statusMessage = null)
    }

    fun signIn(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            _uiState.value = _uiState.value.copy(error = "Invalid email or password format")
            return
        }
        _uiState.value = _uiState.value.copy(loading = true, error = null)
        viewModelScope.launch {
            authRepository.signIn(email, password)
                .onSuccess { _uiState.value = AuthUiState(isLoggedIn = true, userEmail = email) }
                .onFailure { _uiState.value = _uiState.value.copy(loading = false, error = it.message ?: "Login failed") }
        }
    }

    fun signUp(email: String, password: String) {
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            _uiState.value = _uiState.value.copy(error = "Invalid email format")
            return
        }
        if (password.length < 6) {
            _uiState.value = _uiState.value.copy(error = "Password must be at least 6 characters")
            return
        }
        _uiState.value = _uiState.value.copy(loading = true, error = null)
        viewModelScope.launch {
            authRepository.signUp(email, password)
                .onSuccess { 
                    _uiState.value = _uiState.value.copy(
                        loading = false, 
                        mode = AuthMode.OTP, 
                        pendingEmail = email,
                        pendingPassword = password,
                        statusMessage = "OTP sent to your email"
                    ) 
                }
                .onFailure { _uiState.value = _uiState.value.copy(loading = false, error = it.message ?: "Signup failed") }
        }
    }

    fun verifyOtp(otp: String) {
        val email = _uiState.value.pendingEmail
        val password = _uiState.value.pendingPassword
        _uiState.value = _uiState.value.copy(loading = true, error = null)
        viewModelScope.launch {
            authRepository.verifyOtp(email, otp)
                .onSuccess { 
                    // After verification, sign the user in
                    signIn(email, password)
                }
                .onFailure { _uiState.value = _uiState.value.copy(loading = false, error = it.message ?: "Incorrect OTP") }
        }
    }

    fun requestPasswordReset(email: String) {
        if (email.isBlank()) {
            _uiState.value = _uiState.value.copy(error = "Email is required")
            return
        }
        _uiState.value = _uiState.value.copy(loading = true, error = null)
        viewModelScope.launch {
            authRepository.requestPasswordReset(email)
                .onSuccess { 
                    _uiState.value = _uiState.value.copy(
                        loading = false,
                        mode = AuthMode.RESET_PASSWORD,
                        pendingEmail = email,
                        statusMessage = "Reset code sent to your email"
                    )
                }
                .onFailure { _uiState.value = _uiState.value.copy(loading = false, error = it.message) }
        }
    }

    fun resetPassword(otp: String, newPassword: String) {
        val email = _uiState.value.pendingEmail
        if (otp.length != 6) {
            _uiState.value = _uiState.value.copy(error = "Enter 6-digit code")
            return
        }
        if (newPassword.length < 6) {
            _uiState.value = _uiState.value.copy(error = "Password too short")
            return
        }
        _uiState.value = _uiState.value.copy(loading = true, error = null)
        viewModelScope.launch {
            authRepository.resetPassword(email, otp, newPassword)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(
                        loading = false,
                        mode = AuthMode.LOGIN,
                        statusMessage = "Password reset successfully. Please login."
                    )
                }
                .onFailure { _uiState.value = _uiState.value.copy(loading = false, error = it.message) }
        }
    }

    fun signOut() {
        authRepository.signOut()
        _uiState.value = AuthUiState(isLoggedIn = false)
    }
}

class AuthViewModelFactory(
    private val repository: AuthRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return AuthViewModel(repository) as T
    }
}
