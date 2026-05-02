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
import androidx.compose.ui.graphics.graphicsLayer
import com.todo.dailyroutine.data.model.ChatMessage
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

    var showModelPicker by remember { mutableStateOf(false) }

    LaunchedEffect(state.chatHistory.size) {
        if (state.chatHistory.isNotEmpty()) {
            listState.animateScrollToItem(state.chatHistory.size - 1)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundBase)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Premium Header
            AiHeader(
                activeModel = state.selectedModel,
                availableModels = state.availableModels,
                onModelSelected = { viewModel.selectModel(it) }
            )

            // Chat Messages
            Box(modifier = Modifier.weight(1f)) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(state.chatHistory) { message ->
                        ChatBubble(message)
                    }
                    if (state.loading) {
                        item { TypingIndicator() }
                    }
                }
            }

            // Input Area
            InputArea(
                value = state.prompt,
                onValueChange = { viewModel.onPromptChanged(it) },
                onSend = { viewModel.sendMessage() },
                enabled = !state.loading
            )
        }
    }
}

@Composable
fun AiHeader(
    activeModel: String,
    availableModels: List<String>,
    onModelSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding(),
        color = BackgroundBase
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    "Oracle Intelligence",
                    style = Typography.displaySmall.copy(fontSize = 28.sp),
                    color = Color.White,
                    fontWeight = FontWeight.Black
                )
                
                Box {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.05f))
                            .clickable { expanded = true }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(SuccessGreen, CircleShape)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            activeModel.uppercase(),
                            style = Typography.labelSmall,
                            color = Color.White.copy(alpha = 0.6f),
                            letterSpacing = 1.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Icon(
                            Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.4f),
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                        modifier = Modifier.background(SurfaceCard),
                        offset = androidx.compose.ui.unit.DpOffset(0.dp, 8.dp)
                    ) {
                        availableModels.forEach { model ->
                            DropdownMenuItem(
                                text = { Text(model, color = Color.White, style = Typography.bodyMedium) },
                                onClick = {
                                    onModelSelected(model)
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }
            
            Surface(
                modifier = Modifier.size(52.dp),
                shape = CircleShape,
                color = SurfaceCard,
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
            ) {
                Icon(
                    Icons.Default.Cyclone,
                    contentDescription = null,
                    tint = AccentPrimary,
                    modifier = Modifier.padding(14.dp)
                )
            }
        }
    }
}

@Composable
fun ChatBubble(message: ChatMessage) {
    val isUser = message.role == "user"
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(0.92f),
            horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
            verticalAlignment = Alignment.Top
        ) {
            if (!isUser) {
                Surface(
                    modifier = Modifier.size(36.dp),
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.05f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
                ) {
                    Icon(
                        Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = AccentPrimary,
                        modifier = Modifier.padding(10.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
            }

            Surface(
                color = if (isUser) AccentPrimary else SurfaceCard,
                shape = RoundedCornerShape(
                    topStart = 24.dp,
                    topEnd = 24.dp,
                    bottomStart = if (isUser) 24.dp else 4.dp,
                    bottomEnd = if (isUser) 4.dp else 24.dp
                ),
                border = if (isUser) null else androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
            ) {
                Text(
                    text = message.content,
                    style = Typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                    color = Color.White,
                    lineHeight = 26.sp
                )
            }
        }
    }
}

@Composable
fun InputArea(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    enabled: Boolean
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(20.dp),
        color = SurfaceCard,
        shape = RoundedCornerShape(32.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { /* Actions */ },
                modifier = Modifier
                    .size(40.dp)
                    .background(Color.White.copy(alpha = 0.03f), CircleShape)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = Color.White.copy(alpha = 0.4f))
            }
            
            TextField(
                value = value,
                onValueChange = onValueChange,
                placeholder = { 
                    Text(
                        "Transmit objective...", 
                        style = Typography.bodyLarge,
                        color = Color.White.copy(alpha = 0.2f)
                    ) 
                },
                modifier = Modifier.weight(1f),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                maxLines = 5
            )

            val sendEnabled = value.isNotBlank() && enabled
            IconButton(
                onClick = onSend,
                enabled = sendEnabled,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .then(
                        if (sendEnabled) Modifier.background(AccentGradient)
                        else Modifier.background(Color.White.copy(alpha = 0.05f))
                    )
            ) {
                Icon(
                    Icons.Default.ArrowUpward,
                    contentDescription = null,
                    tint = if (sendEnabled) Color.White else Color.White.copy(alpha = 0.2f),
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
fun TypingIndicator() {
    Row(
        modifier = Modifier
            .padding(start = 44.dp, top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        repeat(3) { index ->
            val infiniteTransition = rememberInfiniteTransition()
            val scale by infiniteTransition.animateFloat(
                initialValue = 0.5f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(600, delayMillis = index * 200),
                    repeatMode = RepeatMode.Reverse
                )
            )
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        alpha = scale
                    }
                    .background(AccentPrimary, CircleShape)
            )
        }
    }
}
