package com.todo.dailyroutine.ui.navigation

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.HistoryEdu
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.todo.dailyroutine.ui.screens.*
import com.todo.dailyroutine.ui.viewmodel.*
import com.todo.dailyroutine.ui.components.*
import com.todo.dailyroutine.ui.theme.*

sealed class Screen(val route: String, val label: String) {
    data object Auth : Screen("auth", "Auth")
    data object Home : Screen("home", "Home")
    data object Flow : Screen("flow", "Flow")
    data object Ai : Screen("ai", "AI")
    data object Journal : Screen("journal", "Journal")
    data object Profile : Screen("profile", "Profile")
    data object Onboarding : Screen("onboarding", "Onboarding")
}

private val navItems = listOf(
    NavItem(Screen.Home.route, Icons.Default.Home, "State"),
    NavItem(Screen.Flow.route, Icons.Default.Timeline, "Flow"),
    NavItem(Screen.Ai.route, Icons.Default.AutoAwesome, "Oracle"),
    NavItem(Screen.Journal.route, Icons.Default.HistoryEdu, "Sync"),
    NavItem(Screen.Profile.route, Icons.Default.Person, "System")
)

@Composable
fun AppNavHost() {
    val navController = rememberNavController()
    val context = LocalContext.current
    val app = context.applicationContext as com.todo.dailyroutine.DailyRoutineApp
    val container = app.container

    val authViewModel: AuthViewModel = viewModel(
        factory = AuthViewModelFactory(container.authRepository)
    )
    val homeViewModel: HomeViewModel = viewModel(
        factory = HomeViewModelFactory(
            container.taskRepository,
            container.habitRepository,
            container.aiRepository,
            container.aiScheduler
        )
    )
    val aiViewModel: AiViewModel = viewModel(
        factory = AiViewModelFactory(
            container.aiRepository,
            container.aiConfigRepository,
            container.notificationScheduler,
            container.aiContextManager,
            container.aiToolController
        )
    )

    val journalViewModel: JournalViewModel = viewModel(
        factory = container.journalViewModelFactory
    )

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val showBottomBar = currentDestination?.route != Screen.Auth.route && currentDestination?.route != Screen.Onboarding.route

    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = Screen.Auth.route,
            modifier = Modifier.fillMaxSize(),
            enterTransition = { fadeIn(tween(400)) + scaleIn(initialScale = 0.95f, animationSpec = tween(400)) },
            exitTransition = { fadeOut(tween(400)) + scaleOut(targetScale = 0.95f, animationSpec = tween(400)) }
        ) {
            composable(Screen.Auth.route) {
                AuthScreen(
                    viewModel = authViewModel,
                    onLoggedIn = {
                        navController.navigate(Screen.Onboarding.route) {
                            popUpTo(Screen.Auth.route) { inclusive = true }
                        }
                    }
                )
            }
            composable(Screen.Onboarding.route) {
                OracleInterviewScreen(
                    onFinished = {
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.Onboarding.route) { inclusive = true }
                        }
                    }
                )
            }
            composable(Screen.Home.route) {
                DashboardScreen(homeViewModel, aiViewModel)
            }
            composable(Screen.Flow.route) {
                FlowScreen(homeViewModel)
            }
            composable(Screen.Ai.route) {
                AiScreen(aiViewModel)
            }
            composable(Screen.Journal.route) {
                JournalScreen(journalViewModel, authViewModel)
            }
            composable(Screen.Profile.route) {
                ProfileScreen(
                    authViewModel = authViewModel,
                    onSignedOut = {
                        navController.navigate(Screen.Auth.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                )
            }
        }

        if (showBottomBar) {
            FloatingPillNav(
                items = navItems,
                currentRoute = currentDestination?.route,
                onNavigate = { route ->
                    navController.navigate(route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 32.dp)
            )
        }
    }
}
