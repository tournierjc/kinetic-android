package dev.kinetick.kinetic.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * Light/dark selection. `System` follows the phone, the other two pin a mode so
 * a bright room or a dark train does not have to win an argument with the OS.
 */
enum class ThemeMode(val id: String, val label: String, val glyph: String) {
    System("system", "Auto", "◐"),
    Dark("dark", "Dark", "☾"),
    Light("light", "Light", "☀");

    fun next(): ThemeMode = entries[(ordinal + 1) % entries.size]

    companion object {
        fun fromId(id: String?): ThemeMode = entries.firstOrNull { it.id == id } ?: System
    }
}

@Composable
fun KineticTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Dark -> true
        ThemeMode.Light -> false
    }
    val context = LocalContext.current
    val scheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        if (dark) darkColorScheme() else lightColorScheme()
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

/** One button that cycles Auto → Dark → Light, showing where it currently sits. */
@Composable
fun ThemeToggleButton(mode: ThemeMode, onCycle: () -> Unit) {
    TextButton(onClick = onCycle) {
        Text("${mode.glyph} ${mode.label}")
    }
}
