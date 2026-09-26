package dev.kinetick.kinetic.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeModeTest {

    @Test
    fun theToggleCyclesThroughEveryMode() {
        var mode = ThemeMode.System
        val seen = mutableListOf<ThemeMode>()
        repeat(ThemeMode.entries.size) {
            seen += mode
            mode = mode.next()
        }
        assertEquals(listOf(ThemeMode.System, ThemeMode.Dark, ThemeMode.Light), seen)
        assertEquals("cycling wraps back to Auto", ThemeMode.System, mode)
    }

    @Test
    fun storedIdsRoundTripAndUnknownOnesFallBack() {
        assertEquals(ThemeMode.Dark, ThemeMode.fromId("dark"))
        assertEquals(ThemeMode.Light, ThemeMode.fromId("light"))
        assertEquals(ThemeMode.System, ThemeMode.fromId("system"))
        assertEquals(ThemeMode.System, ThemeMode.fromId(null))
        assertEquals(ThemeMode.System, ThemeMode.fromId("nonsense"))
    }
}
