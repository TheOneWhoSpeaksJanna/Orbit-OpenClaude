package com.omniclaw.ui.navigation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.activity.compose.BackHandler
import android.app.Activity
import androidx.compose.ui.platform.LocalContext
import com.omniclaw.ui.screens.DashboardScreen
import com.omniclaw.ui.screens.ChatScreen
import com.omniclaw.ui.screens.HistoryScreen
import com.omniclaw.ui.screens.ProvidersScreen
import com.omniclaw.ui.screens.SettingsScreen
import com.omniclaw.ui.screens.SkillsScreen
import com.omniclaw.ui.screens.TermuxScreen
import com.omniclaw.ui.theme.MotionTokens

sealed class ChatViewState {
    object SessionList : ChatViewState()
    data class ActiveChat(val sessionId: String) : ChatViewState()
}

private const val TAB_INDICATOR_ALPHA = 0.15f

/**
 * Tab transition: fade-through (Material 3's recommended pattern for unrelated bottom-nav
 * destinations) instead of a full-width horizontal slide. Two reasons:
 * 1. Sliding two complete, heavily-nested screen trees across the full screen width meant both
 *    were rendering simultaneously mid-transition - the actual source of the "laggy" tab
 *    switches, not the animation curve itself.
 * 2. A slide implies spatial/hierarchical relationship between tabs (like moving between pages
 *    of a book). Bottom-nav tabs are peers, not a sequence - fade-through is the semantically
 *    correct motion for that relationship per Material 3 guidance.
 */
private fun tabTransitionSpec() = (
    fadeIn(
        animationSpec = tween(
            durationMillis = MotionTokens.DURATION_NORMAL,
            delayMillis = MotionTokens.DURATION_FAST / 2,
            easing = MotionTokens.EasingDecelerate
        )
    )
) togetherWith (
    fadeOut(
        animationSpec = tween(
            durationMillis = MotionTokens.DURATION_FAST,
            easing = MotionTokens.EasingAccelerate
        )
    )
)

@Composable
fun AppShell() {
    var selectedTab by remember { mutableStateOf(BottomNavTab.HOME) }
    var targetSessionId by remember { mutableStateOf<String?>(null) }
    var showTermux by remember { mutableStateOf(false) }
    val context = LocalContext.current

    BackHandler(enabled = true) {
        if (showTermux) {
            showTermux = false
        } else if (selectedTab == BottomNavTab.HOME) {
            (context as? Activity)?.finish()
        } else {
            selectedTab = BottomNavTab.HOME
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (!showTermux) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp
                ) {
                    BottomNavTab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = selectedTab == tab,
                            onClick = { selectedTab = tab },
                            icon = {
                                Icon(
                                    imageVector = tab.icon,
                                    contentDescription = tab.label
                                )
                            },
                            label = { Text(tab.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.secondary,
                                selectedTextColor = MaterialTheme.colorScheme.secondary,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                indicatorColor = MaterialTheme.colorScheme.secondary.copy(alpha = TAB_INDICATOR_ALPHA)
                            )
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (showTermux) {
                TermuxScreen(onNavigateBack = { showTermux = false })
            } else {
                Box(modifier = Modifier.padding(paddingValues)) {
                    AnimatedContent(
                        targetState = selectedTab,
                        transitionSpec = { tabTransitionSpec() },
                        label = "TabContent"
                    ) { tab ->
                        when (tab) {
                            BottomNavTab.HOME -> DashboardScreen(
                                onNavigateToSession = { id ->
                                    targetSessionId = id
                                    selectedTab = BottomNavTab.CHAT
                                },
                                onNavigateToNewSession = {
                                    targetSessionId = null
                                    selectedTab = BottomNavTab.CHAT
                                },
                                onNavigateToTermux = { showTermux = true },
                                onNavigateToSettings = { selectedTab = BottomNavTab.SETTINGS }
                            )
                            BottomNavTab.CHAT -> ChatScreen(
                                sessionId = targetSessionId,
                                onNavigateBack = {
                                    targetSessionId = null
                                    selectedTab = BottomNavTab.HOME
                                }
                            )
                            BottomNavTab.HISTORY -> HistoryScreen(
                                onOpenSession = { id ->
                                    targetSessionId = id
                                    selectedTab = BottomNavTab.CHAT
                                }
                            )
                            BottomNavTab.SKILLS -> SkillsScreen()
                            BottomNavTab.PROVIDERS -> ProvidersScreen()
                            BottomNavTab.SETTINGS -> SettingsScreen(onNavigateBack = { selectedTab = BottomNavTab.HOME })
                        }
                    }
                }
            }
        }
    }
}
