package com.todo.dailyroutine.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.todo.dailyroutine.ui.theme.*

data class NavItem(
    val route: String,
    val icon: ImageVector,
    val label: String
)

@Composable
fun FloatingPillNav(
    items: List<NavItem>,
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .padding(horizontal = 24.dp)
            .background(SurfaceElevated, RoundedCornerShape(32.dp))
            .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(32.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEach { item ->
            val isSelected = currentRoute == item.route
            
            val pillColor by animateColorAsState(
                if (isSelected) AccentPrimary else Color.Transparent,
                label = "PillColor"
            )
            
            val iconColor by animateColorAsState(
                if (isSelected) Color.White else TextTertiary,
                label = "IconColor"
            )

            val scale by animateFloatAsState(
                if (isSelected) 1.1f else 1.0f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                label = "Scale"
            )

            Box(
                modifier = Modifier
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
                    .clip(RoundedCornerShape(24.dp))
                    .background(pillColor)
                    .clickable { onNavigate(item.route) }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.label,
                        tint = iconColor,
                        modifier = Modifier.size(20.dp)
                    )
                    if (isSelected) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = item.label,
                            color = Color.White,
                            style = Typography.labelLarge,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}
