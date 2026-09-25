package com.asnidev.sysreadout

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import androidx.core.content.ContextCompat
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.asnidev.sysreadout.apps.AppEntry
import com.asnidev.sysreadout.apps.AppRepository
import com.asnidev.sysreadout.data.AppKey
import com.asnidev.sysreadout.data.LauncherPrefs
import com.asnidev.sysreadout.data.LockMode
import com.asnidev.sysreadout.data.LogLayout
import com.asnidev.sysreadout.data.MonitorPrefs
import com.asnidev.sysreadout.data.Preset
import com.asnidev.sysreadout.data.Presets
import com.asnidev.sysreadout.data.SettingsStore
import com.asnidev.sysreadout.data.StyleElement
import com.asnidev.sysreadout.data.Theme
import com.asnidev.sysreadout.log.LogEngine
import com.asnidev.sysreadout.monitor.DnsLog
import com.asnidev.sysreadout.monitor.DnsVpnService
import com.asnidev.sysreadout.monitor.NotifLog
import com.asnidev.sysreadout.monitor.SetupItem
import com.asnidev.sysreadout.monitor.ShizukuBridge
import com.asnidev.sysreadout.monitor.ShizukuSetup
import com.asnidev.sysreadout.system.CrashGuard
import com.asnidev.sysreadout.system.LockScreen
import com.asnidev.sysreadout.system.LockService
import com.asnidev.sysreadout.ui.Fonts
import com.asnidev.sysreadout.ui.settings.Page
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import java.io.File

enum class Screen { HOME, DRAWER, SETTINGS }

class LauncherViewModel(app: Application) : AndroidViewModel(app) {

    // Background work that fails is logged, never allowed to crash the home screen.
    private val scope: CoroutineScope =
        viewModelScope + CoroutineExceptionHandler { _, e -> Log.w(TAG, "background task failed", e) }

    /**
     * Set by [CrashGuard] after SysReadout crashed repeatedly right after starting:
     * a plain launcher with the default look, and no log, Shizuku or DNS monitor.
     */
    val safeMode = MutableStateFlow(CrashGuard.isSafeMode(app))

    val repo = AppRepository(app, scope)
    private val store = SettingsStore(app)

    val apps: StateFlow<List<AppEntry>> = repo.apps
    val prefs: StateFlow<LauncherPrefs> =
        store.prefs.stateIn(scope, SharingStarted.Eagerly, LauncherPrefs())
    val theme: StateFlow<Theme> =
        store.theme.stateIn(scope, SharingStarted.Eagerly, Presets.default.theme)
    val userPresets: StateFlow<List<Preset>> =
        store.presets.stateIn(scope, SharingStarted.Eagerly, emptyList())
    val monitor: StateFlow<MonitorPrefs> =
        store.monitor.stateIn(scope, SharingStarted.Eagerly, MonitorPrefs())

    /** Completed once the saved settings have reached [prefs] and [monitor] (they start as defaults). */
    private val settingsLoaded = CompletableDeferred<Unit>()

    val shizuku = ShizukuBridge(app)
    val engine = LogEngine(
        app, prefs, monitor, apps, shizuku,
        onShizukuTip = { updateMonitor { it.copy(shizukuTip = true) } },
        awaitSettings = { settingsLoaded.await() },
    )

