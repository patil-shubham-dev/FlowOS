package com.todo.dailyroutine.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

val BackgroundBase = Color(0xFF0B0B0F)
val SurfaceCard = Color(0xFF111118)
val SurfaceElevated = Color(0xFF1A1A24)
val AccentPrimary = Color(0xFF7C5CFF)
val AccentSecondary = Color(0xFF4F8EF7)
val TextPrimary = Color(0xFFFFFFFF)
val TextSecondary = Color(0xFF8E8E9A)
val TextTertiary = Color(0xFF3A3A4A)
val SuccessGreen = Color(0xFF30D158)
val DestructiveRed = Color(0xFFFF453A)

val AccentGradient = Brush.linearGradient(
    colors = listOf(AccentPrimary, AccentSecondary)
)

val BorderGradient = Brush.linearGradient(
    listOf(AccentPrimary.copy(alpha = 0.2f), AccentSecondary.copy(alpha = 0.2f))
)
