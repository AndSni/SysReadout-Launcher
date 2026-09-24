package com.asnidev.sysreadout.data

import com.asnidev.sysreadout.log.ProbeCatalog

/**
 * One launchable activity in one user profile. The profile is stored as its
 * serial number because UserHandle itself isn't stable across reboots.
 */
data class AppKey(val pkg: String, val cls: String, val user: Long) {
    fun encode(): String = "$pkg/$cls#$user"

    companion object {
        fun decode(s: String): AppKey? {
            val slash = s.indexOf('/')
            val hash = s.lastIndexOf('#')
            if (slash <= 0 || hash <= slash) return null
            val user = s.substring(hash + 1).toLongOrNull() ?: return null
            return AppKey(s.substring(0, slash), s.substring(slash + 1, hash), user)
        }
    }
}

enum class EntryStyle { FROSTED, HIGHLIGHT, INVERTED, BARE }
enum class HAlign { START, CENTER, END }
enum class VAlign { TOP, CENTER, BOTTOM }

/**
 * CLASSIC: rows, tables and stream in fixed places. FEED: one list without
 * headers, new rows push older ones off the screen, rows on screen update in place.
 */
enum class LogLayout { CLASSIC, FEED }

/** What SysReadout does with the lock-screen wallpaper. OFF leaves it alone. */
enum class LockMode { OFF, IMAGE, SNAPSHOT }

data class LauncherPrefs(
    val pinned: List<AppKey> = emptyList(),
    val hidden: Set<AppKey> = emptySet(),
    val renames: Map<AppKey, String> = emptyMap(),
    val showClock: Boolean = true,
    val showDate: Boolean = true,
    val showLog: Boolean = true,
    val entryStyle: EntryStyle = EntryStyle.FROSTED,
    val hAlign: HAlign = HAlign.START,
    val vAlign: VAlign = VAlign.CENTER,
    val swipeLeft: AppKey? = null,
    val swipeRight: AppKey? = null,
    val doubleTapLock: Boolean = false,
    val autoKeyboard: Boolean = true,
    val autoLaunch: Boolean = true,
    /** Pinned log rows, in display order (ids from ProbeCatalog). */
    val logRows: List<String> = ProbeCatalog.defaultIds,
    val logIntervalSec: Int = 2,
    val showStream: Boolean = true,
    val streamTimestamps: Boolean = false,
    /** Header text above the log; {variables} are filled in live (see BannerText). */
    val showBanner: Boolean = true,
    val banner: String = DEFAULT_BANNER,
    val bannerAlign: HAlign = HAlign.CENTER,
    val lockMode: LockMode = LockMode.OFF,
    val logLayout: LogLayout = LogLayout.CLASSIC,
    /** Feed layout: new rows enter at the top (true) or at the bottom. */
    val feedNewestAtTop: Boolean = true,
) {
    companion object {
        val INTERVALS = listOf(1, 2, 5, 10)
        const val DEFAULT_BANNER =
            "SYSREADOUT INDUSTRIES UNIFIED OPERATING SYSTEM\nCOPYRIGHT 2026-{year} SYSREADOUT INC.\n- {device} -"
    }
}
