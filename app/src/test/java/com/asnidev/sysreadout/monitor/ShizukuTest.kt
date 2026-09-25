package com.asnidev.sysreadout.monitor

import org.junit.Assert.assertEquals
import org.junit.Test

class ShizukuStateTest {

    private fun state(
        enabled: Boolean = true,
        running: Boolean = true,
        installed: Boolean = true,
        preV11: Boolean = false,
        permitted: Boolean = true,
        connected: Boolean = true,
        failing: Boolean = false,
    ) = ShizukuState.of(enabled, running, installed, preV11, permitted, connected, failing)

    @Test
    fun switchedOffBeatsEverything() {
        assertEquals(ShizukuState.OFF, state(enabled = false))
    }

    @Test
    fun notRunningTellsInstalledFromMissing() {
        assertEquals(ShizukuState.NOT_RUNNING, state(running = false))
        assertEquals(ShizukuState.NOT_INSTALLED, state(running = false, installed = false))
    }

    @Test
    fun suiRunsWithoutTheShizukuApp() {
        // Sui (root) answers without the Shizuku app installed: that must still count as running.
        assertEquals(ShizukuState.READY, state(installed = false))
    }

    @Test
    fun stepsInOrder() {
        assertEquals(ShizukuState.UNSUPPORTED, state(preV11 = true, permitted = false))
        assertEquals(ShizukuState.NO_PERMISSION, state(permitted = false, connected = false))
        assertEquals(ShizukuState.CONNECTING, state(connected = false))
        assertEquals(ShizukuState.FAILING, state(connected = false, failing = true))
        assertEquals(ShizukuState.READY, state())
    }
}

class ShizukuSetupTest {

    private val lock = "com.sysreadout.app/com.asnidev.sysreadout.system.LockService"

    @Test
    fun addsToAnEmptyList() {
        assertEquals(lock, ShizukuSetup.withService(null, lock))
        assertEquals(lock, ShizukuSetup.withService("null", lock))
        assertEquals(lock, ShizukuSetup.withService("  \n", lock))
    }

    @Test
    fun keepsOtherServices() {
        val other = "com.google.android.marvin.talkback/com.google.android.marvin.talkback.TalkBackService"
        assertEquals("$other:$lock", ShizukuSetup.withService("$other\n", lock))
    }

    @Test
    fun doesNotAddTwice() {
        val other = "a.b/a.b.C"
        assertEquals("$other:$lock", ShizukuSetup.withService("$other:$lock", lock))
        assertEquals("$other:$lock", ShizukuSetup.withService("$other::$lock:", lock))
    }
}
