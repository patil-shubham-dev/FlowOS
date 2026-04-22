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
                    .padding(vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                BrainStateOrb(
                    flowScore = flowScore,
                    syncProgress = syncProgress,
                    flowHoursProgress = taskProgress,
                    vibeProgress = vibeProgress,
                    modifier = Modifier.size(280.dp)
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                FlowStatCard("Sync", "${(syncProgress * 100).toInt()}%", Color(0xFF30D158), Modifier.weight(1f))
                FlowStatCard("Flow", "${completedTasks}/${totalTasks}", Color(0xFF5B9CFF), Modifier.weight(1f))
            }
        }
    }
}
