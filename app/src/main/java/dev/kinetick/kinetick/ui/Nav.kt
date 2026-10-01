package dev.kinetick.kinetick.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import java.net.URLEncoder

/** URL-encode a server id so it survives the route string (`srv-lan:8788`). */
internal fun encSeg(s: String): String = URLEncoder.encode(s, "UTF-8")

@Composable
fun KinetickNavGraph(
    initialSessionId: String?,
    initialServerId: String?,
    themeMode: ThemeMode,
    onSetTheme: (ThemeMode) -> Unit,
) {
    val nav = rememberNavController()
    val start = if (initialSessionId.isNullOrBlank()) "sessions"
    else "session/${encSeg(initialServerId ?: "")}/${encSeg(initialSessionId)}"
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
                onOpen = { serverId, id -> nav.navigate("session/${encSeg(serverId)}/${encSeg(id)}") },
                onSettings = { nav.navigate("settings") },
                themeMode = themeMode,
                onSetTheme = onSetTheme,
            )
        }
        composable("session/{serverId}/{id}") { entry ->
            val id = entry.arguments?.getString("id") ?: return@composable
            val serverId = entry.arguments?.getString("serverId") ?: ""
            SessionScreen(
                serverId = serverId,
                sessionId = id,
                onBack = {
                    if (!nav.popBackStack()) nav.navigate("sessions")
                },
                onOpenSession = { otherServerId, other ->
                    if (other != id || otherServerId != serverId) {
                        nav.navigate("session/${encSeg(otherServerId)}/${encSeg(other)}")
                    }
                },
                onSettings = { nav.navigate("settings") },
                themeMode = themeMode,
                onSetTheme = onSetTheme,
            )
        }
    }
}
