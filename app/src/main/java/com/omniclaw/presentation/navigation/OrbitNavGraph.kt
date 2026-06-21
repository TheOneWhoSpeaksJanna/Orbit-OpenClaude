package com.omniclaw.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.omniclaw.presentation.screens.ChatScreen
import com.omniclaw.presentation.dashboard.DashboardScreen
import com.omniclaw.presentation.screens.SettingsScreen
import com.omniclaw.presentation.screens.TermuxScreen

@Composable
fun OmniClawNavGraph(
    navController: NavHostController = rememberNavController(),
    startDestination: String = Routes.DASHBOARD
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Routes.SETUP) {
            com.omniclaw.presentation.screens.SetupWizardScreen(
                onFinishSetup = {
                    navController.navigate(Routes.DASHBOARD) {
                        popUpTo(Routes.SETUP) { inclusive = true }
                    }
                }
            )
        }
        composable(Routes.DASHBOARD) {
            DashboardScreen(
                onNavigateToChat = {
                    navController.navigate(Routes.CHAT_NEW)
                },
                onNavigateToTermux = {
                    navController.navigate(Routes.TERMUX)
                },
                onNavigateToSettings = {
                    navController.navigate(Routes.SETTINGS)
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
