package com.asnidev.sysreadout.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MonitorPrefsTest {

    @Test
    fun newInstallsStartWithoutShizuku() {
        assertFalse(MonitorPrefs.fromJson(null).shizuku)
        assertFalse(MonitorPrefs().shizuku)
    }

    @Test
    fun settingsFromBeforeTheSwitchKeepUsingShizuku() {
        assertTrue(MonitorPrefs.fromJson("""{"procs":true,"conns":false}""").shizuku)
    }

    @Test
    fun roundTrip() {
        val m = MonitorPrefs(shizuku = true, shizukuTip = true, procSort = ProcSort.MEM, connRows = 9, dnsVpn = true)
        assertEquals(m, MonitorPrefs.fromJson(m.toJson()))
    }

    @Test
    fun garbageFallsBackToDefaults() {
        assertEquals(MonitorPrefs(), MonitorPrefs.fromJson("not json"))
    }
}
