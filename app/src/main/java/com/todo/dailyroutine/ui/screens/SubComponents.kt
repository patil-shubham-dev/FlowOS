package com.todo.dailyroutine.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airbnb.lottie.compose.*
import com.todo.dailyroutine.data.model.*
import com.todo.dailyroutine.data.local.entity.LocalJournalEntry
import com.todo.dailyroutine.ui.theme.*
import com.todo.dailyroutine.ui.components.AnimatedCheckbox

@Composable
fun BrainStateOrb(
    flowScore: Int,
    syncProgress: Float,
    flowHoursProgress: Float,
    vibeProgress: Float,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        val infiniteTransition = rememberInfiniteTransition(label = "orb_pulse")
        val pulseScale by infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 1.05f,
            animationSpec = infiniteRepeatable(
                animation = tween(2000, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulse"
        )

        // Outer Glow
        Canvas(modifier = Modifier.fillMaxSize().scale(pulseScale)) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(AccentPrimary.copy(alpha = 0.15f * syncProgress), Color.Transparent),
                    center = center,
                    radius = size.minDimension / 1.5f
                )
            )
        }

        // Inner Orb
        Surface(
            modifier = Modifier.size(200.dp).scale(pulseScale),
            shape = CircleShape,
            color = Color.Transparent,
            border = BorderStroke(2.dp, Brush.linearGradient(listOf(AccentPrimary, AccentSecondary)))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(SurfaceElevated, BackgroundBase),
                            center = Offset.Zero,
                            radius = 500f
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "$flowScore",
                        style = Typography.displayLarge.copy(fontSize = 64.sp),
                        color = Color.White,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = "SYNCED",
                        style = Typography.labelSmall,
                        color = AccentPrimary,
                        letterSpacing = 4.sp
                    )
                }
            }
        }
    }
}

@Composable
fun JournalStreakHeader(streak: Int) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
        shape = RoundedCornerShape(24.dp),
        color = SurfaceCard,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        Brush.linearGradient(
                            listOf(
                                AccentPrimary.copy(alpha = 0.1f),
                                AccentSecondary.copy(alpha = 0.1f)
                            )
                        ),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Whatshot, contentDescription = null, tint = AccentPrimary)
            }
            Spacer(Modifier.width(16.dp))
            Column {
                Text("Consistency Protocol", style = Typography.labelSmall, color = TextTertiary)
                Text("$streak Day Streak", style = Typography.titleLarge, color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun JournalEntryCard(entry: LocalJournalEntry) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        shape = RoundedCornerShape(20.dp),
        color = SurfaceElevated.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.03f))
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(entry.date, style = Typography.labelSmall, color = TextTertiary)
                Spacer(Modifier.weight(1f))
                Text("Vibe: ${entry.rating}/10", style = Typography.labelSmall, color = AccentPrimary)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = entry.content.take(100) + if(entry.content.length > 100) "..." else "",
                style = Typography.bodyMedium,
                color = TextSecondary
            )
        }
    }
}

@Composable
fun FlowHabitItem(habit: HabitItem, onToggle: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        color = SurfaceCard,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AnimatedCheckbox(checked = habit.completedToday, onCheckedChange = { onToggle() })
            Spacer(Modifier.width(16.dp))
            Column {
                Text(habit.name, style = Typography.titleMedium, color = Color.White)
                Text("${habit.streak} day streak", style = Typography.labelSmall, color = TextTertiary)
            }
        }
    }
}

@Composable
fun FlowTaskItem(task: TaskItem, onToggle: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        color = SurfaceCard,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AnimatedCheckbox(checked = task.completed, onCheckedChange = { onToggle() })
            Spacer(Modifier.width(16.dp))
            Column {
                Text(task.title, style = Typography.titleMedium, color = Color.White)
                Text(task.category, style = Typography.labelSmall, color = TextTertiary)
            }
        }
    }
}

@Composable
fun TypingIndicator() {
    Row(
        modifier = Modifier.padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        repeat(3) { index ->
            val infiniteTransition = rememberInfiniteTransition()
            val alpha by infiniteTransition.animateFloat(
                initialValue = 0.2f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(600, delayMillis = index * 200),
                    repeatMode = RepeatMode.Reverse
                )
            )
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(AccentPrimary.copy(alpha = alpha), CircleShape)
            )
        }
    }
}

@Composable
fun ChatBubble(message: ChatMessage) {
    val isUser = message.role == "user"
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Surface(
            color = if (isUser) AccentPrimary else SurfaceElevated,
            shape = RoundedCornerShape(
                topStart = 20.dp,
                topEnd = 20.dp,
                bottomStart = if (isUser) 20.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 20.dp
            ),
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Text(
                text = message.content,
                modifier = Modifier.padding(16.dp),
                color = Color.White,
                style = Typography.bodyLarge
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = if (isUser) "Identity" else "Oracle",
            style = Typography.labelSmall,
            color = TextTertiary,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
    }
}
