package com.asnidev.sysreadout.data

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONObject

private val Context.dataStore by preferencesDataStore("launcher")

class SettingsStore(private val context: Context) {

    private object K {
        val pinned = stringPreferencesKey("pinned")
        val hidden = stringSetPreferencesKey("hidden")
        val renames = stringSetPreferencesKey("renames")
        val showClock = booleanPreferencesKey("show_clock")
        val showDate = booleanPreferencesKey("show_date")
        val showLog = booleanPreferencesKey("show_log")
        val entryStyle = stringPreferencesKey("entry_style")
        val hAlign = stringPreferencesKey("h_align")
        val vAlign = stringPreferencesKey("v_align")
        val swipeLeft = stringPreferencesKey("swipe_left")
        val swipeRight = stringPreferencesKey("swipe_right")
        val doubleTapLock = booleanPreferencesKey("double_tap_lock")
        val autoKeyboard = booleanPreferencesKey("auto_keyboard")
        val autoLaunch = booleanPreferencesKey("auto_launch")
        val logRows = stringPreferencesKey("log_rows")
        val logInterval = intPreferencesKey("log_interval")
        val showStream = booleanPreferencesKey("show_stream")
        val streamTimestamps = booleanPreferencesKey("stream_timestamps")
        val theme = stringPreferencesKey("theme")
        val presets = stringPreferencesKey("presets")
        val monitor = stringPreferencesKey("monitor")
        val showBanner = booleanPreferencesKey("show_banner")
        val banner = stringPreferencesKey("banner")
        val bannerAlign = stringPreferencesKey("banner_align")
        val lockMode = stringPreferencesKey("lock_mode")
        val logLayout = stringPreferencesKey("log_layout")
        val feedTop = booleanPreferencesKey("feed_newest_at_top")
    }

    val prefs: Flow<LauncherPrefs> = context.dataStore.data.map(::read)

    val theme: Flow<Theme> = context.dataStore.data.map(::readTheme)

    val presets: Flow<List<Preset>> = context.dataStore.data.map { presetsFromJson(it[K.presets]) }

    val monitor: Flow<MonitorPrefs> = context.dataStore.data.map { MonitorPrefs.fromJson(it[K.monitor]) }

    suspend fun update(transform: (LauncherPrefs) -> LauncherPrefs) {
        context.dataStore.edit { p -> write(p, transform(read(p))) }
    }

    suspend fun updateTheme(transform: (Theme) -> Theme) {
        context.dataStore.edit { p -> p[K.theme] = transform(readTheme(p)).toJson().toString() }
    }

    suspend fun updatePresets(transform: (List<Preset>) -> List<Preset>) {
        context.dataStore.edit { p -> p[K.presets] = transform(presetsFromJson(p[K.presets])).toJson() }
    }

    suspend fun updateMonitor(transform: (MonitorPrefs) -> MonitorPrefs) {
        context.dataStore.edit { p -> p[K.monitor] = transform(MonitorPrefs.fromJson(p[K.monitor])).toJson() }
    }

    private fun readTheme(p: Preferences): Theme =
        p[K.theme]?.let { runCatching { Theme.fromJson(JSONObject(it)) }.getOrNull() } ?: Presets.default.theme

    private fun read(p: Preferences): LauncherPrefs {
        val d = LauncherPrefs()
        return LauncherPrefs(
            pinned = p[K.pinned]?.lines()?.mapNotNull(AppKey::decode) ?: d.pinned,
            hidden = p[K.hidden]?.mapNotNull(AppKey::decode)?.toSet() ?: d.hidden,
            renames = p[K.renames]?.mapNotNull { line ->
                val tab = line.indexOf('\t')
                if (tab < 0) null else AppKey.decode(line.substring(0, tab))?.let { it to line.substring(tab + 1) }
            }?.toMap() ?: d.renames,
            showClock = p[K.showClock] ?: d.showClock,
            showDate = p[K.showDate] ?: d.showDate,
            showLog = p[K.showLog] ?: d.showLog,
            entryStyle = enumOr(p[K.entryStyle], d.entryStyle),
            hAlign = enumOr(p[K.hAlign], d.hAlign),
            vAlign = enumOr(p[K.vAlign], d.vAlign),
            swipeLeft = p[K.swipeLeft]?.let(AppKey::decode),
            swipeRight = p[K.swipeRight]?.let(AppKey::decode),
            doubleTapLock = p[K.doubleTapLock] ?: d.doubleTapLock,
            autoKeyboard = p[K.autoKeyboard] ?: d.autoKeyboard,
            autoLaunch = p[K.autoLaunch] ?: d.autoLaunch,
            logRows = p[K.logRows]?.split(',')?.filter { it.isNotBlank() } ?: d.logRows,
            logIntervalSec = p[K.logInterval] ?: d.logIntervalSec,
            showStream = p[K.showStream] ?: d.showStream,
            streamTimestamps = p[K.streamTimestamps] ?: d.streamTimestamps,
            showBanner = p[K.showBanner] ?: d.showBanner,
            banner = p[K.banner] ?: d.banner,
            bannerAlign = enumOr(p[K.bannerAlign], d.bannerAlign),
            lockMode = enumOr(p[K.lockMode], d.lockMode),
            logLayout = enumOr(p[K.logLayout], d.logLayout),
            feedNewestAtTop = p[K.feedTop] ?: d.feedNewestAtTop,
        )
    }

    private fun write(p: MutablePreferences, v: LauncherPrefs) {
        p[K.pinned] = v.pinned.joinToString("\n") { it.encode() }
        p[K.hidden] = v.hidden.map { it.encode() }.toSet()
        p[K.renames] = v.renames.map { (k, label) -> "${k.encode()}\t$label" }.toSet()
        p[K.showClock] = v.showClock
        p[K.showDate] = v.showDate
        p[K.showLog] = v.showLog
        p[K.entryStyle] = v.entryStyle.name
        p[K.hAlign] = v.hAlign.name
        p[K.vAlign] = v.vAlign.name
        v.swipeLeft?.let { p[K.swipeLeft] = it.encode() } ?: p.remove(K.swipeLeft)
        v.swipeRight?.let { p[K.swipeRight] = it.encode() } ?: p.remove(K.swipeRight)
        p[K.doubleTapLock] = v.doubleTapLock
        p[K.autoKeyboard] = v.autoKeyboard
        p[K.autoLaunch] = v.autoLaunch
        p[K.logRows] = v.logRows.joinToString(",")
        p[K.logInterval] = v.logIntervalSec
        p[K.showStream] = v.showStream
        p[K.streamTimestamps] = v.streamTimestamps
        p[K.showBanner] = v.showBanner
        p[K.banner] = v.banner
        p[K.bannerAlign] = v.bannerAlign.name
        p[K.lockMode] = v.lockMode.name
        p[K.logLayout] = v.logLayout.name
        p[K.feedTop] = v.feedNewestAtTop
    }

    private inline fun <reified E : Enum<E>> enumOr(name: String?, default: E): E =
        name?.let { runCatching { enumValueOf<E>(it) }.getOrNull() } ?: default
}
