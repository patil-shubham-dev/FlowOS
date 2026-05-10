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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.todo.dailyroutine.ui.components.*
import com.todo.dailyroutine.ui.theme.*
import com.todo.dailyroutine.ui.viewmodel.HomeViewModel

@Composable
fun DashboardScreen(viewModel: HomeViewModel, navController: NavHostController? = null) {
    val uiState by viewModel.uiState.collectAsState()
    var showAddTaskDialog by remember { mutableStateOf(false) }
    var newTaskTitle by remember { mutableStateOf("") }

    if (showAddTaskDialog) {
        AlertDialog(
            onDismissRequest = { showAddTaskDialog = false },
            containerColor = ObsidianSurface,
            title = { Text("Quick Add Task", color = TextPrimary) },
            text = {
                TextField(
                    value = newTaskTitle,
                    onValueChange = { newTaskTitle = it },
                    placeholder = { Text("What needs to be done?", color = TextMuted) },
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
                    if (newTaskTitle.isNotBlank()) {
                        viewModel.addTask(newTaskTitle, "General")
                        newTaskTitle = ""
                        showAddTaskDialog = false
                    }
                }) {
                    Text("ADD", color = AccentBlue)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddTaskDialog = false }) {
                    Text("CANCEL", color = TextMuted)
                }
            }
        )
    }

    Scaffold(
        containerColor = ObsidianBackground
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            item { Spacer(Modifier.height(16.dp)) }

            // Header Section
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("FlowOS", style = Typography.displayLarge, color = TextPrimary)
                        Text("Operational Status: Optimal", style = Typography.labelMedium, color = SuccessGreen)
                    }
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(ObsidianSurfaceElevated, CircleShape)
                            .border(1.dp, BorderSubtle, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Person, contentDescription = null, tint = TextPrimary)
                    }
                }
            }

            // Hero Metric (Vitality Pulse)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    VitalityPulse(
                        progress = uiState.stats.progressPercent / 100f,
                        modifier = Modifier.size(160.dp)
                    )
                    
                    Spacer(Modifier.width(24.dp))
                    
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Operational Flow", style = Typography.labelSmall, color = TextMuted)
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text("${uiState.stats.progressPercent}", style = Typography.displayLarge.copy(fontSize = 64.sp), color = TextPrimary)
                            Text("%", style = Typography.headlineMedium, color = TextMuted, modifier = Modifier.padding(bottom = 12.dp))
                        }
                        
                        Text("Status: ${uiState.stats.level}", style = Typography.labelMedium, color = AccentBlue)
                        
                        Spacer(Modifier.height(12.dp))
                        
                        GradientProgressBar(
                            progress = uiState.stats.progressPercent / 100f,
                            gradient = FlowGradient
                        )
                    }
                }
            }
            
            // Flow Consistency Heatmap
            item {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Operational Consistency", style = Typography.labelSmall, color = TextMuted)
                        Text("LAST 7 DAYS", style = Typography.labelSmall, color = AccentBlue.copy(alpha = 0.5f))
                    }
                    Spacer(Modifier.height(12.dp))
                    GlassCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            (0..6).forEach { i ->
                                val intensity = when(i) {
                                    0 -> 0.2f 1 -> 0.8f 2 -> 1.0f 3 -> 0.4f 4 -> 0.9f 5 -> 0.7f else -> 0.1f
                                }
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            Brush.verticalGradient(
                                                listOf(AccentBlue.copy(alpha = intensity), AccentCyan.copy(alpha = intensity * 0.5f))
                                            )
                                        )
                                        .border(1.dp, if (intensity > 0.5f) AccentCyan.copy(alpha = 0.5f) else BorderSubtle, RoundedCornerShape(8.dp))
                                )
                            }
                        }
                    }
                }
            }

            // Control Pad (Quick Actions)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    QuickActionItem("COMMAND", Icons.Default.Add, Modifier.weight(1f)) {
                        showAddTaskDialog = true
                    }
                    QuickActionItem("ORACLE", Icons.Default.AutoAwesome, Modifier.weight(1f)) {
                        navController?.navigate("oracle")
                    }
                    QuickActionItem("JOURNAL", Icons.Default.EditNote, Modifier.weight(1f)) {
                        navController?.navigate("journal")
                    }
                }
            }

            // Oracle Insight
            item {
                GlassCard(
                    backgroundColor = AccentBlue.copy(alpha = 0.05f),
                    borderColor = AccentBlue.copy(alpha = 0.2f)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = AccentBlue, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text("Oracle Insight", style = Typography.labelSmall, color = AccentBlue)
                            Text(uiState.oracleInsight ?: "Analyzing execution patterns...", style = Typography.bodyMedium)
                        }
                    }
                }
            }

            // Task Queue Preview
            item {
                SectionHeader(title = "Task Queue", actionLabel = "View All", onActionClick = {
                    navController?.navigate("flow")
                })
            }

            val pendingTasks = uiState.tasks.filter { !it.completed }.take(3)
            if (pendingTasks.isEmpty()) {
                item {
                    Text("No pending tasks. Protocol satisfied.", color = TextMuted, style = Typography.labelMedium)
                }
            } else {
                items(pendingTasks) { task ->
                    DashboardTaskRow(task.title, task.category, task.completed) {
                        viewModel.toggleTask(task)
                    }
                }
            }

            item { Spacer(Modifier.height(120.dp)) }
        }
    }
}

@Composable
fun QuickActionItem(label: String, icon: ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(ObsidianSurface)
            .clickable { onClick() }
            .padding(vertical = 16.dp)
    ) {
        Icon(icon, contentDescription = null, tint = AccentBlue, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(8.dp))
        Text(label, style = Typography.labelSmall, color = TextPrimary)
    }
}

@Composable
fun DashboardTaskRow(title: String, category: String, completed: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(ObsidianSurface)
            .clickable { onToggle() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .border(2.dp, if (completed) SuccessGreen else BorderSubtle, CircleShape)
                .background(if (completed) SuccessGreen else Color.Transparent),
            contentAlignment = Alignment.Center
        ) {
            if (completed) Icon(Icons.Default.Check, contentDescription = null, tint = ObsidianBackground, modifier = Modifier.size(14.dp))
        }
        Spacer(Modifier.width(16.dp))
        Column {
            Text(title, style = Typography.titleMedium, color = if (completed) TextMuted else TextPrimary)
            Text(category, style = Typography.labelSmall, color = TextSecondary)
        }
    }
}
