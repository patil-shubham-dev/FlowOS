package com.todo.dailyroutine.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.todo.dailyroutine.ui.theme.*

data class Mood(val label: String, val color: Color, val emoji: String)

val moods = listOf(
    Mood("Productive", SuccessGreen, "🚀"),
    Mood("Focused", AccentPrimary, "🧠"),
    Mood("Tired", Color(0xFF94A3B8), "😴"),
    Mood("Stressed", Color(0xFFF43F5E), "😫"),
    Mood("Optimistic", Color(0xFFFACC15), "☀️"),
    Mood("Anxious", Color(0xFFA855F7), "😰")
)

@Composable
fun MoodSelector(
    selectedMood: Mood?,
    onMoodSelected: (Mood) -> Unit
) {
    Column(modifier = Modifier.padding(vertical = 16.dp)) {
        Text("How's the signal today?", style = Typography.labelSmall, color = TextSecondary, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(moods) { mood ->
                val isSelected = selectedMood == mood
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(if (isSelected) mood.color.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.05f))
                        .border(1.dp, if (isSelected) mood.color else Color.Transparent, CircleShape)
                        .clickable { onMoodSelected(mood) }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(mood.emoji, fontSize = 16.sp)
                        Spacer(Modifier.width(8.dp))
                        Text(mood.label, style = Typography.labelSmall, color = if (isSelected) Color.White else TextSecondary)
                    }
                }
            }
        }
    }
}

@Composable
fun AiJournalPrompt(prompt: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        color = AccentPrimary.copy(alpha = 0.05f),
        border = BorderStroke(1.dp, AccentPrimary.copy(alpha = 0.1f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = androidx.compose.material.icons.Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = AccentPrimary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = prompt,
                style = Typography.bodyMedium,
                color = AccentPrimary.copy(alpha = 0.8f)
            )
        }
    }
}
