package com.todo.dailyroutine.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.todo.dailyroutine.ui.theme.*
import com.todo.dailyroutine.ui.viewmodel.DeepFlowViewModel

@Composable
fun DeepFlowScreen(onBack: () -> Unit) {
    val viewModel: DeepFlowViewModel = viewModel()
    val state by viewModel.uiState.collectAsState()
    
    val infiniteTransition = rememberInfiniteTransition()
    val glowScale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    Surface(modifier = Modifier.fillMaxSize(), color = BackgroundBase) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Flow Protocol", style = Typography.labelMedium, color = AccentPrimary)
                    Text(state.phase, style = Typography.headlineMedium, color = Color.White)
                }
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.Close, contentDescription = null, tint = Color.White.copy(alpha = 0.4f))
                }
            }

            // Central Focus Orb
            Box(contentAlignment = Alignment.Center, modifier = Modifier.weight(1f)) {
                // Background Glow
                Box(
                    modifier = Modifier
                        .size(280.dp)
                        .graphicsLayer(scaleX = glowScale, scaleY = glowScale)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(AccentPrimary.copy(alpha = 0.15f), Color.Transparent)
                            ),
                            shape = CircleShape
                        )
                )
                
                // Progress Circle
                CircularProgressIndicator(
                    progress = state.timeLeftSeconds.toFloat() / state.totalSeconds,
                    modifier = Modifier.size(240.dp),
                    strokeWidth = 4.dp,
                    color = AccentPrimary,
                    trackColor = Color.White.copy(alpha = 0.05f)
                )
                
                // Flow Logo with Timer
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    FlowLogo(
                        modifier = Modifier.size(160.dp),
                        showGlow = true
                    )
                    Spacer(Modifier.height(32.dp))
                    val minutes = state.timeLeftSeconds / 60
                    val seconds = state.timeLeftSeconds % 60
                    Text(
                        text = String.format("%02d:%02d", minutes, seconds),
                        style = Typography.displayLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 48.sp
                    )
                }
            }

            // Controls
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (!state.isActive) {
                    Button(
                        onClick = { viewModel.startFlow() },
                        modifier = Modifier.fillMaxWidth().height(64.dp),
                        shape = RoundedCornerShape(24.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentPrimary)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(12.dp))
                        Text("INITIATE PROTOCOL", fontWeight = FontWeight.Bold)
                    }
                } else {
                    OutlinedButton(
                        onClick = { viewModel.stopFlow() },
                        modifier = Modifier.fillMaxWidth().height(64.dp),
                        shape = RoundedCornerShape(24.dp),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                    ) {
                        Text("SUSPEND PROTOCOL")
                    }
                }
                
                Spacer(Modifier.height(24.dp))
                
                Text(
                    "Session Cycles: ${state.completedCycles}",
                    style = Typography.labelMedium,
                    color = TextSecondary
                )
            }
        }
    }
}
