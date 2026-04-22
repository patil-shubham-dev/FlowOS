package com.todo.dailyroutine.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.todo.dailyroutine.ui.theme.*

@Composable
fun BrainStateOrb(
    flowScore: Int,
    syncProgress: Float,
    flowHoursProgress: Float,
    vibeProgress: Float,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "Orb")
    
    val rotationOuter by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(8000, easing = LinearEasing)),
        label = "OuterRotation"
    )
    
    val rotationMiddle by infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(6000, easing = LinearEasing)),
        label = "MiddleRotation"
    )
    
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(tween(2000), RepeatMode.Reverse),
        label = "InnerPulse"
    )

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(240.dp)) {
            // Ring 1: Sync (Outer)
            drawArc(
                color = SuccessGreen,
                startAngle = rotationOuter,
                sweepAngle = 360f * syncProgress,
                useCenter = false,
                style = Stroke(width = 3.dp.toPx())
            )
            
            // Ring 2: Flow (Middle)
            drawCircle(
                color = AccentSecondary.copy(alpha = 0.1f),
                radius = 90.dp.toPx(),
                style = Stroke(width = 1.dp.toPx())
            )
            drawArc(
                color = AccentSecondary,
                startAngle = rotationMiddle,
                sweepAngle = 360f * flowHoursProgress,
                useCenter = false,
                style = Stroke(width = 4.dp.toPx()),
                size = this.size.copy(width = 180.dp.toPx(), height = 180.dp.toPx()),
                topLeft = center.copy(x = center.x - 90.dp.toPx(), y = center.y - 90.dp.toPx())
            )
            
            // Ring 3: Vibe (Inner Glow)
            drawCircle(
                color = AccentPrimary.copy(alpha = 0.2f * vibeProgress.coerceIn(0f, 1f)),
                radius = 60.dp.toPx() * pulseScale,
                style = Stroke(width = 6.dp.toPx())
            )
        }
        
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "$flowScore",
                style = MaterialTheme.typography.displayLarge.copy(
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White
                )
            )
            Text(
                text = "FLOW",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.5f)
            )
        }
    }
}
