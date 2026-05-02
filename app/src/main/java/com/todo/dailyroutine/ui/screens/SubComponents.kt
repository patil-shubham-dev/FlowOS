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
import androidx.compose.ui.draw.*
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
            targetValue = 1.08f,
            animationSpec = infiniteRepeatable(
                animation = tween(3000, easing = LinearOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulse"
        )
        
        val rotation by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(10000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "rotation"
        )

        // Ultra-Premium Multi-Layer Glow
        Canvas(modifier = Modifier.fillMaxSize().scale(pulseScale).graphicsLayer { alpha = 0.6f }) {
            drawCircle(
                brush = Brush.radialGradient(
                    0.0f to AccentPrimary.copy(alpha = 0.4f),
                    0.5f to AccentSecondary.copy(alpha = 0.2f),
                    1.0f to Color.Transparent,
                    center = center,
                    radius = size.minDimension / 1.2f
                )
            )
        }

        // Rotating Energy Ring
        Box(
            modifier = Modifier
                .size(240.dp)
                .rotate(rotation)
                .border(
                    width = 1.dp,
                    brush = Brush.sweepGradient(
                        0.0f to Color.Transparent,
                        0.5f to AccentPrimary.copy(alpha = 0.5f),
                        1.0f to Color.Transparent
                    ),
                    shape = CircleShape
                )
        )

        // Core Glass Orb
        Surface(
            modifier = Modifier
                .size(200.dp)
                .scale(pulseScale)
                .shadow(elevation = 40.dp, shape = CircleShape, spotColor = AccentPrimary.copy(alpha = 0.5f)),
            shape = CircleShape,
            color = Color.Transparent,
            border = BorderStroke(1.5.dp, Brush.linearGradient(listOf(Color.White.copy(alpha = 0.2f), Color.Transparent)))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.White.copy(alpha = 0.05f), Color.Transparent)
                        )
                    )
                    .background(
                        Brush.radialGradient(
                            colors = listOf(SurfaceElevated.copy(alpha = 0.8f), BackgroundBase.copy(alpha = 0.9f)),
                            center = Offset(0.5f, 0f),
                            radius = 600f
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "$flowScore",
                        style = Typography.displayLarge.copy(
                            fontSize = 72.sp,
                            brush = Brush.verticalGradient(listOf(Color.White, Color.White.copy(alpha = 0.7f)))
                        ),
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = "PROTOCOL SYNC",
                        style = Typography.labelSmall,
                        color = AccentPrimary,
                        letterSpacing = 5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun JournalStreakHeader(streak: Int) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        shape = RoundedCornerShape(32.dp),
        color = SurfaceCard,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
    ) {
        Row(
            modifier = Modifier.padding(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(AccentPrimary.copy(alpha = 0.1f), CircleShape)
                    .border(1.dp, Color.White.copy(alpha = 0.05f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Whatshot,
                    contentDescription = null,
                    tint = AccentPrimary,
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(Modifier.width(20.dp))
            Column {
                Text("Operational Continuity", style = Typography.labelSmall, color = TextSecondary)
                Text(
                    "$streak Day Sequence",
                    style = Typography.headlineSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun JournalEntryCard(entry: LocalJournalEntry) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        shape = RoundedCornerShape(24.dp),
        color = SurfaceCard,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.03f))
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = Color.White.copy(alpha = 0.05f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        entry.date,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = Typography.labelSmall,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                }
                Spacer(Modifier.weight(1f))
                Text("Vibe ${entry.rating}/10", style = Typography.labelSmall, color = AccentPrimary, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text = entry.content,
                style = Typography.bodyLarge,
                color = TextSecondary,
                maxLines = 3,
                lineHeight = 24.sp
            )
            
            if (!entry.aiInsight.isNullOrBlank()) {
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = AccentPrimary, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(entry.aiInsight, style = Typography.labelSmall, color = AccentPrimary.copy(alpha = 0.8f))
                }
            }
        }
    }
}

@Composable
fun FlowHabitItem(habit: HabitItem, onToggle: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        shape = RoundedCornerShape(24.dp),
        color = SurfaceCard,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AnimatedCheckbox(checked = habit.completedToday, onCheckedChange = { onToggle() })
            Spacer(Modifier.width(20.dp))
            Column(Modifier.weight(1f)) {
                Text(habit.name, style = Typography.titleMedium, color = Color.White)
                Text("${habit.streak} day streak", style = Typography.labelSmall, color = TextSecondary)
            }
            if (habit.completedToday) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
fun FlowTaskItem(task: TaskItem, onToggle: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        shape = RoundedCornerShape(24.dp),
        color = SurfaceCard,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AnimatedCheckbox(checked = task.completed, onCheckedChange = { onToggle() })
            Spacer(Modifier.width(20.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    task.title,
                    style = Typography.titleMedium,
                    color = if (task.completed) Color.White.copy(alpha = 0.4f) else Color.White,
                    textDecoration = if (task.completed) androidx.compose.ui.text.style.TextDecoration.LineThrough else null
                )
                Text(task.category.uppercase(), style = Typography.labelSmall, color = TextSecondary, letterSpacing = 1.sp)
            }
            
            Surface(
                color = Color.White.copy(alpha = 0.05f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    "E${task.energyRequired}",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = Typography.labelSmall,
                    color = AccentPrimary
                )
            }
        }
    }
}


