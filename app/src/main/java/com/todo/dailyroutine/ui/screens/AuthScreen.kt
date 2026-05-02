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
import androidx.compose.ui.graphics.Brush
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
        // Deep Ambient Glow
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(AccentPrimary.copy(alpha = 0.08f), Color.Transparent),
                    center = Offset(size.width * 0.1f, size.height * 0.2f),
                    radius = size.maxDimension
                )
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .background(Color.White.copy(alpha = 0.03f), CircleShape)
                    .border(1.dp, Color.White.copy(alpha = 0.05f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Cyclone,
                    contentDescription = null,
                    modifier = Modifier.size(44.dp),
                    tint = AccentPrimary
                )
            }
            
            Spacer(Modifier.height(32.dp))
            
            Text(
                "FlowOS",
                style = Typography.displayLarge.copy(fontSize = 48.sp),
                color = Color.White,
                fontWeight = FontWeight.Black
            )
            
            Text(
                "Synchronize your biological state",
                style = Typography.labelLarge,
                color = TextSecondary,
                letterSpacing = 1.sp
            )

            Spacer(Modifier.height(64.dp))

            AnimatedContent(
                targetState = state.mode, 
                label = "AuthMode",
                transitionSpec = {
                    fadeIn(tween(500)) + slideInVertically { it / 2 } togetherWith
                    fadeOut(tween(500)) + slideOutVertically { -it / 2 }
                }
            ) { mode ->
                Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    when (mode) {
                        AuthMode.LOGIN, AuthMode.SIGNUP -> {
                            AuthTextField(email, { email = it }, "Identity (Email)", Icons.Default.AlternateEmail)
                            AuthTextField(password, { password = it }, "Security Key", Icons.Default.Security, isPassword = true)
                        }
                        AuthMode.OTP -> {
                            AuthTextField(otp, { otp = it }, "Verification Protocol", Icons.Default.Fingerprint)
                        }
                        AuthMode.FORGOT_PASSWORD -> {
                            AuthTextField(email, { email = it }, "Identity (Email)", Icons.Default.AlternateEmail)
                        }
                        AuthMode.RESET_PASSWORD -> {
                            AuthTextField(otp, { otp = it }, "Reset Code", Icons.Default.VpnKey)
                            AuthTextField(password, { password = it }, "New Security Key", Icons.Default.Security, isPassword = true)
                        }
                    }
                }
            }

            Spacer(Modifier.height(40.dp))

            if (state.error != null) ErrorCard(state.error!!, modifier = Modifier.padding(bottom = 20.dp))
            if (state.statusMessage != null) SuccessCard(state.statusMessage!!, modifier = Modifier.padding(bottom = 20.dp))
            
            PrimaryGradientButton(
                text = when (state.mode) {
                    AuthMode.LOGIN -> "Initiate Session"
                    AuthMode.SIGNUP -> "Register Identity"
                    AuthMode.OTP -> "Verify Protocol"
                    AuthMode.FORGOT_PASSWORD -> "Send Reset Code"
                    AuthMode.RESET_PASSWORD -> "Reset Password"
                },
                loading = state.loading,
                onClick = {
                    when (state.mode) {
                        AuthMode.LOGIN -> viewModel.signIn(email, password)
                        AuthMode.SIGNUP -> viewModel.signUp(email, password)
                        AuthMode.OTP -> viewModel.verifyOtp(otp)
                        AuthMode.FORGOT_PASSWORD -> viewModel.requestPasswordReset(email)
                        AuthMode.RESET_PASSWORD -> viewModel.resetPassword(otp, password)
                    }
                }
            )

            Row(
                modifier = Modifier.padding(top = 24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                when (state.mode) {
                    AuthMode.LOGIN -> {
                        TextButton(onClick = { viewModel.setMode(AuthMode.SIGNUP) }) {
                            Text("New Identity", color = AccentPrimary, fontWeight = FontWeight.Bold)
                        }
                        Text(
                            " | ", 
                            color = Color.White.copy(alpha = 0.1f),
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                        TextButton(onClick = { viewModel.setMode(AuthMode.FORGOT_PASSWORD) }) {
                            Text("Lost Key", color = TextSecondary)
                        }
                    }
                    AuthMode.SIGNUP -> {
                        TextButton(onClick = { viewModel.setMode(AuthMode.LOGIN) }) {
                            Text("Existing Identity", color = AccentPrimary, fontWeight = FontWeight.Bold)
                        }
                    }
                    else -> {
                        TextButton(onClick = { viewModel.setMode(AuthMode.LOGIN) }) {
                            Text("Return to Standby", color = TextSecondary)
                        }
                    }
                }
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

    TextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(label, color = Color.White.copy(alpha = 0.2f)) },
        leadingIcon = { Icon(icon, contentDescription = null, tint = AccentPrimary, modifier = Modifier.size(20.dp)) },
        trailingIcon = {
            if (isPassword) {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.3f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        },
        visualTransformation = if (isPassword && !passwordVisible) 
            androidx.compose.ui.text.input.PasswordVisualTransformation() 
            else androidx.compose.ui.text.input.VisualTransformation.None,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.White.copy(alpha = 0.03f),
            unfocusedContainerColor = Color.White.copy(alpha = 0.03f),
            focusedIndicatorColor = AccentPrimary,
            unfocusedIndicatorColor = Color.Transparent,
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            cursorColor = AccentPrimary
        ),
        singleLine = true
    )
}

@Composable
fun ErrorCard(message: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color(0xFF3B1A1A).copy(alpha = 0.5f),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color(0xFFFF5C5C).copy(alpha = 0.2f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Error, contentDescription = null, tint = Color(0xFFFF5C5C), modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(16.dp))
            Text(message, color = Color.White.copy(alpha = 0.8f), style = Typography.bodyMedium)
        }
    }
}

@Composable
fun SuccessCard(message: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color(0xFF1A3B1E).copy(alpha = 0.5f),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color(0xFF5CFF7C).copy(alpha = 0.2f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF5CFF7C), modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(16.dp))
            Text(message, color = Color.White.copy(alpha = 0.8f), style = Typography.bodyMedium)
        }
    }
}
