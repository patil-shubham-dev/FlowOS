package com.todo.dailyroutine.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.todo.dailyroutine.ui.viewmodel.*
import com.todo.dailyroutine.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun AiScreen(viewModel: AiViewModel) {
    val state by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(state.chatHistory.size) {
        if (state.chatHistory.isNotEmpty()) {
            listState.animateScrollToItem(state.chatHistory.size - 1)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepBackground)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Premium Header
            PremiumAiHeader(
                activeModel = state.selectedModel,
                onModelClick = { /* Show model picker */ }
            )

            // Chat Messages
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                items(state.chatHistory) { message ->
                    PremiumChatBubble(message.role, message.content)
                }
                if (state.loading) {
                    item { PremiumTypingIndicator() }
                }
            }

            // Input Area
            PremiumInputArea(
                value = state.prompt,
                onValueChange = { viewModel.onPromptChanged(it) },
                onSend = { viewModel.sendMessage() },
                enabled = !state.loading
            )
        }
    }
}

@Composable
fun PremiumAiHeader(activeModel: String, onModelClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text("Oracle", style = PremiumTypography.headlineMedium)
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onModelClick() }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(Color(0xFF30D158), CircleShape)
                )
                Spacer(Modifier.width(8.dp))
                Text(activeModel, style = PremiumTypography.labelMedium, color = TextSecondary)
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = TextMuted, modifier = Modifier.size(16.dp))
            }
        }
        
        Surface(
            modifier = Modifier.size(40.dp),
            shape = CircleShape,
            color = SurfaceDark,
            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
        ) {
            Icon(Icons.Default.Settings, contentDescription = null, tint = TextPrimary, modifier = Modifier.padding(10.dp))
        }
    }
}

@Composable
fun PremiumChatBubble(role: String, content: String) {
    val isUser = role == "user"
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
            modifier = Modifier.fillMaxWidth(0.85f)
        ) {
            if (!isUser) {
                Surface(
                    modifier = Modifier.size(28.dp),
                    shape = CircleShape,
                    color = AccentFlow
                ) {
                    Icon(Icons.Default.Cyclone, contentDescription = null, tint = Color.White, modifier = Modifier.padding(6.dp))
                }
                Spacer(Modifier.width(12.dp))
            }

            Surface(
                color = if (isUser) SurfaceLight else Color.Transparent,
                shape = RoundedCornerShape(18.dp),
            ) {
                Text(
                    text = content,
                    style = PremiumTypography.bodyLarge,
                    modifier = Modifier.padding(horizontal = if (isUser) 16.dp else 0.dp, vertical = if (isUser) 12.dp else 0.dp),
                    color = if (isUser) TextPrimary else TextPrimary
                )
            }
        }
    }
}

@Composable
fun PremiumInputArea(value: String, onValueChange: (String) -> Unit, onSend: () -> Unit, enabled: Boolean) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(16.dp),
        color = SurfaceDark,
        shape = RoundedCornerShape(28.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { /* Attach */ }) {
                Icon(Icons.Default.Add, contentDescription = null, tint = TextMuted)
            }
            
            TextField(
                value = value,
                onValueChange = onValueChange,
                placeholder = { Text("Message Oracle...", color = TextMuted) },
                modifier = Modifier.weight(1f),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                ),
                maxLines = 5
            )

            val sendEnabled = value.isNotBlank() && enabled
            IconButton(
                onClick = onSend,
                enabled = sendEnabled,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (sendEnabled) TextPrimary else SurfaceLight)
            ) {
                Icon(
                    Icons.Default.ArrowUpward,
                    contentDescription = null,
                    tint = if (sendEnabled) DeepBackground else TextMuted,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun PremiumTypingIndicator() {
    Row(
        modifier = Modifier.padding(start = 40.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        repeat(3) { index ->
            val infiniteTransition = rememberInfiniteTransition()
            val alpha by infiniteTransition.animateFloat(
                initialValue = 0.3f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(600, delayMillis = index * 200),
                    repeatMode = RepeatMode.Reverse
                )
            )
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(TextMuted.copy(alpha = alpha), CircleShape)
            )
        }
    }
}
