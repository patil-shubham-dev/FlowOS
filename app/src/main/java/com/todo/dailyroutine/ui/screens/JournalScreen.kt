package com.todo.dailyroutine.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
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
fun JournalScreen(viewModel: HomeViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var reflectionText by remember { mutableStateOf("") }
    var rating by remember { mutableIntStateOf(7) }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            containerColor = ObsidianSurface,
            title = { Text("Daily Reflection", color = TextPrimary) },
            text = {
                Column {
                    TextField(
                        value = reflectionText,
                        onValueChange = { reflectionText = it },
                        placeholder = { Text("What's on your mind?", color = TextMuted) },
                        modifier = Modifier.fillMaxWidth().height(120.dp),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = ObsidianBackground,
                            unfocusedContainerColor = ObsidianBackground,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )
                    Spacer(Modifier.height(16.dp))
                    Text("Energy Level: $rating/10", color = TextSecondary, style = Typography.labelMedium)
                    Slider(
                        value = rating.toFloat(),
                        onValueChange = { rating = it.toInt() },
                        valueRange = 1f..10f,
                        steps = 8,
                        colors = SliderDefaults.colors(
                            thumbColor = AccentBlue,
                            activeTrackColor = AccentBlue,
                            inactiveTrackColor = BorderSubtle
                        )
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (reflectionText.isNotBlank()) {
                        viewModel.addJournalEntry(reflectionText, rating)
                        reflectionText = ""
                        showAddDialog = false
                    }
                }) {
                    Text("SAVE", color = AccentBlue)
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            item { Spacer(Modifier.height(16.dp)) }

            // Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Reflections", style = Typography.headlineLarge)
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = AccentBlue)
                    }
                }
            }

            // Calendar Strip (Simplified)
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(7) { i ->
                        val day = when(i) {
                            0 -> "M" 1 -> "T" 2 -> "W" 3 -> "T" 4 -> "F" 5 -> "S" else -> "S"
                        }
                        CalendarDay(day, "${15 + i}", active = i == 2)
                    }
                }
            }

            // Quick Reflection Prompt
            item {
                GlassCard(backgroundColor = AccentBlue.copy(alpha = 0.05f)) {
                    Column {
                        Text("Current vibe?", style = Typography.labelMedium, color = AccentBlue)
                        Spacer(Modifier.height(8.dp))
                        Text("How are you feeling about your progress today?", style = Typography.titleMedium)
                        Spacer(Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            listOf("⚡ Optimized", "🔋 Neutral", "📉 Low").forEach { mood ->
                                SuggestionChip(mood) { 
                                    reflectionText = "Feeling $mood."
                                    showAddDialog = true 
                                }
                            }
                        }
                    }
                }
            }

            // Historical Entries
            item { SectionHeader(title = "Historical Logs") }

            if (uiState.journalEntries.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) {
                        Text("No reflections recorded yet.", color = TextMuted, style = Typography.labelMedium)
                    }
                }
            } else {
                items(uiState.journalEntries.sortedByDescending { it.date }) { entry ->
                    JournalEntryCard(
                        date = entry.date,
                        content = entry.content,
                        insight = entry.aiInsight ?: "Insight generation pending.",
                        rating = entry.rating
                    )
                }
            }

            item { Spacer(Modifier.height(120.dp)) }
        }
    }
}

@Composable
fun CalendarDay(day: String, date: String, active: Boolean) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (active) AccentBlue else ObsidianSurface)
            .border(1.dp, if (active) AccentBlue else BorderSubtle, RoundedCornerShape(12.dp))
            .padding(vertical = 12.dp)
    ) {
        Text(day, style = Typography.labelSmall, color = if (active) ObsidianBackground else TextSecondary)
        Text(date, style = Typography.titleMedium, color = if (active) ObsidianBackground else TextPrimary, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun JournalEntryCard(date: String, content: String, insight: String, rating: Int) {
    GlassCard {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(date, style = Typography.labelSmall, color = TextSecondary)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Star, contentDescription = null, tint = WarningYellow, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("$rating/10", style = Typography.labelSmall, color = TextPrimary)
                }
            }
            
            Spacer(Modifier.height(12.dp))
            Text(content, style = Typography.bodyMedium, lineHeight = 20.sp)
            
            Spacer(Modifier.height(16.dp))
            
            // AI Analysis block
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ObsidianBackground.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                    .padding(12.dp)
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = AccentBlue, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Oracle Analysis", style = Typography.labelSmall, color = AccentBlue)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(insight, style = Typography.labelMedium, color = TextSecondary)
                }
            }
        }
    }
}
