package com.todo.dailyroutine.ui

import androidx.compose.runtime.Composable
import com.todo.dailyroutine.ui.navigation.AppNavHost
import com.todo.dailyroutine.ui.theme.FlowOSTheme

@Composable
fun DailyRoutineAppContent() {
    FlowOSTheme {
        AppNavHost()
    }
}
