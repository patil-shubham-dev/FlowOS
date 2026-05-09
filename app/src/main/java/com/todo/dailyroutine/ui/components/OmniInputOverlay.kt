package com.todo.dailyroutine.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.*
import androidx.compose.animation.core.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.todo.dailyroutine.ui.theme.*
import com.todo.dailyroutine.ui.viewmodel.OmniViewModel

@Composable
fun OmniInputOverlay(viewModel: OmniViewModel) {
    val state by viewModel.uiState.collectAsState()
    
    AnimatedVisibility(
        visible = state.isOpen,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f))
                .padding(24.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 100.dp),
                shape = RoundedCornerShape(24.dp),
                color = SurfaceElevated,
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                tonalElevation = 12.dp
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = AccentPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Box(modifier = Modifier.weight(1f)) {
                            if (state.query.isEmpty()) {
                                Text(
                                    "Awaiting command...",
                                    color = TextTertiary,
                                    style = Typography.bodyLarge
                                )
                            }
                            BasicTextField(
                                value = state.query,
                                onValueChange = { viewModel.updateQuery(it) },
                                textStyle = Typography.bodyLarge.copy(color = Color.White),
                                cursorBrush = SolidColor(AccentPrimary),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = { viewModel.executeIntent() }),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        
                        IconButton(onClick = { viewModel.toggleVoice() }) {
                            val icon = if (state.isListening) Icons.Default.MicNone else Icons.Default.Mic
                            val tint by animateColorAsState(
                                if (state.isListening) AccentPrimary else Color.White.copy(alpha = 0.4f),
                                label = "mic_tint"
                            )
                            Icon(icon, contentDescription = null, tint = tint)
                        }
                    }

                    if (state.isListening) {
                        JarvisWaveAnimation(modifier = Modifier.fillMaxWidth().height(40.dp).padding(top = 8.dp))
                    }
                    
                    if (state.isProcessing) {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp)
                                .height(2.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = AccentPrimary,
                            trackColor = Color.Transparent
                        )
                    }

                    state.lastResult?.let { result ->
                        Text(
                            text = result,
                            color = SuccessGreen,
                            style = Typography.labelSmall,
                            modifier = Modifier.padding(top = 12.dp)
                        )
                    }
                    
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Try: 'Gym daily at 8am' or 'Buy coffee tomorrow'",
                        style = Typography.labelSmall,
                        color = TextTertiary
                    )
                }
            }
        }
    }
}

@Composable
fun JarvisWaveAnimation(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "jarvis_wave")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing)),
        label = "phase"
    )

    androidx.compose.foundation.Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val centerY = height / 2
        
        for (i in 0..4) {
            val path = androidx.compose.ui.graphics.Path()
            path.moveTo(0f, centerY)
            val amplitude = (height / 3) * (1f - i * 0.2f)
            val frequency = 0.02f + i * 0.01f
            
            for (x in 0..width.toInt() step 5) {
                val y = centerY + amplitude * kotlin.math.sin(frequency * x + phase + i * 0.5f)
                path.lineTo(x.toFloat(), y)
            }
            
            drawPath(
                path = path,
                color = AccentPrimary.copy(alpha = 0.2f + (1f - i * 0.2f) * 0.4f),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
            )
        }
    }
}
