package com.todo.dailyroutine.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.todo.dailyroutine.ui.components.*
import com.todo.dailyroutine.ui.theme.*
import com.todo.dailyroutine.ui.viewmodel.HomeViewModel
import com.mohamedrejeb.richeditor.model.rememberRichTextState
import com.mohamedrejeb.richeditor.ui.material3.RichText
import kotlinx.coroutines.launch

@Composable
fun AiScreen(viewModel: HomeViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // Scroll to bottom when new message arrives
    LaunchedEffect(uiState.chatHistory.size) {
        if (uiState.chatHistory.isNotEmpty()) {
            listState.animateScrollToItem(uiState.chatHistory.size - 1)
        }
    }

    Scaffold(
        containerColor = ObsidianBackground
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
        ) {
            Spacer(Modifier.height(16.dp))

            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Oracle Intelligence", style = Typography.displayLarge.copy(fontSize = 24.sp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Neural Net", style = Typography.labelMedium, color = TextSecondary)
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = TextSecondary)
                }
            }
            
            Spacer(Modifier.height(16.dp))
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                StatusChip("Memory Active", SuccessGreen)
                Text("Protocol: Active", style = Typography.labelMedium, color = TextSecondary)
            }

            Spacer(Modifier.height(24.dp))

            // Chat Area
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                items(uiState.chatHistory) { message ->
                    ChatMessageItem(
                        text = message.content,
                        isUser = message.role == "user"
                    )
                }
                
                if (uiState.isTyping) {
                    item {
                        val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                        val opacity by infiniteTransition.animateFloat(
                            initialValue = 0.4f,
                            targetValue = 1f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(1000, easing = LinearEasing),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "opacity"
                        )
                        
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.alpha(opacity)) {
                            Box(Modifier.size(32.dp).background(ObsidianSurfaceElevated, CircleShape), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp), tint = AccentBlue)
                            }
                            Spacer(Modifier.width(12.dp))
                            Text("The Oracle is thinking...", style = Typography.labelSmall, color = TextMuted)
                        }
                    }
                }
            }

            // Input Bar
            Column(modifier = Modifier.padding(vertical = 20.dp)) {
                // Suggestions
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SuggestionChip("Optimize day") { viewModel.sendMessage("Optimize my day") }
                    SuggestionChip("Analyze flow") { viewModel.sendMessage("Analyze my flow score") }
                    SuggestionChip("Reset") { /* Clear history if needed */ }
                }
                Spacer(Modifier.height(16.dp))
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .background(ObsidianSurface, RoundedCornerShape(28.dp))
                        .border(1.dp, BorderSubtle, RoundedCornerShape(28.dp))
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BasicTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        modifier = Modifier.weight(1f),
                        textStyle = Typography.bodyMedium.copy(color = TextPrimary),
                        cursorBrush = SolidColor(AccentBlue),
                        decorationBox = { innerTextField ->
                            if (inputText.isEmpty()) {
                                Text("Message Oracle...", color = TextMuted, style = Typography.bodyMedium)
                            }
                            innerTextField()
                        }
                    )
                    
                    IconButton(onClick = { /* Voice toggle */ }) {
                        Icon(Icons.Default.Mic, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(20.dp))
                    }
                    
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(if (inputText.isNotBlank()) AccentBlue else ObsidianSurfaceElevated, CircleShape)
                            .clickable(enabled = inputText.isNotBlank()) {
                                viewModel.sendMessage(inputText)
                                inputText = ""
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.ArrowUpward, contentDescription = null, tint = ObsidianBackground, modifier = Modifier.size(20.dp))
                    }
                }
            }
            
            Spacer(Modifier.height(100.dp))
        }
    }
}

@Composable
fun ChatMessageItem(text: String, isUser: Boolean) {
    val richTextState = rememberRichTextState()
    
    LaunchedEffect(text) {
        richTextState.setMarkdown(text)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!isUser) {
                Box(
                    Modifier
                        .size(28.dp)
                        .background(ObsidianSurfaceElevated, CircleShape)
                        .border(1.dp, AccentBlue.copy(alpha = 0.4f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = AccentBlue
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text("ORACLE", style = Typography.labelSmall.copy(letterSpacing = 2.sp, fontWeight = FontWeight.Bold), color = AccentBlue)
            } else {
                Text("YOU", style = Typography.labelSmall.copy(letterSpacing = 2.sp, fontWeight = FontWeight.Bold), color = TextMuted)
                Spacer(Modifier.width(10.dp))
                Box(
                    Modifier
                        .size(28.dp)
                        .background(AccentBlue.copy(alpha = 0.1f), CircleShape)
                        .border(1.dp, AccentBlue.copy(alpha = 0.2f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = AccentBlue
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        if (isUser) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Surface(
                    color = ObsidianSurfaceElevated,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.widthIn(max = 300.dp)
                ) {
                    Text(
                        text = text,
                        style = Typography.bodyLarge,
                        color = TextPrimary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 38.dp) // Align with text after icon
            ) {
                RichText(
                    state = richTextState,
                    style = Typography.bodyLarge,
                    color = TextPrimary
                )
            }
        }
    }
}

@Composable
fun SuggestionChip(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        style = Typography.labelSmall,
        modifier = Modifier
            .border(1.dp, BorderSubtle, RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        color = TextSecondary
    )
}
