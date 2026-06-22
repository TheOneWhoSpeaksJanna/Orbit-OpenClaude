package com.omniclaw.ui.navigation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import com.omniclaw.ui.screens.DashboardScreen
import com.omniclaw.ui.screens.ChatScreen
import com.omniclaw.ui.screens.HistoryScreen
import com.omniclaw.ui.screens.ProvidersScreen
import com.omniclaw.ui.screens.SettingsScreen
import com.omniclaw.ui.screens.SkillsScreen
import com.omniclaw.ui.theme.OmniClawAccent
import com.omniclaw.ui.theme.OmniClawObsidianBase
import com.omniclaw.ui.theme.OmniClawObsidianSurface
import com.omniclaw.ui.theme.OmniClawTextSecondary

sealed class ChatViewState {
    object SessionList : ChatViewState()
    data class ActiveChat(val sessionId: String) : ChatViewState()
}

@Composable
fun AppShell() {
    var selectedTab by remember { mutableStateOf(BottomNavTab.HOME) }

    Scaffold(
        containerColor = OmniClawObsidianBase,
        bottomBar = {
            NavigationBar(
                containerColor = OmniClawObsidianSurface,
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
                            selectedIconColor = OmniClawAccent,
                            selectedTextColor = OmniClawAccent,
                            unselectedIconColor = OmniClawTextSecondary,
                            unselectedTextColor = OmniClawTextSecondary,
                            indicatorColor = OmniClawAccent.copy(alpha = 0.15f)
                        )
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(OmniClawObsidianBase)
        ) {
            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = {
                    val direction = if (targetState.ordinal > initialState.ordinal) 1 else -1
                    slideInHorizontally { width -> width * direction } + fadeIn() togetherWith
                            slideOutHorizontally { width -> width * -direction } + fadeOut()
                },
                label = "TabContent"
            ) { tab ->
                when (tab) {
                    BottomNavTab.HOME -> DashboardScreen(
                        onNavigateToSession = { selectedTab = BottomNavTab.CHAT },
                        onNavigateToNewSession = { selectedTab = BottomNavTab.CHAT },
                        onNavigateToTermux = { },
                        onNavigateToSettings = { selectedTab = BottomNavTab.SETTINGS }
                    )
                    BottomNavTab.CHAT -> ChatScreen(
                        sessionId = null,
                        onNavigateBack = { }
                    )
                    BottomNavTab.HISTORY -> HistoryScreen(
                        onOpenSession = { _ -> selectedTab = BottomNavTab.CHAT }
                    )
                    BottomNavTab.SKILLS -> SkillsScreen()
                    BottomNavTab.PROVIDERS -> ProvidersScreen()
                    BottomNavTab.SETTINGS -> SettingsScreen(onNavigateBack = { })
                }
            }
        }
    }
}
