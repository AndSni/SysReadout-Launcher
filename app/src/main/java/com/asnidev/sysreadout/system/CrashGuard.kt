package com.asnidev.sysreadout.system

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Process
import android.os.SystemClock
import android.util.Log
import com.asnidev.sysreadout.BuildConfig
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Keeps a crashing launcher from locking the user out of their phone. Android
 * restarts a home app the moment it dies, so a crash on start repeats forever.
 * Every crash is written to [REPORT] (readable and shareable in settings ›
 * about); [LOOP_CRASHES] crashes soon after starting, within [WINDOW_MS] of
 * each other, switch on safe mode: the next start is a plain launcher with the
 * default look and no monitor, Shizuku or DNS monitor, until the user resumes.
 */
object CrashGuard {

    const val REPORT = "last_crash.txt"

    /** A crash this soon after the process started counts towards a loop. */
    const val EARLY_MS = 60_000L

    /** How close together the early crashes have to be. */
    const val WINDOW_MS = 5 * 60_000L

    const val LOOP_CRASHES = 2

    private const val PREFS = "crash_guard"
    private const val KEY_EARLY = "early_crashes"
    private const val KEY_SAFE = "safe_mode"
    private const val MAX_REPORT = 32 * 1024

    fun install(app: Application) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            try {
                record(app, thread, e)
            } catch (_: Throwable) {
                // Never let the guard itself get in the way of the crash being handled.
            }
            previous?.uncaughtException(thread, e)
        }
    }

    fun isSafeMode(context: Context): Boolean = prefs(context).getBoolean(KEY_SAFE, false)

    fun leaveSafeMode(context: Context) {
        prefs(context).edit().putBoolean(KEY_SAFE, false).remove(KEY_EARLY).apply()
    }

    /** The last crash report, or null if SysReadout hasn't crashed (or it was cleared). */
    fun report(context: Context): String? =
        runCatching { File(context.filesDir, REPORT).takeIf { it.exists() }?.readText() }.getOrNull()

    fun clearReport(context: Context) {
        File(context.filesDir, REPORT).delete()
    }

    /** True when at least [LOOP_CRASHES] of [earlyCrashes] (epoch ms) fall within [WINDOW_MS] before [now]. */
    fun isLoop(earlyCrashes: List<Long>, now: Long): Boolean =
        earlyCrashes.count { now - it in 0..WINDOW_MS } >= LOOP_CRASHES

    @SuppressLint("ApplySharedPref") // the process is about to die: apply() might never write
    private fun record(context: Context, thread: Thread, e: Throwable) {
        val now = System.currentTimeMillis()
        val sinceStart = SystemClock.elapsedRealtime() - Process.getStartElapsedRealtime()
        val p = prefs(context)
        val early = p.getString(KEY_EARLY, "").orEmpty().split(',').mapNotNull { it.toLongOrNull() }
            .filter { now - it in 0..WINDOW_MS }
            .let { if (sinceStart <= EARLY_MS) it + now else it }
        val loop = isLoop(early, now)
        p.edit().putString(KEY_EARLY, early.joinToString(",")).apply { if (loop) putBoolean(KEY_SAFE, true) }.commit()
        File(context.filesDir, REPORT).writeText(describe(thread, e, sinceStart, loop))
        Log.e("CrashGuard", "crash recorded" + if (loop) "; next start in safe mode" else "")
    }

    private fun describe(thread: Thread, e: Throwable, sinceStart: Long, loop: Boolean): String {
        val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.US))
        return buildString {
            append("SysReadout ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            append(" · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) · ${Build.MANUFACTURER} ${Build.MODEL}\n")
            append("$stamp · thread ${thread.name} · ${sinceStart / 1000}s after start")
            if (loop) append(" · safe mode switched on")
            append("\n\n")
            append(Log.getStackTraceString(e).ifEmpty { e.toString() })
        }.take(MAX_REPORT)
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
