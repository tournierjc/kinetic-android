@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package dev.kinetick.kinetic.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

@Composable
fun KineticNavGraph(initialSessionId: String?) {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = "sessions") {
        composable("settings") {
            SettingsScreen(onDone = { nav.popBackStack() })
        }
        composable("sessions") {
            SessionListScreen(
                onOpen = { id -> nav.navigate("session/$id") },
                onSettings = { nav.navigate("settings") },
            )
        }
        composable("session/{id}") { entry ->
            val id = entry.arguments?.getString("id") ?: return@composable
            SessionScreen(sessionId = id, onBack = { nav.popBackStack() })
        }
    }
}
