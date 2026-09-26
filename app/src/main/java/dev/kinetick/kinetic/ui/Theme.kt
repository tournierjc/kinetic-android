package dev.kinetick.kinetic.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import dev.kinetick.kinetic.R

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

/** Bundled typefaces: Inter for the UI, JetBrains Mono for anything code-shaped. */
object Fonts {
    val Ui: FontFamily = FontFamily(
        Font(R.font.inter_regular, FontWeight.Normal, FontStyle.Normal),
        Font(R.font.inter_italic, FontWeight.Normal, FontStyle.Italic),
        Font(R.font.inter_medium, FontWeight.Medium, FontStyle.Normal),
        Font(R.font.inter_semibold, FontWeight.SemiBold, FontStyle.Normal),
    )

    val Code: FontFamily = FontFamily(
        Font(R.font.jetbrains_mono_regular, FontWeight.Normal, FontStyle.Normal),
        Font(R.font.jetbrains_mono_italic, FontWeight.Normal, FontStyle.Italic),
        Font(R.font.jetbrains_mono_bold, FontWeight.Bold, FontStyle.Normal),
    )
}

// Deliberately not dynamic colour: the palette should not move with a wallpaper.
// A cyan-leaning azure in both modes, GitHub-flavoured.
private val LightColors = lightColorScheme(
    primary = Color(0xFF0969DA),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD6E7FF),
    onPrimaryContainer = Color(0xFF06305F),
    inversePrimary = Color(0xFF8CC2FF),
    secondary = Color(0xFF1F6FEB),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE3EDFB),
    onSecondaryContainer = Color(0xFF0B2E5C),
    tertiary = Color(0xFF2F81F7),
    background = Color(0xFFF6F8FA),
    onBackground = Color(0xFF1F2328),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1F2328),
    surfaceVariant = Color(0xFFEBF0F5),
    onSurfaceVariant = Color(0xFF59636E),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF9FBFC),
    surfaceContainer = Color(0xFFF3F6F9),
    surfaceContainerHigh = Color(0xFFEDF2F6),
    surfaceContainerHighest = Color(0xFFE7EDF3),
    inverseSurface = Color(0xFF1F2328),
    inverseOnSurface = Color(0xFFF6F8FA),
    outline = Color(0xFFBDC6D1),
    outlineVariant = Color(0xFFD8DFE7),
    error = Color(0xFFCF222E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFE1E1),
    onErrorContainer = Color(0xFF6E1216),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF58A6FF),
    onPrimary = Color(0xFF04182E),
    primaryContainer = Color(0xFF12385C),
    onPrimaryContainer = Color(0xFFCFE6FF),
    inversePrimary = Color(0xFF0969DA),
    secondary = Color(0xFF79C0FF),
    onSecondary = Color(0xFF06121F),
    secondaryContainer = Color(0xFF1B2A3A),
    onSecondaryContainer = Color(0xFFD7E9FF),
    tertiary = Color(0xFFA5D6FF),
    background = Color(0xFF0D1117),
    onBackground = Color(0xFFE6EDF3),
    surface = Color(0xFF0D1117),
    onSurface = Color(0xFFE6EDF3),
    surfaceVariant = Color(0xFF1B222C),
    onSurfaceVariant = Color(0xFF9DA7B3),
    surfaceContainerLowest = Color(0xFF090C10),
    surfaceContainerLow = Color(0xFF11161C),
    surfaceContainer = Color(0xFF161C24),
    surfaceContainerHigh = Color(0xFF1B222C),
    surfaceContainerHighest = Color(0xFF222A35),
    inverseSurface = Color(0xFFE6EDF3),
    inverseOnSurface = Color(0xFF0D1117),
    outline = Color(0xFF3D4754),
    outlineVariant = Color(0xFF2A323C),
    error = Color(0xFFFF7B72),
    onError = Color(0xFF2B0A08),
    errorContainer = Color(0xFF4B1713),
    onErrorContainer = Color(0xFFFFD7D3),
)

private val InterTypography: Typography = Typography().let { base ->
    Typography(
        displayLarge = base.displayLarge.copy(fontFamily = Fonts.Ui),
        displayMedium = base.displayMedium.copy(fontFamily = Fonts.Ui),
        displaySmall = base.displaySmall.copy(fontFamily = Fonts.Ui),
        headlineLarge = base.headlineLarge.copy(fontFamily = Fonts.Ui),
        headlineMedium = base.headlineMedium.copy(fontFamily = Fonts.Ui),
        headlineSmall = base.headlineSmall.copy(fontFamily = Fonts.Ui),
        titleLarge = base.titleLarge.copy(fontFamily = Fonts.Ui),
        titleMedium = base.titleMedium.copy(fontFamily = Fonts.Ui),
        titleSmall = base.titleSmall.copy(fontFamily = Fonts.Ui),
        bodyLarge = base.bodyLarge.copy(fontFamily = Fonts.Ui),
        bodyMedium = base.bodyMedium.copy(fontFamily = Fonts.Ui),
        bodySmall = base.bodySmall.copy(fontFamily = Fonts.Ui),
        labelLarge = base.labelLarge.copy(fontFamily = Fonts.Ui),
        labelMedium = base.labelMedium.copy(fontFamily = Fonts.Ui),
        labelSmall = base.labelSmall.copy(fontFamily = Fonts.Ui),
    )
}

@Composable
fun KineticTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Dark -> true
        ThemeMode.Light -> false
    }
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography = InterTypography,
        content = content,
    )
}

/** One button that cycles Auto → Dark → Light, showing where it currently sits. */
@Composable
fun ThemeToggleButton(mode: ThemeMode, onCycle: () -> Unit) {
    TextButton(onClick = onCycle) {
        Text("${mode.glyph} ${mode.label}")
    }
}
