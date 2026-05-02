package com.todo.dailyroutine.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateColor
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.todo.dailyroutine.ui.theme.*

@Composable
fun FlowStatCard(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(110.dp),
        shape = RoundedCornerShape(20.dp),
        color = SurfaceCard
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = value,
                    style = Typography.headlineLarge.copy(color = Color.White)
                )
                Text(
                    text = label,
                    style = Typography.labelSmall.copy(color = TextSecondary)
                )
            }
        }
    }
}

@Composable
fun PillTabs(
    selectedTab: String,
    tabs: List<String>,
    onTabSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .background(SurfaceElevated, RoundedCornerShape(24.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        tabs.forEach { tab ->
            val isSelected = selectedTab == tab
            val bgColor by animateColorAsState(
                targetValue = if (isSelected) SurfaceCard else Color.Transparent,
                label = "pill_bg"
            )
            val textColor by animateColorAsState(
                targetValue = if (isSelected) Color.White else TextSecondary,
                label = "pill_text"
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(bgColor)
                    .clickable { onTabSelected(tab) }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = tab,
                    color = textColor,
                    style = Typography.labelLarge,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}

@Composable
fun ShimmerBox(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(12.dp)
) {
    val infiniteTransition = rememberInfiniteTransition()
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    Box(
        modifier = modifier
            .clip(shape)
            .background(SurfaceElevated.copy(alpha = alpha))
    )
}

@Composable
fun PrimaryGradientButton(
    text: String,
    loading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) 0.95f else 1f, label = "button_scale")

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .graphicsLayer(scaleX = scale, scaleY = scale)
            .clip(RoundedCornerShape(20.dp))
            .clickable(
                enabled = !loading && enabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        color = Color.Transparent
    ) {
        Box(
            modifier = Modifier.background(
                if (enabled) AccentGradient else Brush.linearGradient(listOf(SurfaceElevated, SurfaceElevated))
            ),
            contentAlignment = Alignment.Center
        ) {
            if (loading) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
            } else {
                Text(
                    text = text,
                    style = Typography.titleMedium,
                    color = if (enabled) Color.White else TextTertiary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun AnimatedCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val strokeWidth = 2.dp
    val checkboxSize = 24.dp
    
    val transition = updateTransition(checked, label = "checkbox_transition")
    val borderColor by transition.animateColor(label = "border_color") { isChecked ->
        if (isChecked) AccentPrimary else TextTertiary
    }
    val fillAlpha by transition.animateFloat(label = "fill_alpha") { if (it) 1f else 0f }
    val scale by transition.animateFloat(label = "scale") { if (it) 1f else 0.8f }

    Box(
        modifier = modifier
            .size(checkboxSize)
            .clip(CircleShape)
            .background(AccentPrimary.copy(alpha = fillAlpha))
            .border(strokeWidth, borderColor, CircleShape)
            .clickable { onCheckedChange(!checked) }
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        if (checked) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.scale(scale)
            )
        }
    }
}

@Composable
fun FlowProgressHeader(
    ritualsDone: Int,
    ritualsTotal: Int,
    objectivesDone: Int,
    objectivesTotal: Int,
    flowScore: Int,
    overallProgress: Float,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = SurfaceCard,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MiniStat("Rituals", ritualsDone, ritualsTotal, Color(0xFF30D158))
                MiniStat("Objectives", objectivesDone, objectivesTotal, AccentPrimary)
                Column(horizontalAlignment = Alignment.End) {
                    Text("Flow Score", style = Typography.labelSmall, color = TextTertiary)
                    Text(
                        "$flowScore", 
                        style = Typography.headlineMedium, 
                        color = Color.White,
                        fontWeight = FontWeight.Black
                    )
                }
            }
            
            Spacer(Modifier.height(16.dp))
            
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(CircleShape)
                    .background(SurfaceElevated)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(overallProgress)
                        .fillMaxHeight()
                        .background(AccentGradient)
                )
            }
        }
    }
}

@Composable
private fun MiniStat(label: String, done: Int, total: Int, color: Color) {
    Column {
        Text(label, style = Typography.labelSmall, color = TextTertiary)
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("$done/$total", style = Typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier
                    .size(width = 40.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.2f))
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(if (total > 0) done.toFloat() / total else 0f)
                        .fillMaxHeight()
                        .background(color)
                )
            }
        }
    }
}

@Composable
fun DashboardScaffold(
    title: String,
    onBackClick: (() -> Unit)? = null,
    content: LazyListScope.() -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundBase)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = 24.dp,
                bottom = 140.dp
            )
        ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(bottom = 24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (onBackClick != null) {
                        IconButton(
                            onClick = onBackClick,
                            modifier = Modifier
                                .padding(end = 16.dp)
                                .size(48.dp)
                                .background(Color.White.copy(alpha = 0.05f), CircleShape)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = Color.White)
                        }
                    }
                    Text(
                        text = title,
                        style = Typography.displayLarge.copy(fontSize = 42.sp),
                        color = Color.White,
                        fontWeight = FontWeight.Black
                    )
                }
            }
            content()
        }
    }
}

@Composable
fun AnimatedCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val transition = updateTransition(checked, label = "checkbox")
    val scale by transition.animateFloat(label = "scale") { if (it) 1f else 0.8f }
    val alpha by transition.animateFloat(label = "alpha") { if (it) 1f else 0.4f }
    val color by transition.animateColor(label = "color") { if (it) SuccessGreen else Color.White.copy(alpha = 0.1f) }

    Box(
        modifier = modifier
            .size(32.dp)
            .scale(scale)
            .background(color.copy(alpha = 0.1f), CircleShape)
            .border(1.dp, color, CircleShape)
            .clickable { onCheckedChange(!checked) },
        contentAlignment = Alignment.Center
    ) {
        if (checked) {
            Icon(
                Icons.Default.Check,
                contentDescription = null,
                tint = SuccessGreen,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
