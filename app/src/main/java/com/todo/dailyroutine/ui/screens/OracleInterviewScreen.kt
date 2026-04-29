package com.todo.dailyroutine.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.todo.dailyroutine.ui.components.PrimaryGradientButton
import com.todo.dailyroutine.ui.theme.*

@Composable
fun OracleInterviewScreen(onFinished: () -> Unit) {
    var step by remember { mutableIntStateOf(0) }
    val questions = listOf(
        "Identity verified. I am the Oracle. What is your primary objective for this cycle?",
        "Understood. On a scale of 1-10, what is your current biological energy level?",
        "Synchronization almost complete. Shall we initiate the high-performance execution flow?"
    )
    val responses = remember { mutableStateListOf("", "", "") }
    var isProcessing by remember { mutableStateOf(false) }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundBase)
            .padding(32.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(androidx.compose.foundation.rememberScrollState()),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Progress indicator
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(questions.size) { index ->
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(
                                if (index <= step) AccentPrimary else Color.White.copy(alpha = 0.2f),
                                RoundedCornerShape(4.dp)
                            )
                    )
                    if (index < questions.size - 1) {
                        Spacer(Modifier.width(8.dp))
                    }
                }
            }
            
            Text(
                text = questions[step],
                style = Typography.headlineMedium,
                color = Color.White,
                textAlign = TextAlign.Center
            )
            
            Spacer(Modifier.height(48.dp))
            
            when (step) {
                0 -> {
                    // Text input for objective
                    OutlinedTextField(
                        value = responses[0],
                        onValueChange = { responses[0] = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 100.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentPrimary,
                            unfocusedBorderColor = TextTertiary,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        shape = RoundedCornerShape(16.dp),
                        placeholder = { Text("Describe your goal...", color = TextTertiary) }
                    )
                }
                1 -> {
                    // Energy level slider
                    Column {
                        Slider(
                            value = responses[1].toIntOrNull()?.toFloat() ?: 5f,
                            onValueChange = { responses[1] = it.toInt().toString() },
                            valueRange = 1f..10f,
                            steps = 8,
                            modifier = Modifier.fillMaxWidth(),
                            colors = SliderDefaults.colors(
                                thumbColor = AccentPrimary,
                                activeTrackColor = AccentPrimary,
                                inactiveTrackColor = Color.White.copy(alpha = 0.1f)
                            )
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Low", style = Typography.labelSmall, color = TextSecondary)
                            Text(
                                responses[1].ifEmpty { "5" },
                                style = Typography.headlineMedium,
                                color = Color.White
                            )
                            Text("High", style = Typography.labelSmall, color = TextSecondary)
                        }
                    }
                }
                2 -> {
                    // Yes/No choice
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Button(
                            onClick = { responses[2] = "yes" },
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (responses[2] == "yes") AccentPrimary else SurfaceElevated
                            )
                        ) {
                            Text("Yes", color = Color.White)
                        }
                        Button(
                            onClick = { responses[2] = "no" },
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (responses[2] == "no") AccentPrimary else SurfaceElevated
                            )
                        ) {
                            Text("No", color = Color.White)
                        }
                    }
                }
            }
            
            Spacer(Modifier.height(32.dp))
            
            PrimaryGradientButton(
                text = if (step < questions.size - 1) "Continue" else "Complete Sync",
                loading = isProcessing,
                onClick = {
                    if (step < questions.size - 1) {
                        step++
                    } else {
                        isProcessing = true
                        // Simulate processing
                        onFinished()
                    }
                },
                enabled = when (step) {
                    0 -> responses[0].isNotBlank()
                    1 -> responses[1].isNotBlank()
                    2 -> responses[2].isNotBlank()
                    else -> true
                }
            )
        }
    }
}
