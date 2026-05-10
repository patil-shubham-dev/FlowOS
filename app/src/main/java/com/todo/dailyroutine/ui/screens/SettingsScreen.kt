package com.todo.dailyroutine.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.todo.dailyroutine.data.ai.ProviderFactory
import com.todo.dailyroutine.data.model.AiProviderConfig
import com.todo.dailyroutine.ui.components.*
import com.todo.dailyroutine.ui.theme.*
import com.todo.dailyroutine.ui.viewmodel.HomeViewModel

@Composable
fun SettingsScreen(viewModel: HomeViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    
    var showResetDialog by remember { mutableStateOf(false) }
    var showEditNameDialog by remember { mutableStateOf(false) }
    var showEditGoalDialog by remember { mutableStateOf(false) }
    var showModelSelector by remember { mutableStateOf(false) }
    var showAppearanceDialog by remember { mutableStateOf(false) }
    
    var tempConfig by remember(uiState.aiConfig) { mutableStateOf(uiState.aiConfig) }
    var keyVisible by remember { mutableStateOf(false) }
    var showAdvanced by remember { mutableStateOf(false) }


    if (showModelSelector) {
        AlertDialog(
            onDismissRequest = { showModelSelector = false },
            containerColor = ObsidianSurface,
            title = { Text("Select Model", color = TextPrimary) },
            text = {
                if (uiState.availableModels.isEmpty()) {
                    Text("Fetch models first to populate this list.", color = TextMuted, modifier = Modifier.padding(16.dp))
                } else {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        uiState.availableModels.forEach { model ->
                            Row(
                                Modifier.fillMaxWidth().clickable { 
                                    tempConfig = tempConfig.copy(selectedModelId = model.id, selectedModelName = model.displayName)
                                    showModelSelector = false 
                                }.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(selected = tempConfig.selectedModelId == model.id, onClick = null, colors = RadioButtonDefaults.colors(selectedColor = AccentBlue))
                                Spacer(Modifier.width(12.dp))
                                Text(model.displayName, color = TextPrimary)
                            }
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }

    Scaffold(
        containerColor = ObsidianBackground
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            item { Spacer(Modifier.height(16.dp)) }
            item { Text("Settings", style = Typography.displayLarge) }

            // AI Configuration Card
            item {
                Text("AI OS Intelligence", style = Typography.labelSmall, color = TextMuted, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
                GlassCard(backgroundColor = ObsidianSurface) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        // API Key Input
                        Column {
                            Text("Universal API Key", style = Typography.labelSmall, color = TextMuted)
                            TextField(
                                value = tempConfig.apiKey,
                                onValueChange = { tempConfig = tempConfig.copy(apiKey = it) },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text("Enter API Key (OpenAI, Gemini, Anthropic...)", color = TextMuted) },
                                visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                trailingIcon = {
                                    IconButton(onClick = { keyVisible = !keyVisible }) {
                                        Icon(if (keyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility, null, tint = TextMuted)
                                    }
                                },
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = ObsidianBackground,
                                    unfocusedContainerColor = ObsidianBackground,
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary,
                                    focusedIndicatorColor = AccentBlue
                                ),
                                shape = RoundedCornerShape(12.dp)
                            )
                        }

                        // Provider Display (Read-only badge revealed after detection)
                        if (tempConfig.providerId.isNotBlank()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(AccentBlue.copy(alpha = 0.05f))
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Detected Provider", style = Typography.labelSmall, color = TextMuted)
                                    Text(tempConfig.providerName, color = AccentBlue, fontWeight = FontWeight.Bold)
                                }
                                
                                Icon(Icons.Default.Verified, null, tint = AccentBlue, modifier = Modifier.size(20.dp))
                            }
                        }

                        // Model Selector (Revealed after detection)
                        if (tempConfig.providerId.isNotBlank()) {
                            Column {
                                Text("Model", style = Typography.labelSmall, color = TextMuted, modifier = Modifier.padding(bottom = 4.dp))
                                Button(
                                    onClick = { showModelSelector = true },
                                    modifier = Modifier.fillMaxWidth().height(52.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = ObsidianBackground),
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, BorderSubtle),
                                    enabled = !uiState.isTyping
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(tempConfig.selectedModelName ?: "Select Model", color = TextPrimary)
                                        Icon(Icons.Default.ArrowDropDown, null, tint = TextMuted)
                                    }
                                }
                            }
                        }

                        // Actions: Fetch & Save
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            // Fetch Models Button (Always visible after first tap for refresh)
                            Button(
                                onClick = { viewModel.detectAiProvider(tempConfig.apiKey) },
                                modifier = Modifier.fillMaxWidth().height(52.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (tempConfig.providerId.isBlank()) AccentBlue else ObsidianSurfaceElevated
                                ),
                                shape = RoundedCornerShape(12.dp),
                                enabled = !uiState.isTyping && tempConfig.apiKey.isNotBlank()
                            ) {
                                if (uiState.isTyping) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = if (tempConfig.providerId.isBlank()) ObsidianBackground else AccentBlue, strokeWidth = 2.dp)
                                    Spacer(Modifier.width(12.dp))
                                    Text("FETCHING MODELS...", style = Typography.labelLarge, color = if (tempConfig.providerId.isBlank()) ObsidianBackground else AccentBlue)
                                } else {
                                    Icon(
                                        if (tempConfig.providerId.isBlank()) Icons.Default.AutoAwesome else Icons.Default.Refresh,
                                        null, 
                                        modifier = Modifier.size(18.dp),
                                        tint = if (tempConfig.providerId.isBlank()) ObsidianBackground else AccentBlue
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        if (tempConfig.providerId.isBlank()) "FETCH MODELS & DETECT" else "REFRESH MODELS", 
                                        style = Typography.labelLarge, 
                                        color = if (tempConfig.providerId.isBlank()) ObsidianBackground else AccentBlue,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            // Save Button (Only shown/enabled after detection)
                            if (tempConfig.providerId.isNotBlank()) {
                                Button(
                                    onClick = { viewModel.updateAiConfig(tempConfig) },
                                    modifier = Modifier.fillMaxWidth().height(52.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                                    shape = RoundedCornerShape(12.dp),
                                    enabled = tempConfig.selectedModelId != null && !uiState.isTyping
                                ) {
                                    val isSaved = uiState.aiConfig == tempConfig
                                    Text(
                                        if (isSaved) "CONFIGURATION SAVED" else "SAVE CONFIGURATION", 
                                        color = if (isSaved) SuccessGreen else ObsidianBackground, 
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        // Advanced Toggle
                        TextButton(onClick = { showAdvanced = !showAdvanced }) {
                            Text(if (showAdvanced) "Hide Advanced" else "Show Advanced Settings", color = TextMuted, fontSize = 12.sp)
                        }

                        AnimatedVisibility(visible = showAdvanced) {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                // Base URL Override
                                Column {
                                    Text("Custom Base URL (Override)", style = Typography.labelSmall, color = TextMuted)
                                    TextField(
                                        value = tempConfig.baseUrl,
                                        onValueChange = { tempConfig = tempConfig.copy(baseUrl = it) },
                                        modifier = Modifier.fillMaxWidth(),
                                        placeholder = { Text("https://api.example.com/v1", color = TextMuted) },
                                        colors = TextFieldDefaults.colors(
                                            focusedContainerColor = ObsidianBackground,
                                            unfocusedContainerColor = ObsidianBackground,
                                            focusedTextColor = TextPrimary,
                                            unfocusedTextColor = TextPrimary
                                        ),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                }
                                
                                Column {
                                    Text("Temperature: ${String.format("%.1f", tempConfig.temperature)}", color = TextSecondary, style = Typography.labelSmall)
                                    Slider(
                                        value = tempConfig.temperature,
                                        onValueChange = { tempConfig = tempConfig.copy(temperature = it) },
                                        valueRange = 0f..2f,
                                        colors = SliderDefaults.colors(thumbColor = AccentBlue, activeTrackColor = AccentBlue)
                                    )
                                }

                                Column {
                                    Text("Top P: ${String.format("%.2f", tempConfig.topP)}", color = TextSecondary, style = Typography.labelSmall)
                                    Slider(
                                        value = tempConfig.topP,
                                        onValueChange = { tempConfig = tempConfig.copy(topP = it) },
                                        valueRange = 0f..1f,
                                        colors = SliderDefaults.colors(thumbColor = AccentBlue, activeTrackColor = AccentBlue)
                                    )
                                }

                                Column {
                                    Text("Max Output Tokens: ${tempConfig.maxTokens}", color = TextSecondary, style = Typography.labelSmall)
                                    Slider(
                                        value = tempConfig.maxTokens.toFloat(),
                                        onValueChange = { tempConfig = tempConfig.copy(maxTokens = it.toInt()) },
                                        valueRange = 100f..32000f,
                                        steps = 32,
                                        colors = SliderDefaults.colors(thumbColor = AccentBlue, activeTrackColor = AccentBlue)
                                    )
                                }

                                SettingsToggle(Icons.Default.Stream, "Streaming", tempConfig.streamingEnabled) {
                                    tempConfig = tempConfig.copy(streamingEnabled = !tempConfig.streamingEnabled)
                                }

                                Spacer(Modifier.height(8.dp))
                                
                                OutlinedButton(
                                    onClick = { viewModel.updateAiConfig(tempConfig); viewModel.testAiConnection() },
                                    modifier = Modifier.fillMaxWidth().height(48.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, BorderSubtle),
                                    enabled = !uiState.isTyping
                                ) { 
                                    if (uiState.isTyping) {
                                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = TextPrimary, strokeWidth = 2.dp)
                                    } else {
                                        Text("TEST CONNECTION", color = TextPrimary, style = Typography.labelLarge)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Identity Section
            item {
                SettingsSection("Identity") {
                    SettingsRow(Icons.Default.Badge, "Display Name", uiState.displayName) { showEditNameDialog = true }
                    SettingsRow(Icons.Default.TrackChanges, "Core Goal", uiState.coreGoal) { showEditGoalDialog = true }
                    SettingsRow(Icons.Default.Palette, "Appearance", uiState.appearance) { showAppearanceDialog = true }
                }
            }

            item {
                SettingsSection("System") {
                    SettingsToggle(Icons.Default.Security, "Privacy Lock", uiState.appLockEnabled) { viewModel.toggleAppLock() }
                    SettingsToggle(Icons.Default.Notifications, "Notifications", uiState.notificationsEnabled) { viewModel.toggleNotifications() }
                    SettingsToggle(Icons.Default.RecordVoiceOver, "Voice-First Interactivity", uiState.isVoiceEnabled) { viewModel.toggleVoice(!uiState.isVoiceEnabled) }
                }
            }

            item {
                Button(
                    onClick = { showResetDialog = true },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ObsidianSurfaceElevated),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, ErrorRed.copy(alpha = 0.2f))
                ) { Text("SYSTEM RESET", color = ErrorRed, fontWeight = FontWeight.Bold) }
            }
            item { Spacer(Modifier.height(120.dp)) }
        }
    }
}

@Composable
fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(title.uppercase(), style = Typography.labelSmall.copy(letterSpacing = 2.sp), color = TextMuted, modifier = Modifier.padding(start = 4.dp, bottom = 12.dp))
        GlassCard(backgroundColor = ObsidianSurface.copy(alpha = 0.5f)) { 
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) { content() } 
        }
    }
}

@Composable
fun SettingsRow(icon: ImageVector, title: String, value: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically, 
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null
            ) { onClick() }
            .padding(vertical = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(AccentBlue.copy(alpha = 0.1f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = AccentBlue, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = Typography.bodyLarge.copy(fontWeight = FontWeight.Medium))
            Text(value, style = Typography.labelSmall, color = TextMuted)
        }
        Icon(Icons.Default.ChevronRight, null, tint = TextMuted, modifier = Modifier.size(20.dp))
    }
}

@Composable
fun SettingsToggle(icon: ImageVector, title: String, checked: Boolean, onToggle: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically, 
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(AccentBlue.copy(alpha = 0.1f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = AccentBlue, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(16.dp))
        Text(title, style = Typography.bodyLarge.copy(fontWeight = FontWeight.Medium), modifier = Modifier.weight(1f))
        Switch(
            checked = checked, 
            onCheckedChange = { onToggle() },
            colors = SwitchDefaults.colors(
                checkedThumbColor = AccentBlue, 
                checkedTrackColor = AccentBlue.copy(alpha = 0.3f), 
                uncheckedThumbColor = TextMuted, 
                uncheckedTrackColor = ObsidianBackground
            )
        )
    }
}
