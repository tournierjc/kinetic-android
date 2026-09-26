package dev.kinetick.kinetic.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.TimeUnit

class RelativeTimeTest {

    private val now = TimeUnit.HOURS.toMillis(10)

    @Test
    fun bucketsGrowWithAge() {
        assertEquals("now", relativeTime(now - TimeUnit.SECONDS.toMillis(30)))
        assertEquals("5m", relativeTime(now - TimeUnit.MINUTES.toMillis(5)))
        assertEquals("2h", relativeTime(now - TimeUnit.HOURS.toMillis(2)))
        assertEquals("3d", relativeTime(now - TimeUnit.DAYS.toMillis(3)))
        assertEquals("2w", relativeTime(now - TimeUnit.DAYS.toMillis(20)))
        assertEquals("3mo", relativeTime(now - TimeUnit.DAYS.toMillis(100)))
    }

    @Test
    fun missingAndFutureTimestamps() {
        assertEquals(null, relativeTime(null))
        assertEquals(null, relativeTime(0))
        assertEquals("now", relativeTime(now + TimeUnit.HOURS.toMillis(1)))
    }
}
