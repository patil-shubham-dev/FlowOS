package com.todo.dailyroutine.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import com.mohamedrejeb.richeditor.model.RichTextState
import com.todo.dailyroutine.ui.theme.AccentPrimary
import com.todo.dailyroutine.ui.theme.SurfaceCard
import com.todo.dailyroutine.ui.theme.SurfaceElevated

@Composable
fun JournalEditorToolbar(
    state: RichTextState,
    onAiClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        color = SurfaceCard.copy(alpha = 0.9f),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ToolbarIcon(
                    icon = Icons.Default.FormatBold,
                    isSelected = false, // state.isBold,
                    onClick = { state.toggleSpanStyle(androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.Bold)) }
                )
                ToolbarIcon(
                    icon = Icons.Default.FormatItalic,
                    isSelected = false, // state.isItalic,
                    onClick = { state.toggleSpanStyle(androidx.compose.ui.text.SpanStyle(fontStyle = FontStyle.Italic)) }
                )
                ToolbarIcon(
                    icon = Icons.Default.FormatListBulleted,
                    isSelected = false, // state.isOrderedList (not easily available in current API version)
                    onClick = { state.toggleUnorderedList() }
                )
            }

            Surface(
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable { onAiClick() }
                    .background(AccentPrimary.copy(alpha = 0.1f)),
                color = Color.Transparent,
                shape = CircleShape,
                border = androidx.compose.foundation.BorderStroke(1.dp, AccentPrimary.copy(alpha = 0.2f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = AccentPrimary, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("AI ACTIONS", style = MaterialTheme.typography.labelSmall, color = AccentPrimary, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun ToolbarIcon(
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) AccentPrimary.copy(alpha = 0.15f) else Color.Transparent)
    ) {
        Icon(icon, contentDescription = null, tint = if (isSelected) AccentPrimary else Color.White.copy(alpha = 0.6f))
    }
}
