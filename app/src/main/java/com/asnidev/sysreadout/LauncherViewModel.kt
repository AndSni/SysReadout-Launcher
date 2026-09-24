package com.asnidev.sysreadout

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import androidx.core.content.ContextCompat
import android.net.Uri
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
import com.asnidev.sysreadout.monitor.ShizukuBridge
import com.asnidev.sysreadout.system.LockScreen
import com.asnidev.sysreadout.ui.Fonts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

enum class Screen { HOME, DRAWER, SETTINGS }

class LauncherViewModel(app: Application) : AndroidViewModel(app) {

    val repo = AppRepository(app, viewModelScope)
    private val store = SettingsStore(app)

    val apps: StateFlow<List<AppEntry>> = repo.apps
    val prefs: StateFlow<LauncherPrefs> =
        store.prefs.stateIn(viewModelScope, SharingStarted.Eagerly, LauncherPrefs())
    val theme: StateFlow<Theme> =
        store.theme.stateIn(viewModelScope, SharingStarted.Eagerly, Presets.default.theme)
    val userPresets: StateFlow<List<Preset>> =
        store.presets.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val monitor: StateFlow<MonitorPrefs> =
        store.monitor.stateIn(viewModelScope, SharingStarted.Eagerly, MonitorPrefs())

    val shizuku = ShizukuBridge(app)
    val engine = LogEngine(app, prefs, monitor, apps, shizuku)

    private var snapshotAt = 0L
    private val screenOff = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (prefs.value.lockMode == LockMode.SNAPSHOT) updateLockSnapshot(force = false)
        }
    }

    /** Redraws the lock-screen snapshot; at most once a minute unless [force]d. */
    fun updateLockSnapshot(force: Boolean = true) {
        val now = System.currentTimeMillis()
        if (!force && now - snapshotAt < 60_000L) return
        snapshotAt = now
        viewModelScope.launch(Dispatchers.Default) {
            LockScreen.setSnapshot(getApplication(), engine.freshFrame(), previewTheme ?: theme.value, prefs.value)
        }
    }

    /** Debug builds: the snapshot as a file (cache/snapshot.png) rather than the wallpaper. */
    fun snapshotToFile() {
        viewModelScope.launch(Dispatchers.Default) {
            val app = getApplication<Application>()
            val bitmap = LockScreen.render(app, engine.freshFrame(), previewTheme ?: theme.value, prefs.value)
            File(app.cacheDir, "snapshot.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    init {
        ContextCompat.registerReceiver(
            app, screenOff, IntentFilter(Intent.ACTION_SCREEN_OFF), ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        // Bring the DNS monitor back after the launcher restarts, if the user left it on.
        viewModelScope.launch {
            val m = store.monitor.first()
            if (m.dnsVpn && !DnsLog.running.value && DnsVpnService.consentIntent(app) == null) DnsVpnService.start(app)
        }
    }

    /** Bumped when imported fonts change, so font lists re-read the directory. */
    var fontsVersion by mutableIntStateOf(0)

    var screen by mutableStateOf(Screen.HOME)

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
        viewModelScope.launch { store.update(transform) }
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
        viewModelScope.launch { store.updateMonitor(transform) }
    }

    // --- appearance ---

    fun updateTheme(transform: (Theme) -> Theme) {
        viewModelScope.launch { store.updateTheme(transform) }
    }

    fun savePreset(name: String) {
        val preset = Preset(name.trim(), theme.value)
        viewModelScope.launch { store.updatePresets { list -> list.filter { it.name != preset.name } + preset } }
    }

    fun deletePreset(name: String) {
        viewModelScope.launch { store.updatePresets { list -> list.filter { it.name != name } } }
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
        viewModelScope.launch {
            store.updatePresets { list -> list.map { p -> p.copy(theme = p.theme.replacingFont(id, "system-mono")) } }
        }
    }

    override fun onCleared() {
        getApplication<Application>().unregisterReceiver(screenOff)
        repo.close()
        shizuku.close()
    }
}
