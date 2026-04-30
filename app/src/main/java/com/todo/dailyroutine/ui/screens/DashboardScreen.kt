package com.todo.dailyroutine.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Cyclone
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
fun DashboardScreen(homeViewModel: HomeViewModel, aiViewModel: AiViewModel) {
    val state by homeViewModel.uiState.collectAsState()
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepBackground)
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
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Protocol", style = PremiumTypography.labelMedium, color = TextSecondary)
                        Text("Operational", style = PremiumTypography.headlineMedium)
                    }
                    Surface(
                        modifier = Modifier.size(48.dp),
                        shape = CircleShape,
                        color = SurfaceDark
                    ) {
                        Icon(Icons.Default.Cyclone, contentDescription = null, tint = AccentFlow, modifier = Modifier.padding(12.dp))
                    }
                }
            }

            // Core Metrics
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    MetricCard(
                        label = "Sync",
                        value = "${state.stats.progressPercent}%",
                        modifier = Modifier.weight(1f),
                        color = Color(0xFF30D158)
                    )
                    MetricCard(
                        label = "Flow",
                        value = "${state.tasks.count { it.completed }}/${state.tasks.size}",
                        modifier = Modifier.weight(1f),
                        color = AccentFlow
                    )
                }
            }

            // Brain State Visualization
            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp),
                    shape = RoundedCornerShape(32.dp),
                    color = SurfaceDark,
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
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

            // Next Best Action
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    color = AccentFlow.copy(alpha = 0.1f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AccentFlow.copy(alpha = 0.2f))
                ) {
                    Row(
                        modifier = Modifier.padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Bolt, contentDescription = null, tint = AccentFlow)
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text("Next Best Action", style = PremiumTypography.labelMedium, color = AccentFlow)
                            Text(state.nextBestAction, style = PremiumTypography.bodyLarge, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
            
            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

@Composable
fun MetricCard(label: String, value: String, modifier: Modifier, color: Color) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        color = SurfaceDark,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(label, style = PremiumTypography.labelMedium, color = TextSecondary)
            Spacer(Modifier.height(4.dp))
            Text(value, style = PremiumTypography.headlineMedium, color = color)
        }
    }
}
