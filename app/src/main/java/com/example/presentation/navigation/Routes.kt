package com.example.presentation.navigation

object Routes {
    const val DASHBOARD = "dashboard"
    const val CHAT = "chat/{sessionId}"
    const val CHAT_NEW = "chat_new"
    const val TERMUX = "termux"
    const val SETTINGS = "settings"

    fun createChatRoute(sessionId: String) = "chat/$sessionId"
}
