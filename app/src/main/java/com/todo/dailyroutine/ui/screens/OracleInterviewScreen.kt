package com.todo.dailyroutine.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
    val response = remember { mutableStateOf("") }
    
    Box(Modifier.fillMaxSize().background(BackgroundBase).padding(32.dp)) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = questions[step],
                style = Typography.headlineMedium,
                color = Color.White,
                textAlign = TextAlign.Center
            )
            
            Spacer(Modifier.height(48.dp))
            
            OutlinedTextField(
                value = response.value,
                onValueChange = { response.value = it },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AccentPrimary,
                    unfocusedBorderColor = TextTertiary,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                shape = RoundedCornerShape(16.dp)
            )
            
            Spacer(Modifier.height(32.dp))
            
            PrimaryGradientButton(
                text = "Proceed",
                loading = false,
                onClick = {
                    if (step < questions.size - 1) {
                        step++
                        response.value = ""
                    } else {
                        onFinished()
                    }
                }
            )
        }
    }
}
