package dev.kinetick.kinetic.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.TimeUnit

class RelativeTimeTest {

    // A base far in the past: `now` is a fixed hour, ages are added to it.
    private val base = TimeUnit.DAYS.toMillis(365)

    @Test
    fun bucketsGrowWithAge() {
        assertEquals("now", relativeTime(base, base + TimeUnit.SECONDS.toMillis(30)))
        assertEquals("5m", relativeTime(base, base + TimeUnit.MINUTES.toMillis(5)))
        assertEquals("2h", relativeTime(base, base + TimeUnit.HOURS.toMillis(2)))
        assertEquals("3d", relativeTime(base, base + TimeUnit.DAYS.toMillis(3)))
        assertEquals("2w", relativeTime(base, base + TimeUnit.DAYS.toMillis(20)))
        assertEquals("3mo", relativeTime(base, base + TimeUnit.DAYS.toMillis(100)))
    }

    @Test
    fun missingAndFutureTimestamps() {
        assertEquals(null, relativeTime(null, base))
        assertEquals(null, relativeTime(0, base))
        assertEquals("now", relativeTime(base + TimeUnit.HOURS.toMillis(1), base))
    }
}
