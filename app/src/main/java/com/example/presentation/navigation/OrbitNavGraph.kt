package com.example.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.presentation.screens.ChatScreen
import com.example.presentation.screens.DashboardScreen
import com.example.presentation.screens.SettingsScreen
import com.example.presentation.screens.TermuxScreen

@Composable
fun OrbitNavGraph(
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = Routes.DASHBOARD
    ) {
        composable(Routes.DASHBOARD) {
            DashboardScreen(
                onNavigateToSession = { sessionId ->
                    navController.navigate(Routes.createChatRoute(sessionId))
                },
                onNavigateToNewSession = {
                    navController.navigate(Routes.CHAT_NEW)
                },
                onNavigateToSettings = {
                    navController.navigate(Routes.SETTINGS)
                },
                onNavigateToTermux = {
                    navController.navigate(Routes.TERMUX)
                }
            )
        }
        
        composable(Routes.CHAT) { backStackEntry ->
            val sessionId = backStackEntry.arguments?.getString("sessionId") ?: ""
            ChatScreen(
                sessionId = sessionId,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Routes.CHAT_NEW) {
            ChatScreen(
                sessionId = null,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Routes.TERMUX) {
            TermuxScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
