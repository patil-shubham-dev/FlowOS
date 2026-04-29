package com.todo.dailyroutine.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.todo.dailyroutine.ui.viewmodel.*
import com.todo.dailyroutine.ui.components.*

@Composable
fun DashboardScreen(homeViewModel: HomeViewModel, aiViewModel: AiViewModel) {
    val state by homeViewModel.uiState.collectAsState()
    val completedTasks = state.tasks.count { it.completed }
    val completedHabits = state.habits.count { it.completedToday }
    val totalTasks = state.tasks.size
    val totalHabits = state.habits.size
    val overallItems = totalTasks + totalHabits
    val overallCompleted = completedTasks + completedHabits
    val syncProgress = if (overallItems > 0) overallCompleted.toFloat() / overallItems else 0f
    val taskProgress = if (totalTasks > 0) completedTasks.toFloat() / totalTasks else 0f
    val vibeProgress = if (totalHabits > 0) completedHabits.toFloat() / totalHabits else syncProgress
    val flowScore = state.stats.progressPercent

    DashboardScaffold(title = "Neurostate") {
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                BrainStateOrb(
                    flowScore = flowScore,
                    syncProgress = syncProgress,
                    flowHoursProgress = taskProgress,
                    vibeProgress = vibeProgress,
                    modifier = Modifier.size(240.dp)
                )
            }
        }

        item {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FlowStatCard("Sync", "${(syncProgress * 100).toInt()}%", Color(0xFF30D158), Modifier.fillMaxWidth())
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    FlowStatCard("Flow", "$completedTasks/$totalTasks", Color(0xFF5B9CFF), Modifier.weight(1f))
                    FlowStatCard("Rituals", "$completedHabits/$totalHabits", Color(0xFFFF9500), Modifier.weight(1f))
                }
            }
        }

        item {
            FlowProgressHeader(
                ritualsDone = completedHabits,
                ritualsTotal = totalHabits,
                objectivesDone = completedTasks,
                objectivesTotal = totalTasks,
                flowScore = flowScore,
                overallProgress = syncProgress,
                modifier = Modifier.fillMaxWidth()
            )
        }

        item {
            if (state.nextAction.isNotBlank()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0xFF7C5CFF).copy(alpha = 0.15f),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF7C5CFF).copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            androidx.compose.material.icons.filled.Bolt,
                            contentDescription = null,
                            tint = Color(0xFF7C5CFF),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                "Next Best Action",
                                style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                                color = Color.White
                            )
                            Text(
                                state.nextAction,
                                style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                                color = com.todo.dailyroutine.ui.theme.TextSecondary
                            )
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(20.dp)) }
    }
}
