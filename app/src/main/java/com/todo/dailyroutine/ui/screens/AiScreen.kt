package com.todo.dailyroutine.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Cyclone
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.todo.dailyroutine.data.model.ChatMessage
import com.todo.dailyroutine.ui.viewmodel.*
import com.todo.dailyroutine.ui.components.*
import com.todo.dailyroutine.ui.theme.*

@Composable
fun AiScreen(viewModel: AiViewModel) {
    val state by viewModel.uiState.collectAsState()
    var showSettings by remember { mutableStateOf(false) }
    
    if (showSettings) {
        AiSettingsDialog(viewModel, onDismiss = { showSettings = false })
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundBase)
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(12.dp))
        
        // Header with settings
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Cyclone, contentDescription = null, tint = AccentPrimary, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(8.dp))
                Text("Oracle", style = Typography.headlineMedium, color = Color.White)
            }
            
            IconButton(onClick = { showSettings = true }, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.Settings, contentDescription = "Settings", tint = TextSecondary, modifier = Modifier.size(20.dp))
            }
        }

        // Active config indicator
        if (state.activeConfig != null) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 8.dp),
                color = SurfaceElevated,
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            state.activeConfig!!.providerName,
                            style = Typography.labelMedium,
                            color = TextSecondary
                        )
                        Text(
                            state.selectedModel,
                            style = Typography.labelSmall,
                            color = TextTertiary
                        )
                    }
                    Surface(
                        color = Color(0xFF30D158).copy(alpha = 0.2f),
                        shape = CircleShape,
                        modifier = Modifier.size(8.dp)
                    ) {}
                }
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 12.dp, horizontal = 4.dp)
        ) {
            items(state.chatHistory, key = { it.timestamp }) { message ->
                ChatBubble(message)
            }
            if (state.loading) {
                item { TypingIndicator() }
            }
            if (state.chatHistory.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.Bolt,
                            contentDescription = null,
                            tint = AccentPrimary.copy(alpha = 0.5f),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "Real-time Intelligence",
                            style = Typography.labelLarge,
                            color = Color.White
                        )
                        Text(
                            "Start a conversation to sync your flow",
                            style = Typography.labelSmall,
                            color = TextSecondary
                        )
                    }
                }
            }
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 100.dp, start = 4.dp, end = 4.dp),
            shape = RoundedCornerShape(24.dp),
            color = SurfaceElevated,
            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
        ) {
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                androidx.compose.foundation.text.BasicTextField(
                    value = state.prompt,
                    onValueChange = { viewModel.onPromptChanged(it) },
                    modifier = Modifier.weight(1f),
                    textStyle = TextStyle(color = Color.White, fontSize = 15.sp, fontFamily = InterFontFamily),
                    cursorBrush = SolidColor(AccentPrimary),
                    decorationBox = { innerTextField ->
                        if (state.prompt.isEmpty()) Text("Ask Oracle...", color = TextTertiary, fontSize = 15.sp)
                        innerTextField()
                    }
                )
                
                IconButton(
                    onClick = { viewModel.sendMessage() },
                    modifier = Modifier
                        .size(36.dp)
                        .background(AccentPrimary, CircleShape),
                    enabled = state.prompt.isNotBlank() && !state.loading
                ) {
                    Icon(Icons.Default.ArrowUpward, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
fun AiSettingsDialog(viewModel: AiViewModel, onDismiss: () -> Unit) {
    val state by viewModel.uiState.collectAsState()
    var apiKey by remember { mutableStateOf("") }
    var showApiKeyInput by remember { mutableStateOf(false) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Oracle Configuration", color = Color.White) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(androidx.compose.foundation.rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (state.activeConfig != null) {
                    Text(
                        "Active Provider: ${state.activeConfig!!.providerName}",
                        style = Typography.labelMedium,
                        color = Color.White
                    )
                    
                    if (state.availableModels.isNotEmpty()) {
                        Text("Select Model:", style = Typography.labelSmall, color = TextSecondary)
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            state.availableModels.forEach { model ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { viewModel.updateSelectedModel(model) }
                                        .padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = model == state.selectedModel,
                                        onClick = { viewModel.updateSelectedModel(model) },
                                        colors = RadioButtonDefaults.colors(selectedColor = AccentPrimary)
                                    )
                                    Text(
                                        model,
                                        style = Typography.labelSmall,
                                        color = Color.White,
                                        modifier = Modifier.padding(start = 8.dp)
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Text(
                        "No AI provider configured. Add your API key to get started.",
                        style = Typography.labelSmall,
                        color = TextSecondary
                    )
                    showApiKeyInput = true
                }
                
                if (showApiKeyInput) {
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        label = { Text("API Key") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentPrimary,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (apiKey.isNotBlank()) {
                        val config = viewModel.autoDetectProvider(apiKey)
                        viewModel.saveConfig(config)
                    }
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = AccentPrimary)
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = TextSecondary)
            }
        },
        containerColor = SurfaceElevated,
        textContentColor = Color.White
    )
}
