package com.asnidev.sysreadout.data

import org.junit.Assert.assertEquals
import org.junit.Test

class LauncherPrefsTest {

    @Test
    fun noSavedBannerIsTheDefault() {
        assertEquals(LauncherPrefs.DEFAULT_BANNER, LauncherPrefs.bannerFrom(null))
    }

    @Test
    fun theUneditedPreviousDefaultFollowsTheNewOne() {
        assertEquals(LauncherPrefs.DEFAULT_BANNER, LauncherPrefs.bannerFrom(LauncherPrefs.PREVIOUS_DEFAULT_BANNER))
    }

    @Test
    fun anEditedBannerStaysAsItIs() {
        val mine = "MY PHONE\n{date}"
        assertEquals(mine, LauncherPrefs.bannerFrom(mine))
        assertEquals("", LauncherPrefs.bannerFrom(""))
    }
}
