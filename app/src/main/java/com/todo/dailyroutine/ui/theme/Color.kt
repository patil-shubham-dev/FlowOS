package com.todo.dailyroutine.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Obsidian Deep Theme
val BackgroundBase = Color(0xFF010101)
val SurfaceCard = Color(0xFF0A0A0A)
val SurfaceElevated = Color(0xFF141414)

// Electric Blue Branding
val AccentPrimary = Color(0xFF007AFF) // Electric Blue from the logo dot
val AccentSecondary = Color(0xFFFFFFFF) // Pure White from logo lines

val SuccessGreen = Color(0xFF34C759)
val DestructiveRed = Color(0xFFFF3B30)

val TextPrimary = Color(0xFFFFFFFF)
val TextSecondary = Color(0xFF7B7B7B)
val TextTertiary = Color(0xFF333333)

val AccentGradient = Brush.linearGradient(
    colors = listOf(AccentPrimary, Color(0xFF5856D6)) // Blue to Indigo
)

val BorderGradient = Brush.linearGradient(
    listOf(AccentPrimary.copy(alpha = 0.3f), Color.White.copy(alpha = 0.1f))
)
