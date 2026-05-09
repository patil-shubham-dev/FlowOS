package com.todo.dailyroutine.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.mohamedrejeb.richeditor.model.RichTextState
import com.mohamedrejeb.richeditor.ui.material3.RichTextEditor
import com.mohamedrejeb.richeditor.ui.material3.RichTextEditorDefaults
import com.todo.dailyroutine.ui.viewmodel.*
import com.todo.dailyroutine.ui.components.*
import com.todo.dailyroutine.ui.theme.*
import com.todo.dailyroutine.ui.utils.shimmerEffect
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JournalScreen(viewModel: JournalViewModel) {
    val entries by viewModel.entries.collectAsState()
    val isEnhancing by viewModel.isEnhancing.collectAsState()
    val streak by viewModel.streak.collectAsState()
    val isVoiceListening by viewModel.isVoiceListening.collectAsState()
    val voiceText by viewModel.voiceText.collectAsState()
    val richTextState = remember { RichTextState() }

    LaunchedEffect(voiceText) {
        if (voiceText.isNotEmpty()) {
            viewModel.enhanceEntry(voiceText) { polished ->
                val currentText = richTextState.toHtml()
                richTextState.setHtml(currentText + "<p>$polished</p>")
            }
        }
    }
    var currentRating by remember { mutableIntStateOf(5) }
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    var showAiMenu by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()
    
    var selectedMood by remember { mutableStateOf<Mood?>(null) }
    val aiPrompt = "The Oracle suggests: What's the biggest bottleneck in your current cycle?"
    var isLoadingEntries by remember { mutableStateOf(true) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.saveEvent.collect {
            snackbarHostState.showSnackbar("Reflection Synthesized")
            richTextState.setText("")
        }
    }

    LaunchedEffect(entries) {
        if (entries.isNotEmpty()) isLoadingEntries = false
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color.Transparent
    ) { padding ->
        DashboardScaffold(
            title = "Reflection",
            modifier = Modifier.padding(padding)
        ) {
        item {
            JournalStreakHeader(streak = streak)
        }

        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                color = SurfaceCard,
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
            ) {
                Column(Modifier.padding(24.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Current Vibe", style = Typography.titleLarge, color = Color.White)
                        Spacer(Modifier.weight(1f))
                        Text("$currentRating/10", style = Typography.headlineLarge, color = AccentPrimary)
                    }
                    
                    Slider(
                        value = currentRating.toFloat(),
                        onValueChange = { currentRating = it.toInt() },
                        valueRange = 1f..10f,
                        steps = 8,
                        colors = SliderDefaults.colors(
                            thumbColor = AccentPrimary,
                            activeTrackColor = AccentPrimary,
                            inactiveTrackColor = TextTertiary
                        )
                    )
                    
                    MoodSelector(
                        selectedMood = selectedMood,
                        onMoodSelected = { selectedMood = it }
                    )
                    
                    AiJournalPrompt(
                        prompt = aiPrompt,
                        onClick = { 
                            val current = richTextState.toHtml()
                            richTextState.setHtml(current + "<p><b>Topic:</b> $aiPrompt</p>")
                        }
                    )
                    
                    Spacer(Modifier.height(16.dp))
                    
                    Surface(
                        modifier = Modifier.fillMaxWidth().heightIn(min = 200.dp),
                        shape = RoundedCornerShape(24.dp),
                        color = BackgroundBase,
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
                    ) {
                        Column {
                            JournalEditorToolbar(
                                state = richTextState,
                                onAiClick = { showAiMenu = true }
                            )

                            RichTextEditor(
                                state = richTextState,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .background(Color.Transparent),
                                colors = RichTextEditorDefaults.richTextEditorColors(
                                    containerColor = Color.Transparent,
                                    cursorColor = AccentPrimary,
                                    textColor = Color.White,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent
                                ),
                                placeholder = { Text("Synthesize your cycles...", color = TextTertiary) }
                            )
                            
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(onClick = { 
                                    if (isVoiceListening) {
                                        viewModel.stopVoiceRecording { refined ->
                                            val currentHtml = richTextState.toHtml()
                                            richTextState.setHtml(currentHtml + "<p>$refined</p>")
                                            scope.launch {
                                                snackbarHostState.showSnackbar("Thought Stream Synced")
                                            }
                                        }
                                    } else {
                                        viewModel.startVoiceRecording()
                                    }
                                }) {
                                    Icon(
                                        imageVector = Icons.Default.Mic, 
                                        contentDescription = null, 
                                        tint = if (isVoiceListening) Color.Red else AccentPrimary
                                    )
                                }
                                IconButton(onClick = { 
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    viewModel.enhanceEntry(richTextState.toHtml()) { enhanced ->
                                        richTextState.setHtml(enhanced)
                                    }
                                }) {
                                    if (isEnhancing) {
                                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = AccentPrimary)
                                    } else {
                                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = AccentPrimary)
                                    }
                                }
                            }
                        }
                    }
                    
                    Spacer(Modifier.height(24.dp))
                    
                    PrimaryGradientButton(
                        text = "Document Reflection",
                        loading = false,
                        onClick = { 
                            viewModel.saveEntry("user", richTextState.toHtml(), currentRating)
                            richTextState.setText("")
                        }
                    )
                }
            }
        }
        
        item {
            Spacer(Modifier.height(24.dp))
            Text("Synced Journey", style = Typography.titleLarge, color = Color.White)
        }

        if (isLoadingEntries) {
            items(5) {
                SkeletonCard(height = 120.dp)
                Spacer(Modifier.height(16.dp))
            }
        } else {
            items(entries) { entry ->
                JournalEntryCard(entry)
            }
        }

        item { Spacer(Modifier.height(120.dp)) }
    }
        }

    if (showAiMenu) {
        ModalBottomSheet(
            onDismissRequest = { showAiMenu = false },
            sheetState = sheetState,
            containerColor = SurfaceCard
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
                    .navigationBarsPadding()
            ) {
                Text("AI Neural Processor", style = Typography.titleLarge, color = Color.White)
                Text("Select an operation to perform on your reflection", style = Typography.labelSmall, color = TextSecondary)
                
                Spacer(Modifier.height(24.dp))
                
                AiMenuOption(
                    title = "Refine & Polish",
                    description = "Professionalize the entry for clarity",
                    icon = Icons.Default.AutoAwesome,
                    onClick = {
                        viewModel.refineEntry(richTextState.toHtml(), JournalViewModel.RefineStyle.PROFESSIONAL) {
                            richTextState.setHtml(it)
                            showAiMenu = false
                        }
                    }
                )
                
                AiMenuOption(
                    title = "Condense",
                    description = "Make it concise and impactful",
                    icon = Icons.Default.ShortText,
                    onClick = {
                        viewModel.refineEntry(richTextState.toHtml(), JournalViewModel.RefineStyle.CONCISE) {
                            richTextState.setHtml(it)
                            showAiMenu = false
                        }
                    }
                )

                AiMenuOption(
                    title = "Extract Action Items",
                    description = "Identify tasks from your thoughts",
                    icon = Icons.Default.Bolt,
                    onClick = {
                        viewModel.extractActionItems(richTextState.toHtml()) { tasks ->
                            // In a real app, we'd show a task confirmation dialog here
                            tasks.forEach { /* Task logic */ }
                            showAiMenu = false
                        }
                    }
                )
                
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@Composable
fun AiMenuOption(
    title: String,
    description: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clickable { onClick() },
        color = SurfaceElevated,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.03f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(40.dp).background(AccentPrimary.copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = AccentPrimary, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(16.dp))
            Column {
                Text(title, style = Typography.titleMedium, color = Color.White)
                Text(description, style = Typography.labelSmall, color = TextSecondary)
            }
        }
    }
}
