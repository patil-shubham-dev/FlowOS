package com.todo.dailyroutine.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.unit.dp
import com.todo.dailyroutine.ui.viewmodel.*
import com.todo.dailyroutine.ui.components.*

@Composable
fun FlowScreen(viewModel: HomeViewModel) {
    val state by viewModel.uiState.collectAsState()
    val habitsCompleted = state.habits.count { it.completedToday }
    val tasksCompleted = state.tasks.count { it.completed }
    val totalHabits = state.habits.size
    val totalTasks = state.tasks.size
    val totalItems = totalHabits + totalTasks
    val overallProgress = if (totalItems > 0) {
        (habitsCompleted + tasksCompleted).toFloat() / totalItems
    } else {
        0f
    }
    
    DashboardScaffold(title = "Execution") {
        item {
            FlowProgressHeader(
                ritualsDone = habitsCompleted,
                ritualsTotal = totalHabits,
                objectivesDone = tasksCompleted,
                objectivesTotal = totalTasks,
                flowScore = state.stats.progressPercent,
                overallProgress = overallProgress
            )
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                FlowSection(title = "Rituals") {
                    state.habits.forEach { habit ->
                        FlowHabitItem(
                            habit = habit,
                            onToggle = { viewModel.toggleHabit(habit) }
                        )
                    }
                }
                
                FlowSection(title = "Objectives") {
                    state.tasks.forEach { task ->
                        FlowTaskItem(
                            task = task,
                            onToggle = { viewModel.toggleTask(task) }
                        )
                    }
                }
            }
        }
        
        item { Spacer(Modifier.height(120.dp)) }
    }
}

@Composable
private fun FlowSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(text = title, style = MaterialTheme.typography.titleLarge)
        content()
    }
}