    private var snapshotAt = 0L
    private val screenOff = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (prefs.value.lockMode == LockMode.SNAPSHOT) updateLockSnapshot(force = false)
        }
    }

    /** Redraws the lock-screen snapshot; at most once a minute unless [force]d. */
    fun updateLockSnapshot(force: Boolean = true) {
        if (safeMode.value) return
        val now = System.currentTimeMillis()
        if (!force && now - snapshotAt < 60_000L) return
        snapshotAt = now
        scope.launch(Dispatchers.Default) {
            LockScreen.setSnapshot(getApplication(), engine.freshFrame(), previewTheme ?: theme.value, prefs.value)
        }
    }

    /** Debug builds: the snapshot as a file (cache/snapshot.png) rather than the wallpaper. */
    fun snapshotToFile() {
        scope.launch(Dispatchers.Default) {
            val app = getApplication<Application>()
            val bitmap = LockScreen.render(app, engine.freshFrame(), previewTheme ?: theme.value, prefs.value)
            File(app.cacheDir, "snapshot.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    init {
        ContextCompat.registerReceiver(
            app, screenOff, IntentFilter(Intent.ACTION_SCREEN_OFF), ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        scope.launch {
            try {
                val m = store.monitor.first()
                val p = store.prefs.first()
                // The StateFlows catch up on this same main thread a moment later.
                withTimeoutOrNull(2_000) {
                    monitor.first { it == m }
                    prefs.first { it == p }
                }
            } finally {
                settingsLoaded.complete(Unit)
            }
            // Shizuku runs only while the user wants it, and never in safe mode.
            combine(monitor, safeMode) { m, safe -> m.shizuku && !safe }
                .distinctUntilChanged()
                .collect { shizuku.setEnabled(it) }
        }
        restoreDnsMonitor()
    }

    /** Brings the DNS monitor back after the launcher restarts, if the user left it on. */
    private fun restoreDnsMonitor() {
        scope.launch {
            val m = store.monitor.first()
            if (safeMode.value || !m.dnsVpn || DnsLog.running.value) return@launch
            val allowed = withContext(Dispatchers.IO) { DnsVpnService.consentIntent(getApplication()) == null }
            if (allowed) DnsVpnService.start(getApplication())
        }
    }

    /** Whether the access behind a one-tap setup item is on already. */
    fun isGranted(item: SetupItem): Boolean = when (item) {
        SetupItem.USAGE -> engine.usage.hasAccess()
        SetupItem.NOTIFICATIONS -> NotifLog.connected
        SetupItem.LOCK -> LockService.isRunning
    }

    /**
     * Switches [items] on through Shizuku's shell and reports, per item, whether
     * it's really on afterwards (Android binds the services a moment later).
     */
    suspend fun setUpWithShizuku(items: Set<SetupItem>): Map<SetupItem, Boolean> {
        val app = getApplication<Application>()
        val user = android.os.Process.myUid() / 100_000
        val ran = items.associateWith { ShizukuSetup.apply(it, shizuku, app.packageName, user) }
        for (i in 0 until 20) {
            if (items.all { ran[it] != true || isGranted(it) }) break
            delay(250)
        }
        val results = items.associateWith { ran[it] == true && isGranted(it) }
        if (results[SetupItem.LOCK] == true) update { it.copy(doubleTapLock = true) }
        return results
    }

    /** Back to normal after safe mode: the log, Shizuku and the DNS monitor start again. */
    fun leaveSafeMode() {
        CrashGuard.leaveSafeMode(getApplication())
        safeMode.value = false
        restoreDnsMonitor()
    }

    /** Bumped when imported fonts change, so font lists re-read the directory. */
    var fontsVersion by mutableIntStateOf(0)

    var screen by mutableStateOf(Screen.HOME)

    /** A settings page to open next time settings show (debug `--es page …`). */
    var requestedPage by mutableStateOf<Page?>(null)

    /** Debug builds only: a preset shown without being saved (`adb shell am start … --es preview <name>`). */
    var previewTheme by mutableStateOf<Theme?>(null)

    /** Debug builds only: a log layout shown without being saved (`--es layout feed`, `--es feed bottom`). */
    var previewLayout by mutableStateOf<LogLayout?>(null)
    var previewFeedTop by mutableStateOf<Boolean?>(null)

    /** How many menu entries fit in the home screen's free vertical space; set by the home layout. */
    var menuCapacity by mutableIntStateOf(Int.MAX_VALUE)

    fun label(entry: AppEntry): String = prefs.value.renames[entry.key] ?: entry.label

    fun launch(key: AppKey): Boolean {
        val ok = repo.launch(key)
        if (ok) screen = Screen.HOME
        return ok
    }

    fun update(transform: (LauncherPrefs) -> LauncherPrefs) {
        scope.launch { store.update(transform) }
    }

    /** False when the home screen has no vertical room left. */
    fun pin(key: AppKey): Boolean {
        val p = prefs.value
        if (key in p.pinned) return true
        if (p.pinned.size >= menuCapacity) return false
        update { it.copy(pinned = it.pinned + key) }
        return true
    }

    fun unpin(key: AppKey) = update { it.copy(pinned = it.pinned - key) }

    fun move(key: AppKey, delta: Int) = update {
        val list = it.pinned.toMutableList()
        val from = list.indexOf(key)
        val to = (from + delta).coerceIn(0, list.lastIndex)
        if (from < 0 || from == to) it else it.copy(pinned = list.apply { add(to, removeAt(from)) })
    }

    fun rename(key: AppKey, label: String) = update {
        it.copy(renames = if (label.isBlank()) it.renames - key else it.renames + (key to label.trim()))
    }

    fun hide(key: AppKey) = update { it.copy(hidden = it.hidden + key, pinned = it.pinned - key) }
    fun unhide(key: AppKey) = update { it.copy(hidden = it.hidden - key) }

    // --- log rows ---

    fun toggleRow(id: String) = update {
        it.copy(logRows = if (id in it.logRows) it.logRows - id else it.logRows + id)
    }

    fun moveRow(id: String, delta: Int) = update {
        val list = it.logRows.toMutableList()
        val from = list.indexOf(id)
        val to = (from + delta).coerceIn(0, list.lastIndex)
        if (from < 0 || from == to) it else it.copy(logRows = list.apply { add(to, removeAt(from)) })
    }

    fun updateMonitor(transform: (MonitorPrefs) -> MonitorPrefs) {
        scope.launch { store.updateMonitor(transform) }
    }

    // --- appearance ---

    fun updateTheme(transform: (Theme) -> Theme) {
        scope.launch { store.updateTheme(transform) }
    }

    fun savePreset(name: String) {
        val preset = Preset(name.trim(), theme.value)
        scope.launch { store.updatePresets { list -> list.filter { it.name != preset.name } + preset } }
    }

    fun deletePreset(name: String) {
        scope.launch { store.updatePresets { list -> list.filter { it.name != name } } }
    }

    /** Imports a font file and applies it to [element]; false if the file isn't a font. */
    suspend fun importFont(uri: Uri, element: StyleElement): Boolean {
        val font = withContext(Dispatchers.IO) { Fonts.import(getApplication(), uri) } ?: return false
        fontsVersion++
        updateTheme { it.with(element, it.spec(element).copy(font = font.id)) }
        return true
    }

    fun deleteFont(id: String) {
        Fonts.delete(getApplication(), id)
        fontsVersion++
        updateTheme { it.replacingFont(id, "system-mono") }
        scope.launch {
            store.updatePresets { list -> list.map { p -> p.copy(theme = p.theme.replacingFont(id, "system-mono")) } }
        }
    }

    override fun onCleared() {
        runCatching { getApplication<Application>().unregisterReceiver(screenOff) }
        repo.close()
        shizuku.close()
    }

    private companion object {
        const val TAG = "SysReadout"
    }
}
