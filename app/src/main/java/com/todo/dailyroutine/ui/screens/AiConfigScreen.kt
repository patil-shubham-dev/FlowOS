package com.todo.dailyroutine.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
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
import com.todo.dailyroutine.ui.viewmodel.AiUiState
import com.todo.dailyroutine.ui.theme.*
import com.todo.dailyroutine.ui.components.DashboardScaffold
import com.todo.dailyroutine.data.model.UserApiConfig

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
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
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
            UniversalSetupCard(
                selectedProvider = selectedProvider,
                config = state.apiConfigs.find { it.providerName == selectedProvider },
                onKeyChange = { viewModel.updateConfigField(selectedProvider, apiKey = it) },
                onBaseUrlChange = { viewModel.updateConfigField(selectedProvider, baseUrl = it) },
                onTest = { viewModel.testConnection(selectedProvider) },
                onActivate = { viewModel.saveAndActivateConfig(selectedProvider) },
                onFetchModels = { viewModel.fetchAvailableModels(selectedProvider) },
                onSelectModel = { viewModel.updateConfigField(selectedProvider, model = it) },
                state = state
            )
        }
    }
}

@Composable
fun UniversalSetupCard(
    selectedProvider: String,
    config: UserApiConfig?,
    onKeyChange: (String) -> Unit,
    onBaseUrlChange: (String) -> Unit,
    onTest: () -> Unit,
    onActivate: () -> Unit,
    onFetchModels: () -> Unit,
    onSelectModel: (String) -> Unit,
    state: AiUiState
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(32.dp),
        color = SurfaceCard,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            // Header with Magic Feel
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(AccentPrimary.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = when(selectedProvider) {
                            "OpenAI" -> Icons.Default.Bolt
                            "Anthropic" -> Icons.Default.MenuBook
                            "Google" -> Icons.Default.AutoAwesome
                            "Nvidia" -> Icons.Default.Memory
                            "Groq" -> Icons.Default.Speed
                            else -> Icons.Default.Link
                        },
                        contentDescription = null,
                        tint = AccentPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }
                
                Spacer(Modifier.width(16.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        selectedProvider,
                        style = Typography.titleLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        "Neural Interface Protocol",
                        style = Typography.labelSmall,
                        color = TextSecondary
                    )
                }

                StatusIndicator(state.testResult)
            }
            
            Spacer(Modifier.height(32.dp))

            // Magic Key Input
            ConfigField(
                label = "Neural Key",
                value = config?.apiKey ?: "",
                onValueChange = onKeyChange,
                placeholder = "Paste your secret key here...",
                icon = Icons.Default.VpnKey,
                isPassword = true
            )

            Spacer(Modifier.height(16.dp))

            ConfigField(
                label = "Endpoint Relay (Optional)",
                value = config?.baseUrl ?: "",
                onValueChange = onBaseUrlChange,
                placeholder = "Default Proxy active",
                icon = Icons.Default.Hub
            )

            Spacer(Modifier.height(24.dp))

            // Smart Model Selector
            ModelSelector(
                currentModel = config?.model,
                availableModels = state.availableModels,
                isLoading = state.loading,
                onRefresh = onFetchModels,
                onSelect = onSelectModel
            )

            Spacer(Modifier.height(32.dp))

            // Action Cluster
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = onTest,
                    modifier = Modifier.weight(0.4f).height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                ) {
                    Text("Sync Link")
                }

                Button(
                    onClick = onActivate,
                    modifier = Modifier.weight(0.6f).height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (config?.isActive == true) SuccessGreen else AccentPrimary
                    )
                ) {
                    Icon(
                        if (config?.isActive == true) Icons.Default.CheckCircle else Icons.Default.ElectricalServices,
                        contentDescription = null
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(if (config?.isActive == true) "Synchronized" else "Initialize")
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

@Composable
fun StatusIndicator(testResult: String?) {
    val isSuccess = testResult?.contains("Success") == true
    val isError = testResult?.contains("Failed") == true
    
    val color = when {
        isSuccess -> SuccessGreen
        isError -> Color.Red.copy(alpha = 0.6f)
        else -> Color.White.copy(alpha = 0.1f)
    }

    Box(
        modifier = Modifier
            .size(12.dp)
            .clip(CircleShape)
            .background(color)
            .border(2.dp, color.copy(alpha = 0.3f), CircleShape)
    )
}

@Composable
fun ModelSelector(
    currentModel: String?,
    availableModels: List<String>,
    isLoading: Boolean,
    onRefresh: () -> Unit,
    onSelect: (String) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Model Core", style = Typography.labelMedium, color = TextSecondary)
                Text(
                    currentModel?.ifBlank { "Unidentified" } ?: "Unidentified",
                    style = Typography.bodyLarge,
                    color = if (currentModel.isNullOrBlank()) TextSecondary else Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
            
            IconButton(
                onClick = onRefresh,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.05f))
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = AccentPrimary)
                } else {
                    Icon(Icons.Default.AutoMode, contentDescription = null, tint = Color.White.copy(alpha = 0.6f))
                }
            }
        }

        if (availableModels.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                mainAxisSpacing = 8.dp,
                crossAxisSpacing = 8.dp
            ) {
                availableModels.forEach { model ->
                    val isSelected = currentModel == model
                    Surface(
                        modifier = Modifier.clickable { onSelect(model) },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) AccentPrimary.copy(alpha = 0.1f) else Color.White.copy(alpha = 0.02f),
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

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
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
