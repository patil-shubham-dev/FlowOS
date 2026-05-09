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
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.todo.dailyroutine.ui.viewmodel.*
import com.todo.dailyroutine.ui.components.*
import com.todo.dailyroutine.ui.theme.*

@Composable
fun DashboardScreen(
    homeViewModel: HomeViewModel, 
    aiViewModel: AiViewModel,
    onNavigateToSearch: () -> Unit,
    onNavigateToDeepFlow: () -> Unit
) {
    val state by homeViewModel.uiState.collectAsState()
    val aiState by aiViewModel.uiState.collectAsState()
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundBase)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Header
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FlowLogo(modifier = Modifier.size(56.dp))
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("FlowOS", style = Typography.labelMedium, color = AccentPrimary, fontWeight = FontWeight.Bold)
                            Text("Operational", style = Typography.headlineMedium, color = Color.White)
                        }
                    }
                    IconButton(
                        onClick = onNavigateToSearch,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(SurfaceCard)
                            .border(1.dp, Color.White.copy(alpha = 0.05f), CircleShape)
                    ) {
                        Icon(Icons.Default.Search, contentDescription = null, tint = Color.White.copy(alpha = 0.7f))
                    }
                }
            }

            // Neural Action Row
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ActionPill(
                        label = "Daily Protocol",
                        icon = Icons.Default.AutoMode,
                        onClick = { aiViewModel.generateDailyProtocol(homeViewModel.taskRepository) },
                        modifier = Modifier.weight(1f),
                        isLoading = aiState.protocolLoading
                    )
                    ActionPill(
                        label = "Deep Flow",
                        icon = Icons.Default.Cyclone,
                        onClick = onNavigateToDeepFlow,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Core Metrics
            item {
                if (state.loading) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        SkeletonCard(modifier = Modifier.weight(1f), height = 110.dp)
                        SkeletonCard(modifier = Modifier.weight(1f), height = 110.dp)
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        MetricCard(
                            label = "Sync",
                            value = "${state.stats.progressPercent}%",
                            modifier = Modifier.weight(1f),
                            color = SuccessGreen
                        )
                        MetricCard(
                            label = "Flow",
                            value = "${state.tasks.count { it.completed }}/${state.tasks.size}",
                            modifier = Modifier.weight(1f),
                            color = AccentPrimary
                        )
                    }
                }
            }

            // Brain State Visualization
            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp),
                    shape = RoundedCornerShape(32.dp),
                    color = SurfaceCard,
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        BrainStateOrb(
                            flowScore = state.stats.progressPercent,
                            syncProgress = state.stats.progressPercent / 100f,
                            flowHoursProgress = 0.7f,
                            vibeProgress = 0.8f,
                            modifier = Modifier.size(220.dp)
                        )
                    }
                }
            }

            // Oracle Insight
            item {
                if (state.loading) {
                    SkeletonCard(height = 100.dp)
                } else if (state.oracleInsight != null) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        color = SuccessGreen.copy(alpha = 0.1f),
                        border = BorderStroke(1.dp, SuccessGreen.copy(alpha = 0.2f))
                    ) {
                        Row(
                            modifier = Modifier.padding(20.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Cyclone, contentDescription = null, tint = SuccessGreen)
                            Spacer(Modifier.width(16.dp))
                            Column {
                                Text("Oracle Insight", style = Typography.labelMedium, color = SuccessGreen)
                                Text(state.oracleInsight!!, style = Typography.bodyLarge, fontWeight = FontWeight.Medium, color = Color.White)
                            }
                        }
                    }
                }
            }
            
            item { Spacer(Modifier.height(80.dp)) }
        }

        // Daily Protocol Overlay
        aiState.dailyProtocol?.let { protocol ->
            DailyProtocolOverlay(
                protocol = protocol,
                onDismiss = { /* Optionally clear protocol */ },
                onApply = { aiViewModel.applyProtocol(homeViewModel.taskRepository) }
            )
        }

        CelebrationOverlay(
            isVisible = state.celebrationMessage != null,
            message = state.celebrationMessage ?: ""
        )
    }
}

@Composable
fun ActionPill(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false
) {
    Surface(
        modifier = modifier.clickable { if (!isLoading) onClick() },
        shape = RoundedCornerShape(20.dp),
        color = SurfaceCard,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = AccentPrimary)
            } else {
                Icon(icon, contentDescription = null, tint = AccentPrimary, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(10.dp))
            Text(label, style = Typography.labelLarge, color = Color.White)
        }
    }
}

@Composable
fun DailyProtocolOverlay(
    protocol: DailyProtocol,
    onDismiss: () -> Unit,
    onApply: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BackgroundBase,
        tonalElevation = 8.dp,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AutoMode, contentDescription = null, tint = AccentPrimary)
                Spacer(Modifier.width(12.dp))
                Text("Proposed Protocol", color = Color.White)
            }
        },
        text = {
            Column {
                Text(protocol.summary, color = TextSecondary, style = Typography.bodyMedium)
                Spacer(Modifier.height(20.dp))
                LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                    items(protocol.actions.size) { index ->
                        val action = protocol.actions[index]
                        Column(modifier = Modifier.padding(vertical = 8.dp)) {
                            Text(action.title, color = Color.White, fontWeight = FontWeight.Bold)
                            Text("${action.suggestedTimeBlock}: ${action.reasoning}", color = TextSecondary, style = Typography.labelSmall)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onApply, colors = ButtonDefaults.buttonColors(containerColor = AccentPrimary)) {
                Text("Apply Strategy")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Dismiss", color = TextSecondary)
            }
        },
        shape = RoundedCornerShape(32.dp)
    )
}
