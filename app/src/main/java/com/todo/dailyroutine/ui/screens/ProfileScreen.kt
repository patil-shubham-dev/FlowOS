package com.todo.dailyroutine.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.todo.dailyroutine.ui.viewmodel.*
import com.todo.dailyroutine.ui.components.*
import com.todo.dailyroutine.ui.theme.*

@Composable
fun ProfileScreen(
    homeViewModel: HomeViewModel,
    onNavigateToAiConfig: () -> Unit
) {
    val homeState by homeViewModel.uiState.collectAsState()
    var showAppLockDialog by remember { mutableStateOf(false) }
    
    val currentScore = homeState.stats.progressPercent
    val rankTitle = when {
        currentScore >= 90 -> "Arch-Oracle"
        currentScore >= 80 -> "Oracle Link"
        currentScore >= 60 -> "Neural Weaver"
        currentScore >= 40 -> "Flow Adept"
        currentScore >= 20 -> "Novice Sync"
        else -> "Protocol Initializing"
    }

    if (showAppLockDialog) {
        // App Lock toggle disabled for now or move to SessionManager
        showAppLockDialog = false
    }
    
    DashboardScaffold(title = "System") {
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                color = SurfaceCard,
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .background(AccentGradient, CircleShape)
                            .padding(2.dp)
                    ) {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            shape = CircleShape,
                            color = BackgroundBase
                        ) {
                            Icon(
                                Icons.Default.Person,
                                contentDescription = null,
                                modifier = Modifier.padding(20.dp),
                                tint = Color.White
                            )
                        }
                    }
                    
                    Spacer(Modifier.height(16.dp))
                    Text("Flow User", style = Typography.headlineMedium, color = Color.White)
                    
                    Spacer(Modifier.height(8.dp))
                    
                    Surface(
                        color = AccentPrimary.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, AccentPrimary.copy(alpha = 0.2f))
                    ) {
                        Text(
                            rankTitle,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            style = Typography.labelLarge,
                            color = AccentPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        item {
            ProfileSection(title = "Intelligence Configuration") {
                ProfileItem(
                    Icons.Default.AutoAwesome,
                    "AI Protocol",
                    "Manage API keys and neural models",
                    onClick = onNavigateToAiConfig
                )
            }
        }

        item {
            ProfileSection(title = "Core Configuration") {
                ProfileItem(
                    Icons.Default.Brightness4,
                    "Appearance",
                    "Dark mode enabled",
                    onClick = { /* Theme toggle */ }
                )
                ProfileItem(
                    Icons.Default.Notifications,
                    "Notifications",
                    "Real-time updates active",
                    onClick = { /* Notification settings */ }
                )
                ProfileItem(
                    Icons.Default.Security,
                    "App Lock",
                    "Disabled",
                    onClick = { showAppLockDialog = true }
                )
            }
        }

        item {
            ProfileSection(title = "Neural Mastery") {
                Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("Current Flow", style = Typography.labelSmall, color = TextSecondary)
                        Text("${currentScore}%", style = Typography.titleLarge, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Level", style = Typography.labelSmall, color = TextSecondary)
                        Text((currentScore / 10 + 1).toString(), style = Typography.titleLarge, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
                
                LinearProgressIndicator(
                    progress = (currentScore % 10) / 10f,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(4.dp).clip(CircleShape),
                    color = AccentPrimary,
                    trackColor = Color.White.copy(alpha = 0.05f)
                )
                Spacer(Modifier.height(16.dp))
            }
        }

        item {
            ProfileSection(title = "Protocol Settings") {
                ProfileItem(
                    icon = Icons.Default.Info,
                    title = "Version",
                    subtitle = "FlowOS v2.0-Alpha (Local-First)",
                    onClick = { }
                )
            }
        }
        
        item { Spacer(Modifier.height(60.dp)) }
    }
}

@Composable
fun AppLockDialog(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("App Lock", color = Color.White) },
        text = {
            Column {
                Text(
                    "Protect your FlowOS with biometric or device authentication.",
                    style = Typography.labelSmall,
                    color = TextSecondary,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Enable App Lock", color = Color.White)
                    Switch(
                        checked = enabled,
                        onCheckedChange = onToggle,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = AccentPrimary,
                            checkedTrackColor = AccentPrimary.copy(alpha = 0.5f)
                        )
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = AccentPrimary)
            ) {
                Text("Done")
            }
        },
        containerColor = SurfaceElevated,
        textContentColor = Color.White
    )
}

@Composable
fun ProfileSection(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = modifier) {
        Text(title, style = MaterialTheme.typography.labelMedium, color = Color(0xFFA0A0B0))
        Spacer(Modifier.height(12.dp))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = SurfaceCard,
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
        ) {
            Column(Modifier.padding(vertical = 8.dp)) {
                content()
            }
        }
    }
}

@Composable
fun ProfileItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = Color.White)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = Color(0xFFA0A0B0))
        }
        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = Color.White.copy(alpha = 0.2f), modifier = Modifier.size(16.dp))
    }
}
