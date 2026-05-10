package com.todo.dailyroutine.ui.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import com.todo.dailyroutine.DailyRoutineApp
import com.todo.dailyroutine.ui.viewmodel.HomeViewModelFactory
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.todo.dailyroutine.ui.components.FloatingPillNav
import com.todo.dailyroutine.ui.components.NavItem
import com.todo.dailyroutine.ui.screens.*
import com.todo.dailyroutine.ui.viewmodel.HomeViewModel

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    data object State : Screen("state", "State", Icons.Default.Dashboard)
    data object Flow : Screen("flow", "Flow", Icons.Default.Reorder)
    data object Oracle : Screen("oracle", "Oracle", Icons.Default.AutoAwesome)
    data object Journal : Screen("journal", "Journal", Icons.Default.EditNote)
    data object Settings : Screen("settings", "Settings", Icons.Default.Settings)
}

val navItems = listOf(
    NavItem(Screen.State.route, Screen.State.icon, Screen.State.label),
    NavItem(Screen.Flow.route, Screen.Flow.icon, Screen.Flow.label),
    NavItem(Screen.Oracle.route, Screen.Oracle.icon, Screen.Oracle.label),
    NavItem(Screen.Journal.route, Screen.Journal.icon, Screen.Journal.label),
    NavItem(Screen.Settings.route, Screen.Settings.icon, Screen.Settings.label)
)

@Composable
fun FlowOSNavigation(
    navController: NavHostController = rememberNavController()
) {
    val context = LocalContext.current
    val app = context.applicationContext as DailyRoutineApp
    val container = app.container

    val homeViewModel: HomeViewModel = viewModel(
        factory = HomeViewModelFactory(
            container.taskRepository,
            container.habitRepository,
            container.aiRepository,
            container.journalRepository,
            container.chatRepository,
            container.sessionManager,
            container.aiScheduler,
            container.aiContextManager,
            container.toolExecutionManager,
            container.ttsManager
        )
    )
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // AI-Driven Navigation
    androidx.compose.runtime.LaunchedEffect(Unit) {
        container.navigationManager.navigationEvents.collect { route ->
            if (currentRoute != route) {
                navController.navigate(route) {
                    popUpTo(navController.graph.startDestinationId) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = Screen.State.route,
            modifier = Modifier.fillMaxSize(),
            enterTransition = { 
                slideIntoContainer(
                    AnimatedContentTransitionScope.SlideDirection.Start,
                    animationSpec = tween(500, easing = EaseInOutQuart)
                ) + fadeIn(tween(500))
            },
            exitTransition = { 
                slideOutOfContainer(
                    AnimatedContentTransitionScope.SlideDirection.Start,
                    animationSpec = tween(500, easing = EaseInOutQuart)
                ) + fadeOut(tween(500))
            },
            popEnterTransition = {
                slideIntoContainer(
                    AnimatedContentTransitionScope.SlideDirection.End,
                    animationSpec = tween(500, easing = EaseInOutQuart)
                ) + fadeIn(tween(500))
            },
            popExitTransition = {
                slideOutOfContainer(
                    AnimatedContentTransitionScope.SlideDirection.End,
                    animationSpec = tween(500, easing = EaseInOutQuart)
                ) + fadeOut(tween(500))
            }
        ) {
            composable(Screen.State.route) { DashboardScreen(homeViewModel, navController) }
            composable(Screen.Flow.route) { FlowScreen(homeViewModel) }
            composable(Screen.Oracle.route) { AiScreen(homeViewModel) }
            composable(Screen.Journal.route) { JournalScreen(homeViewModel) }
            composable(Screen.Settings.route) { SettingsScreen(homeViewModel) }
        }

        FloatingPillNav(
            items = navItems,
            currentRoute = currentRoute,
            onNavigate = { route ->
                if (currentRoute != route) {
                    navController.navigate(route) {
                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            },
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}
