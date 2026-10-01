package dev.kinetick.kinetick.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

@Composable
fun KinetickNavGraph(
    initialSessionId: String?,
    themeMode: ThemeMode,
    onSetTheme: (ThemeMode) -> Unit,
) {
    val nav = rememberNavController()
    val start = if (initialSessionId.isNullOrBlank()) "sessions" else "session/$initialSessionId"
    NavHost(navController = nav, startDestination = start) {
        composable("settings") {
            SettingsScreen(
                themeMode = themeMode,
                onSetTheme = onSetTheme,
                onDone = { nav.popBackStack() },
            )
        }
        composable("sessions") {
            SessionListScreen(
                onOpen = { id -> nav.navigate("session/$id") },
                onSettings = { nav.navigate("settings") },
                themeMode = themeMode,
                onSetTheme = onSetTheme,
            )
        }
        composable("session/{id}") { entry ->
            val id = entry.arguments?.getString("id") ?: return@composable
            SessionScreen(
                sessionId = id,
                onBack = {
                    if (!nav.popBackStack()) nav.navigate("sessions")
                },
                onOpenSession = { other ->
                    if (other != id) nav.navigate("session/$other")
                },
                onSettings = { nav.navigate("settings") },
                themeMode = themeMode,
                onSetTheme = onSetTheme,
            )
        }
    }
}
