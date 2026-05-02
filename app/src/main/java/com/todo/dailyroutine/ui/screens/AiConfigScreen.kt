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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.todo.dailyroutine.ui.viewmodel.AiViewModel
import com.todo.dailyroutine.ui.theme.*
import com.todo.dailyroutine.ui.components.DashboardScaffold

@Composable
fun AiConfigScreen(viewModel: AiViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsState()
    var selectedProvider by remember { mutableStateOf("OpenAI") }
    
    val providers = listOf("OpenAI", "Anthropic", "Google", "Nvidia", "Groq")
    
    DashboardScaffold(
        title = "AI Protocol",
        onBackClick = onBack
    ) {
        item {
            Text(
                "Configure your neural links. FlowOS supports Bring Your Own Key (BYOK) for maximum autonomy.",
                style = Typography.bodyMedium,
                color = TextSecondary,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
        }

        item {
            ScrollableRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                providers.forEach { provider ->
                    ProviderChip(
                        name = provider,
                        isSelected = selectedProvider == provider,
                        onClick = { selectedProvider = provider }
                    )
                }
            }
        }

        item {
            val config = state.apiConfigs.find { it.providerName == selectedProvider }
            
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                color = SurfaceCard,
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "$selectedProvider Configuration",
                            style = Typography.titleLarge,
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.weight(1f)
                        )
                        
                        // Connectivity Status Dot
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(
                                    if (state.testResult?.contains("Success") == true) SuccessGreen else Color.White.copy(alpha = 0.1f),
                                    CircleShape
                                )
                        )
                    }
                    
                    Spacer(Modifier.height(24.dp))

                    ConfigField(
                        label = "API Key",
                        value = config?.apiKey ?: "",
                        onValueChange = { 
                            viewModel.updateConfigField(selectedProvider, apiKey = it)
                            // Auto-detection will handle the provider switch in ViewModel
                        },
                        placeholder = "sk-...",
                        icon = Icons.Default.VpnKey,
                        isPassword = true
                    )

                    Spacer(Modifier.height(16.dp))

                    ConfigField(
                        label = "Base URL (Optional)",
                        value = config?.baseUrl ?: "",
                        onValueChange = { viewModel.updateConfigField(selectedProvider, baseUrl = it) },
                        placeholder = "https://api...",
                        icon = Icons.Default.Link
                    )

                    Spacer(Modifier.height(24.dp))

                    // Model Selection Area with Animation
                    AnimatedVisibility(visible = true) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Model Selection", style = Typography.labelMedium, color = TextSecondary)
                                    Text(
                                        config?.model?.ifBlank { "None selected" } ?: "None selected",
                                        style = Typography.bodyLarge,
                                        color = Color.White
                                    )
                                }
                                
                                IconButton(
                                    onClick = { viewModel.fetchAvailableModels(selectedProvider) },
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color.White.copy(alpha = 0.05f))
                                ) {
                                    if (state.loading) {
                                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = AccentPrimary)
                                    } else {
                                        Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.White)
                                    }
                                }
                            }

                            if (state.availableModels.isNotEmpty()) {
                                Spacer(Modifier.height(16.dp))
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    mainAxisSpacing = 8.dp,
                                    crossAxisSpacing = 8.dp
                                ) {
                                    state.availableModels.forEach { model ->
                                        val isSelected = config?.model == model
                                        Surface(
                                            modifier = Modifier.clickable { viewModel.updateConfigField(selectedProvider, model = model) },
                                            shape = RoundedCornerShape(12.dp),
                                            color = if (isSelected) AccentPrimary.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.03f),
                                            border = BorderStroke(1.dp, if (isSelected) AccentPrimary else Color.White.copy(alpha = 0.05f))
                                        ) {
                                            Text(
                                                model,
                                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                                style = Typography.labelSmall,
                                                color = if (isSelected) Color.White else TextSecondary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(32.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        // Test Connection Button
                        OutlinedButton(
                            onClick = { viewModel.testConnection(selectedProvider) },
                            modifier = Modifier.weight(0.4f).height(56.dp),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                        ) {
                            Text("Test")
                        }

                        // Activate Button
                        Button(
                            onClick = { viewModel.saveAndActivateConfig(selectedProvider) },
                            modifier = Modifier.weight(0.6f).height(56.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (config?.isActive == true) SuccessGreen else AccentPrimary
                            )
                        ) {
                            Icon(if (config?.isActive == true) Icons.Default.CheckCircle else Icons.Default.PowerSettingsNew, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(if (config?.isActive == true) "Active" else "Activate")
                        }
                    }
                    
                    state.testResult?.let { result ->
                        Spacer(Modifier.height(16.dp))
                        Text(
                            result,
                            style = Typography.labelSmall,
                            color = if (result.contains("Success")) SuccessGreen else Color.White.copy(alpha = 0.4f),
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ProviderChip(name: String, isSelected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        color = if (isSelected) AccentPrimary else SurfaceCard,
        border = BorderStroke(1.dp, if (isSelected) Color.White.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.05f))
    ) {
        Text(
            name,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
            style = Typography.labelLarge,
            color = if (isSelected) Color.White else TextSecondary,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
fun ConfigField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    icon: ImageVector,
    isPassword: Boolean = false
) {
    Column {
        Text(label, style = Typography.labelMedium, color = TextSecondary)
        Spacer(Modifier.height(8.dp))
        TextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text(placeholder, color = Color.White.copy(alpha = 0.2f)) },
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = { Icon(icon, contentDescription = null, tint = AccentPrimary) },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.White.copy(alpha = 0.03f),
                unfocusedContainerColor = Color.White.copy(alpha = 0.03f),
                focusedIndicatorColor = AccentPrimary,
                unfocusedIndicatorColor = Color.Transparent,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            shape = RoundedCornerShape(12.dp),
            visualTransformation = if (isPassword) androidx.compose.ui.text.input.PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None
        )
    }
}

@Composable
fun ScrollableRow(
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    content: @Composable () -> Unit
) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = horizontalArrangement,
        content = { content() }
    )
}

@Composable
fun FlowRow(
    modifier: Modifier = Modifier,
    mainAxisSpacing: androidx.compose.ui.unit.Dp = 0.dp,
    crossAxisSpacing: androidx.compose.ui.unit.Dp = 0.dp,
    content: @Composable () -> Unit
) {
    androidx.compose.foundation.layout.FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(mainAxisSpacing),
        verticalArrangement = Arrangement.spacedBy(crossAxisSpacing),
        content = { content() }
    )
}
