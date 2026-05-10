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

            // Header (Immersive)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Oracle Intelligence", style = Typography.displaySmall)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(6.dp).background(AccentCyan, CircleShape))
                        Spacer(Modifier.width(6.dp))
                        Text("Protocol: Active Sync", style = Typography.labelMedium, color = TextMuted)
                    }
                }
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .background(AccentBlue.copy(alpha = 0.1f), CircleShape)
                        .border(1.dp, AccentBlue.copy(alpha = 0.2f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Tune, contentDescription = null, tint = AccentBlue, modifier = Modifier.size(20.dp))
                }
            }
            
            Spacer(Modifier.height(24.dp))

            // Chat Area
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                items(
                    items = uiState.chatHistory,
                    key = { it.timestamp }
                ) { message ->
                    ChatMessageItem(
                        text = message.content,
                        isUser = message.role == "user"
                    )
                }
                
                if (uiState.isTyping) {
                    item {
                        TypingIndicator()
                    }
                }
            }

            // Input Bar (Modernized)
            Column(modifier = Modifier.padding(vertical = 16.dp)) {
                // Suggestions
                Row(
                    modifier = Modifier.fillMaxWidth(), 
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.ElectricBolt, null, tint = AccentBlue, modifier = Modifier.size(16.dp))
                    SuggestionChip("Optimize day") { viewModel.sendMessage("Optimize my day") }
                    SuggestionChip("Analyze flow") { viewModel.sendMessage("Analyze my flow score") }
                }
                Spacer(Modifier.height(16.dp))
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 60.dp)
                        .background(ObsidianSurface, RoundedCornerShape(30.dp))
                        .border(1.dp, BorderSubtle, RoundedCornerShape(30.dp))
                        .padding(horizontal = 20.dp, vertical = 10.dp),
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
                                Text("Message Oracle Intelligence...", color = TextMuted, style = Typography.bodyMedium)
                            }
                            innerTextField()
                        }
                    )
                    
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(if (inputText.isNotBlank()) AccentBlue else ObsidianSurfaceElevated)
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
fun TypingIndicator() {
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
    
    Row(
        verticalAlignment = Alignment.CenterVertically, 
        modifier = Modifier.alpha(opacity).padding(start = 12.dp)
    ) {
        Box(
            Modifier
                .size(32.dp)
                .background(AccentBlue.copy(alpha = 0.1f), CircleShape)
                .border(1.dp, AccentBlue.copy(alpha = 0.2f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp), tint = AccentBlue)
        }
        Spacer(Modifier.width(12.dp))
        Text("Oracle is processing...", style = Typography.labelSmall, color = TextMuted)
    }
}

@Composable
fun ChatMessageItem(text: String, isUser: Boolean) {
    val richTextState = rememberRichTextState()
    
    LaunchedEffect(text) {
        richTextState.setMarkdown(text)
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        if (isUser) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp, 20.dp, 4.dp, 20.dp))
                    .background(ObsidianSurfaceElevated)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text(text = text, style = Typography.bodyLarge, color = TextPrimary)
            }
        } else {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AutoAwesome, null, tint = AccentBlue, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("ORACLE", style = Typography.labelSmall.copy(letterSpacing = 2.sp, fontWeight = FontWeight.Bold), color = AccentBlue)
                }
                Spacer(Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp, 20.dp, 20.dp, 20.dp))
                        .background(AccentBlue.copy(alpha = 0.05f))
                        .border(1.dp, AccentBlue.copy(alpha = 0.1f), RoundedCornerShape(4.dp, 20.dp, 20.dp, 20.dp))
                        .padding(horizontal = 16.dp, vertical = 12.dp)
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
