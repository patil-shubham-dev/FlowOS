package com.todo.dailyroutine.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Cyclone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.todo.dailyroutine.data.model.ChatMessage
import com.todo.dailyroutine.ui.viewmodel.*
import com.todo.dailyroutine.ui.components.*
import com.todo.dailyroutine.ui.theme.*

@Composable
fun AiScreen(viewModel: AiViewModel) {
    val state by viewModel.uiState.collectAsState()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundBase)
            .padding(horizontal = 24.dp)
    ) {
        Spacer(Modifier.height(56.dp))
        
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Cyclone, contentDescription = null, tint = AccentPrimary, modifier = Modifier.size(32.dp))
            Spacer(Modifier.width(12.dp))
            Text("Oracle", style = Typography.displayLarge, color = Color.White)
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 24.dp)
        ) {
            items(state.chatHistory, key = { it.timestamp }) { message ->
                ChatBubble(message)
            }
            if (state.loading) {
                item { TypingIndicator() }
            }
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0xFF7C5CFF).copy(alpha = 0.15f),
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF7C5CFF).copy(alpha = 0.3f))
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Bolt, contentDescription = null, tint = Color(0xFF7C5CFF))
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Real-time Intel", style = Typography.labelLarge, color = Color.White)
                            Text("Synchronize your intelligence.", style = Typography.labelSmall, color = TextSecondary)
                        }
                    }
                }
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth().padding(bottom = 100.dp),
            shape = RoundedCornerShape(28.dp),
            color = SurfaceElevated,
            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
        ) {
            Row(
                Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                androidx.compose.foundation.text.BasicTextField(
                    value = state.prompt,
                    onValueChange = { viewModel.onPromptChanged(it) },
                    modifier = Modifier.weight(1f),
                    textStyle = TextStyle(color = Color.White, fontSize = 16.sp, fontFamily = InterFontFamily),
                    cursorBrush = SolidColor(AccentPrimary),
                    decorationBox = { innerTextField ->
                        if (state.prompt.isEmpty()) Text("Command...", color = TextTertiary)
                        innerTextField()
                    }
                )
                
                IconButton(
                    onClick = { viewModel.sendMessage() },
                    modifier = Modifier.size(40.dp).background(AccentPrimary, CircleShape),
                    enabled = state.prompt.isNotBlank() && !state.loading
                ) {
                    Icon(Icons.Default.ArrowUpward, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}
