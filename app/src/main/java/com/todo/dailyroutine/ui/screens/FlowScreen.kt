package com.todo.dailyroutine.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.todo.dailyroutine.ui.components.*
import com.todo.dailyroutine.ui.theme.*
import com.todo.dailyroutine.ui.viewmodel.HomeViewModel

@Composable
fun FlowScreen(viewModel: HomeViewModel) {
    var selectedTab by remember { mutableStateOf("Tasks") }
    val uiState by viewModel.uiState.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var newItemName by remember { mutableStateOf("") }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            containerColor = ObsidianSurface,
            title = { Text("Add New ${if (selectedTab == "Tasks") "Task" else "Ritual"}", color = TextPrimary) },
            text = {
                TextField(
                    value = newItemName,
                    onValueChange = { newItemName = it },
                    placeholder = { Text("Enter title...", color = TextMuted) },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = ObsidianBackground,
                        unfocusedContainerColor = ObsidianBackground,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newItemName.isNotBlank()) {
                        if (selectedTab == "Tasks") {
                            viewModel.addTask(newItemName, "General")
                        } else {
                            viewModel.addHabit(newItemName)
                        }
                        newItemName = ""
                        showAddDialog = false
                    }
                }) {
                    Text("ADD", color = AccentBlue)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("CANCEL", color = TextMuted)
                }
            }
        )
    }

    Scaffold(
        containerColor = ObsidianBackground
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
        ) {
            Spacer(Modifier.height(16.dp))
            
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Operational Flow", style = Typography.headlineLarge)
                IconButton(onClick = { showAddDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = AccentBlue)
                }
            }
            
            Text("Protocol: Standard Execution", color = TextSecondary, style = Typography.labelMedium)
            
            Spacer(Modifier.height(24.dp))

            // Tab Pill Selector
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ObsidianSurface, RoundedCornerShape(12.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf("Tasks", "Rituals", "Calendar").forEach { tab ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selectedTab == tab) ObsidianSurfaceElevated else Color.Transparent)
                            .clickable { selectedTab = tab }
                            .then(if (selectedTab == tab) Modifier.border(1.dp, BorderSubtle, RoundedCornerShape(8.dp)) else Modifier),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = tab,
                            color = if (selectedTab == tab) TextPrimary else TextSecondary,
                            style = Typography.labelMedium,
                            fontWeight = if (selectedTab == tab) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                when (selectedTab) {
                    "Tasks" -> {
                        val activeTasks = uiState.tasks.filter { !it.completed }
                        val completedTasks = uiState.tasks.filter { it.completed }
                        
                        item { SectionHeader(title = "Queue (${activeTasks.size})") }
                        items(activeTasks) { task ->
                            TaskRow(
                                title = task.title,
                                time = task.timeBlock ?: "Now",
                                isCompleted = false,
                                onToggle = { viewModel.toggleTask(task) },
                                onDelete = { viewModel.deleteTask(task.id) }
                            )
                        }
                        
                        if (completedTasks.isNotEmpty()) {
                            item { SectionHeader(title = "Completed") }
                            items(completedTasks) { task ->
                                TaskRow(
                                    title = task.title,
                                    time = task.timeBlock ?: "Done",
                                    isCompleted = true,
                                    onToggle = { viewModel.toggleTask(task) },
                                    onDelete = { viewModel.deleteTask(task.id) }
                                )
                            }
                        }
                    }
                    "Rituals" -> {
                        item { SectionHeader(title = "Active Rituals") }
                        items(uiState.habits) { habit ->
                            RitualRow(
                                name = habit.name,
                                streak = "${habit.streak} day streak",
                                isCompleted = habit.completedToday,
                                onToggle = { viewModel.toggleHabit(habit) },
                                onDelete = { viewModel.deleteHabit(habit.id) }
                            )
                        }
                    }
                    "Calendar" -> {
                        item { SectionHeader(title = "Upcoming Agenda") }
                        val activeTasks = uiState.tasks.filter { !it.completed }
                        if (activeTasks.isEmpty()) {
                            item {
                                Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                                    Text("No upcoming events scheduled.", color = TextMuted, style = Typography.labelMedium)
                                }
                            }
                        } else {
                            items(activeTasks) { task ->
                                CalendarRow(task.title, task.timeBlock ?: "Pending", task.category)
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(120.dp)) }
            }
        }
    }
}

@Composable
fun TaskRow(title: String, time: String, isCompleted: Boolean, onToggle: () -> Unit, onDelete: () -> Unit) {
    GlassCard(
        backgroundColor = if (isCompleted) ObsidianSurface.copy(alpha = 0.3f) else ObsidianSurfaceElevated.copy(alpha = 0.5f)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(if (isCompleted) SuccessGreen else Color.Transparent)
                    .border(2.dp, if (isCompleted) SuccessGreen else BorderSubtle, CircleShape)
                    .clickable { onToggle() },
                contentAlignment = Alignment.Center
            ) {
                if (isCompleted) Icon(Icons.Default.Check, contentDescription = null, tint = ObsidianBackground, modifier = Modifier.size(16.dp))
            }
            
            Spacer(Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = Typography.titleMedium,
                    color = if (isCompleted) TextMuted else TextPrimary,
                    textDecoration = if (isCompleted) androidx.compose.ui.text.style.TextDecoration.LineThrough else null
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(12.dp), tint = TextSecondary)
                    Spacer(Modifier.width(4.dp))
                    Text(time, style = Typography.labelSmall, color = TextSecondary)
                }
            }
            
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = ErrorRed.copy(alpha = 0.6f), modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
fun RitualRow(name: String, streak: String, isCompleted: Boolean, onToggle: () -> Unit, onDelete: () -> Unit) {
    GlassCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(AccentBlue.copy(alpha = 0.1f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, tint = AccentBlue)
            }
            
            Spacer(Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(name, style = Typography.titleMedium)
                Text(streak, style = Typography.labelSmall, color = SuccessGreen)
            }
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = ErrorRed.copy(alpha = 0.4f), modifier = Modifier.size(18.dp))
                }
                Switch(
                    checked = isCompleted,
                    onCheckedChange = { onToggle() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = AccentBlue,
                        checkedTrackColor = AccentBlue.copy(alpha = 0.3f),
                        uncheckedThumbColor = TextMuted,
                        uncheckedTrackColor = ObsidianSurface
                    )
                )
            }
        }
    }
}

@Composable
fun CalendarRow(title: String, time: String, category: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(ObsidianSurface)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .width(60.dp)
                .padding(end = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(time, style = Typography.labelSmall, color = AccentBlue, fontWeight = FontWeight.Bold)
        }
        
        Box(Modifier.width(1.dp).height(30.dp).background(BorderSubtle))
        Spacer(Modifier.width(16.dp))
        
        Column {
            Text(title, style = Typography.titleMedium)
            Text(category, style = Typography.labelSmall, color = TextSecondary)
        }
    }
}
