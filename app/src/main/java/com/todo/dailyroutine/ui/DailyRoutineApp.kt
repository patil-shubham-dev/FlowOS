package com.todo.dailyroutine.ui

import androidx.compose.runtime.Composable
import com.todo.dailyroutine.ui.navigation.FlowOSNavigation
import com.todo.dailyroutine.ui.theme.DailyRoutineTheme

@Composable
fun DailyRoutineAppContent() {
    DailyRoutineTheme {
        FlowOSNavigation()
    }
}
