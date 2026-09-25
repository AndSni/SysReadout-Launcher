package com.asnidev.sysreadout.system

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CrashGuardTest {

    private val now = 10_000_000L

    @Test
    fun oneEarlyCrashIsNotALoop() {
        assertFalse(CrashGuard.isLoop(listOf(now), now))
    }

    @Test
    fun twoEarlyCrashesCloseTogetherAreALoop() {
        assertTrue(CrashGuard.isLoop(listOf(now - 20_000, now), now))
    }

    @Test
    fun crashesFurtherApartThanTheWindowAreNot() {
        assertFalse(CrashGuard.isLoop(listOf(now - CrashGuard.WINDOW_MS - 1, now), now))
    }

    @Test
    fun timesFromTheFutureDontCount() {
        // After the clock was set back, old entries lie in the future: ignore them.
        assertFalse(CrashGuard.isLoop(listOf(now + 60_000, now), now))
    }
}
