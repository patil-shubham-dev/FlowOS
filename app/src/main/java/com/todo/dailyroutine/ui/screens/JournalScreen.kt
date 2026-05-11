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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.todo.dailyroutine.ui.components.*
import com.todo.dailyroutine.ui.theme.*
import com.todo.dailyroutine.ui.viewmodel.HomeViewModel
import kotlinx.coroutines.launch

@Composable
fun JournalScreen(viewModel: HomeViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    var showAddDialog by remember { mutableStateOf(false) }
    var reflectionText by remember { mutableStateOf("") }
    var rating by remember { mutableStateOf(7) }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            containerColor = ObsidianSurface,
            title = { 
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Daily Reflection", color = TextPrimary)
                    Spacer(Modifier.weight(1f))
                    if (uiState.isAiProcessing) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = AccentBlue, strokeWidth = 2.dp)
                    } else {
                        IconButton(onClick = {
                            scope.launch {
                                val enhanced = viewModel.enhanceJournalEntry(reflectionText)
                                reflectionText = enhanced
                            }
                        }) {
                            Icon(Icons.Default.AutoAwesome, "Enhance with AI", tint = AccentBlue)
                        }
                    }
                }
            },
            text = {
                Column {
                    TextField(
                        value = reflectionText,
                        onValueChange = { reflectionText = it },
                        placeholder = { Text("What's on your mind?", color = TextMuted) },
                        modifier = Modifier.fillMaxWidth().height(200.dp),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = ObsidianBackground,
                            unfocusedContainerColor = ObsidianBackground,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text("Energy Level: $rating/10", color = TextSecondary, style = Typography.labelMedium)
                    Slider(
                        value = rating.toFloat(),
                        onValueChange = { rating = it.toInt() },
                        valueRange = 1f..10f,
                        steps = 8,
                        colors = SliderDefaults.colors(thumbColor = AccentBlue, activeTrackColor = AccentBlue)
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
                }) { Text("SAVE", color = AccentBlue) }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) { Text("CANCEL", color = TextMuted) }
            }
        )
    }

    Scaffold(
        containerColor = ObsidianBackground
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            item { Spacer(Modifier.height(16.dp)) }

            // Header & Search
            item {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Reflections", style = Typography.displaySmall)
                        IconButton(onClick = { showAddDialog = true }) {
                            Icon(Icons.Default.AddCircle, null, tint = AccentBlue, modifier = Modifier.size(32.dp))
                        }
                    }
                    
                    Spacer(Modifier.height(16.dp))
                    
                    TextField(
                        value = uiState.journalSearchQuery,
                        onValueChange = { viewModel.setSearchQuery(it) },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Search your logs...", color = TextMuted) },
                        leadingIcon = { Icon(Icons.Default.Search, null, tint = TextMuted) },
                        trailingIcon = {
                            if (uiState.journalSearchQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                    Icon(Icons.Default.Close, null, tint = TextMuted)
                                }
                            }
                        },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = ObsidianSurface,
                            unfocusedContainerColor = ObsidianSurface,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }

            // Calendar Strip (Interactive)
            item {
                Column {
                    Text(
                        if (uiState.journalFilterDate == null) "This Week" else "Filtered: ${uiState.journalFilterDate}",
                        style = Typography.labelSmall,
                        color = TextMuted,
                        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(7) { i ->
                            val dateObj = java.time.LocalDate.now().minusDays(i.toLong())
                            val dateStr = dateObj.toString()
                            val isSelected = uiState.journalFilterDate == dateStr
                            
                            CalendarDay(
                                day = dateObj.dayOfWeek.name.take(1),
                                date = dateObj.dayOfMonth.toString(),
                                active = isSelected,
                                onClick = {
                                    if (isSelected) viewModel.setFilterDate(null)
                                    else viewModel.setFilterDate(dateStr)
                                }
                            )
                        }
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
                                    reflectionText = "Feeling $mood. "
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
                        Text(
                            if (uiState.journalSearchQuery.isNotEmpty() || uiState.journalFilterDate != null) 
                                "No matches found." else "No reflections recorded yet.",
                            color = TextMuted,
                            style = Typography.labelMedium
                        )
                    }
                }
            } else {
                items(uiState.journalEntries.sortedByDescending { it.timestamp }) { entry ->
                    JournalEntryCard(
                        date = entry.date,
                        content = entry.content,
                        insight = entry.aiInsight ?: "Intelligence report incoming...",
                        rating = entry.rating
                    )
                }
            }

            item { Spacer(Modifier.height(120.dp)) }
        }
    }
}

@Composable
fun CalendarDay(day: String, date: String, active: Boolean, onClick: () -> Unit) {
    val glowColor = if (active) AccentBlue else Color.Transparent
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(52.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (active) AccentBlue else ObsidianSurface)
            .then(if (active) Modifier.shadow(8.dp, RoundedCornerShape(16.dp), ambientColor = glowColor, spotColor = glowColor) else Modifier)
            .border(1.dp, if (active) AccentBlue else BorderSubtle, RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(vertical = 14.dp)
    ) {
        Text(day, style = Typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = if (active) ObsidianBackground else TextMuted)
        Spacer(Modifier.height(4.dp))
        Text(date, style = Typography.titleLarge, color = if (active) ObsidianBackground else TextPrimary, fontWeight = FontWeight.Black)
    }
}

@Composable
fun JournalEntryCard(date: String, content: String, insight: String, rating: Int) {
    val energyColor = when {
        rating >= 8 -> SuccessGreen
        rating >= 5 -> AccentBlue
        else -> AccentRose
    }
    
    GlassCard(
        backgroundColor = ObsidianSurfaceElevated.copy(alpha = 0.3f),
        borderColor = BorderSubtle.copy(alpha = 0.4f)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).background(energyColor, CircleShape))
                    Spacer(Modifier.width(8.dp))
                    Text(date.uppercase(), style = Typography.labelSmall.copy(letterSpacing = 1.sp), color = TextMuted)
                }
                
                Surface(
                    color = energyColor.copy(alpha = 0.1f),
                    shape = CircleShape,
                    border = BorderStroke(1.dp, energyColor.copy(alpha = 0.2f))
                ) {
                    Text(
                        "$rating", 
                        style = Typography.labelSmall.copy(fontWeight = FontWeight.Bold), 
                        color = energyColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
            
            Spacer(Modifier.height(20.dp))
            Text(
                content, 
                style = Typography.bodyLarge.copy(lineHeight = 26.sp), 
                color = TextPrimary
            )
            
            Spacer(Modifier.height(24.dp))
            
            // Premium AI Analysis block
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Brush.linearGradient(listOf(AccentBlue.copy(alpha = 0.08f), Color.Transparent)))
                    .border(BorderStroke(1.dp, Brush.linearGradient(listOf(AccentBlue.copy(alpha = 0.15f), Color.Transparent))), RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = AccentBlue, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(10.dp))
                        Text("ORACLE COGNITION", style = Typography.labelSmall.copy(letterSpacing = 1.sp, fontWeight = FontWeight.Bold), color = AccentBlue)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(insight, style = Typography.bodyMedium, color = TextSecondary)
                }
            }
        }
    }
}
