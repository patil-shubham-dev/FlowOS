package com.todo.dailyroutine.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.todo.dailyroutine.ui.viewmodel.*
import com.todo.dailyroutine.ui.components.*
import com.todo.dailyroutine.ui.theme.*

@Composable
fun AuthScreen(
    viewModel: AuthViewModel,
    onLoggedIn: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var otp by remember { mutableStateOf("") }

    LaunchedEffect(state.isLoggedIn) {
        if (state.isLoggedIn) onLoggedIn()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundBase)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(AccentPrimary.copy(alpha = 0.1f), Color.Transparent),
                        center = Offset(0f, 0f),
                        radius = 2500f
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Cyclone,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = AccentPrimary
            )
            
            Spacer(Modifier.height(24.dp))
            
            Text(
                "FlowOS",
                style = Typography.displayLarge,
                color = Color.White
            )
            
            Text(
                "Synchronize your biological state.",
                style = Typography.labelLarge,
                color = TextSecondary
            )

            Spacer(Modifier.height(56.dp))

            AnimatedContent(targetState = state.mode, label = "AuthMode") { mode ->
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    when (mode) {
                        AuthMode.LOGIN, AuthMode.SIGNUP -> {
                            AuthTextField(email, { email = it }, "Identity (Email)", Icons.Default.Email)
                            AuthTextField(password, { password = it }, "Security Key", Icons.Default.Lock, isPassword = true)
                        }
                        AuthMode.OTP -> {
                            AuthTextField(otp, { otp = it }, "Verification Protocol", Icons.Default.VerifiedUser)
                        }
                        else -> { }
                    }
                }
            }

            Spacer(Modifier.height(32.dp))

            if (state.error != null) ErrorCard(state.error!!, modifier = Modifier.padding(bottom = 16.dp))
            
            PrimaryGradientButton(
                text = when (state.mode) {
                    AuthMode.LOGIN -> "Initiate Session"
                    AuthMode.SIGNUP -> "Register Identity"
                    AuthMode.OTP -> "Verify Protocol"
                    else -> "Proceed"
                },
                loading = state.loading,
                onClick = {
                    when (state.mode) {
                        AuthMode.LOGIN -> viewModel.signIn(email, password)
                        AuthMode.SIGNUP -> viewModel.signUp(email, password)
                        AuthMode.OTP -> viewModel.verifyOtp(otp)
                        else -> {}
                    }
                }
            )

            TextButton(
                onClick = { 
                    viewModel.setMode(if (state.mode == AuthMode.LOGIN) AuthMode.SIGNUP else AuthMode.LOGIN)
                },
                modifier = Modifier.padding(top = 16.dp)
            ) {
                Text(
                    if (state.mode == AuthMode.LOGIN) "Create new identity" else "Already has identity",
                    color = TextSecondary
                )
            }
        }
    }
}

@Composable
fun AuthTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: ImageVector,
    isPassword: Boolean = false
) {
    var passwordVisible by remember { mutableStateOf(false) }

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        leadingIcon = { Icon(icon, contentDescription = null, tint = Color(0xFF7C5CFF)) },
        trailingIcon = {
            if (isPassword) {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        contentDescription = null
                    )
                }
            }
        },
        visualTransformation = if (isPassword && !passwordVisible) 
            androidx.compose.ui.text.input.PasswordVisualTransformation() 
            else androidx.compose.ui.text.input.VisualTransformation.None,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Color(0xFF7C5CFF),
            unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
            focusedLabelColor = Color(0xFF7C5CFF),
            unfocusedLabelColor = Color.White.copy(alpha = 0.4f),
            unfocusedTextColor = Color.White,
            focusedTextColor = Color.White
        ),
        singleLine = true
    )
}

@Composable
fun ErrorCard(message: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF3B1A1A)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Error, contentDescription = null, tint = Color(0xFFFF5C5C))
            Spacer(Modifier.width(12.dp))
            Text(message, color = Color(0xFFFFEAEA), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun SuccessCard(message: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A3B1E)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF5CFF7C))
            Spacer(Modifier.width(12.dp))
            Text(message, color = Color(0xFFEAFFEA), style = MaterialTheme.typography.bodyMedium)
        }
    }
}
