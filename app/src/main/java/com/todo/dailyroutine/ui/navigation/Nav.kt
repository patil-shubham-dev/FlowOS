package com.todo.dailyroutine.ui.navigation

import android.app.Application
import androidx.fragment.app.FragmentActivity
import com.todo.dailyroutine.util.BiometricHelper
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.HistoryEdu
import androidx.compose.material.icons.filled.Add
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.FloatingActionButton
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
    data object Home : Screen("home", "Home")
    data object Flow : Screen("flow", "Flow")
    data object Ai : Screen("ai", "AI")
    data object Journal : Screen("journal", "Journal")
    data object Profile : Screen("profile", "Profile")
    data object AiConfig : Screen("ai_config", "AI Config")
    data object Search : Screen("search", "Search")
    data object DeepFlow : Screen("deep_flow", "Deep Flow")
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

    val omniViewModel: OmniViewModel = viewModel(
        factory = container.omniViewModelFactory
    )

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val showBottomBar = currentDestination?.route != Screen.Onboarding.route && 
                       currentDestination?.route != Screen.AiConfig.route &&
                       currentDestination?.route != Screen.DeepFlow.route &&
                       currentDestination?.route != Screen.Search.route

    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.fillMaxSize(),
            enterTransition = { fadeIn(tween(400)) + scaleIn(initialScale = 0.95f, animationSpec = tween(400)) },
            exitTransition = { fadeOut(tween(400)) + scaleOut(targetScale = 0.95f, animationSpec = tween(400)) }
        ) {
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
                DashboardScreen(
                    homeViewModel = homeViewModel,
                    aiViewModel = aiViewModel,
                    onNavigateToSearch = { navController.navigate(Screen.Search.route) },
                    onNavigateToDeepFlow = { navController.navigate(Screen.DeepFlow.route) }
                )
            }
            composable(Screen.Flow.route) {
                FlowScreen(homeViewModel)
            }
            composable(Screen.Ai.route) {
                AiScreen(aiViewModel)
            }
            composable(Screen.Journal.route) {
                JournalScreen(journalViewModel)
            }
            composable(Screen.Profile.route) {
                ProfileScreen(
                    homeViewModel = homeViewModel,
                    onNavigateToAiConfig = {
                        navController.navigate(Screen.AiConfig.route)
                    }
                )
            }
            composable(Screen.AiConfig.route) {
                AiConfigScreen(
                    viewModel = aiViewModel,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.Search.route) {
                val searchViewModel: SearchViewModel = viewModel(
                    factory = container.searchViewModelFactory
                )
                SearchScreen(
                    viewModel = searchViewModel,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.DeepFlow.route) {
                DeepFlowScreen(
                    onBack = { navController.popBackStack() }
                )
            }
        }

        if (showBottomBar) {
            FloatingPillNav(
                items = navItems,
                currentRoute = currentDestination?.route,
                onNavigate = { route ->
                    if (route == Screen.Journal.route) {
                        val activity = context as? FragmentActivity
                        if (activity != null) {
                            BiometricHelper.showPrompt(
                                activity = activity,
                                onSuccess = {
                                    navController.navigate(route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                onError = { /* User cancelled or failed */ }
                            )
                        } else {
                            navController.navigate(route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    } else {
                        navController.navigate(route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 32.dp)
            )
        }

        // Global Omni-Input FAB
        if (showBottomBar) {
            FloatingActionButton(
                onClick = { omniViewModel.open() },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 24.dp, bottom = 100.dp),
                containerColor = AccentPrimary,
                shape = CircleShape
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = Color.White)
            }
        }

        // Global Overlay
        OmniInputOverlay(omniViewModel)
    }
}
