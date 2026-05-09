package com.todo.dailyroutine.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.todo.dailyroutine.data.model.HabitItem
import com.todo.dailyroutine.data.model.TaskItem
import com.todo.dailyroutine.ui.theme.*

@Composable
fun FlowHabitItem(
    habit: HabitItem,
    onToggle: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() },
        shape = RoundedCornerShape(20.dp),
        color = SurfaceCard,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(if (habit.completedToday) SuccessGreen.copy(alpha = 0.1f) else Color.White.copy(alpha = 0.05f))
                    .border(1.dp, if (habit.completedToday) SuccessGreen else Color.Transparent, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (habit.completedToday) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = SuccessGreen)
                } else {
                    Text(
                        text = habit.name.take(1).uppercase(),
                        color = Color.White.copy(alpha = 0.5f),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            Spacer(Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(habit.name, style = Typography.bodyLarge, color = Color.White)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.LocalFireDepartment, contentDescription = null, tint = Color(0xFFFF9500), modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("${habit.streak} day streak", style = Typography.labelSmall, color = TextSecondary)
                }
            }
            
            // Miniature Contribution Grid
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                repeat(5) { i ->
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(if (i < 3) SuccessGreen.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.1f))
                    )
                }
            }
        }
    }
}

@Composable
fun FlowTaskItem(
    task: TaskItem,
    onToggle: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() },
        shape = RoundedCornerShape(20.dp),
        color = SurfaceCard,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AnimatedCheckbox(
                checked = task.completed,
                onCheckedChange = { onToggle() }
            )
            
            Spacer(Modifier.width(16.dp))
            
            Column {
                Text(
                    text = task.title,
                    style = Typography.bodyLarge,
                    color = if (task.completed) TextSecondary else Color.White,
                    textDecoration = if (task.completed) androidx.compose.ui.text.style.TextDecoration.LineThrough else null
                )
                Text(
                    text = "${task.timeBlock} • ${task.category}",
                    style = Typography.labelSmall,
                    color = TextTertiary
                )
            }
        }
    }
}
