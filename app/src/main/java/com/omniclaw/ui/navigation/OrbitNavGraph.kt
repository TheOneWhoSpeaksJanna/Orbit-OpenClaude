package com.omniclaw.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.omniclaw.ui.screens.ChatScreen
import com.omniclaw.ui.screens.DashboardScreen
import com.omniclaw.ui.screens.SetupWizardScreen
import com.omniclaw.ui.screens.SettingsScreen
import com.omniclaw.ui.screens.TermuxScreen
import com.omniclaw.ui.theme.MotionTokens

private fun enter() = fadeIn(tween(MotionTokens.DURATION_NORMAL, easing = MotionTokens.EasingDecelerate))
private fun exit() = fadeOut(tween(MotionTokens.DURATION_FAST, easing = MotionTokens.EasingAccelerate))

@Composable
fun OmniClawNavGraph(
    navController: NavHostController = rememberNavController(),
    startDestination: String = Routes.DASHBOARD
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = { enter() },
        exitTransition = { exit() },
        popEnterTransition = { enter() },
        popExitTransition = { exit() }
    ) {
        composable(Routes.SETUP) {
            SetupWizardScreen(
                onFinishSetup = {
                    navController.navigate(Routes.DASHBOARD) {
                        popUpTo(Routes.SETUP) { inclusive = true }
                    }
                }
            )
        }
        composable(Routes.DASHBOARD) {
            DashboardScreen(
                onNavigateToSession = { sessionId ->
                    navController.navigate(Routes.createChatRoute(sessionId))
                },
                onNavigateToNewSession = {
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
