package com.asnidev.sysreadout.ui.settings

import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.asnidev.sysreadout.LauncherViewModel
import com.asnidev.sysreadout.data.AppKey
import com.asnidev.sysreadout.data.EntryStyle
import com.asnidev.sysreadout.data.HAlign
import com.asnidev.sysreadout.data.LockMode
import com.asnidev.sysreadout.data.StyleElement
import com.asnidev.sysreadout.data.VAlign
import com.asnidev.sysreadout.system.LockScreen
import com.asnidev.sysreadout.system.LockService
import com.asnidev.sysreadout.system.SystemActions
import com.asnidev.sysreadout.ui.AppPickerDialog
import com.asnidev.sysreadout.ui.Palette
import com.asnidev.sysreadout.ui.Type
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface Page {
    val title: String

    data object Root : Page { override val title = "" }
    data object Log : Page { override val title = "log" }
    data object Appearance : Page { override val title = "appearance" }
    data class Element(val element: StyleElement) : Page { override val title get() = element.title }
    data object About : Page { override val title = "about" }
    data object Crt : Page { override val title = "crt effects" }
    data object Shizuku : Page { override val title = "shizuku" }

    companion object {
        /** Top-level pages by name, for `--es page <name>`. */
        fun byName(name: String): Page? = listOf(Log, Appearance, About, Crt, Shizuku)
            .firstOrNull { it.title.substringBefore(' ') == name.lowercase() }
    }
}

@Composable
fun SettingsScreen(vm: LauncherViewModel) {
    var stack by remember { mutableStateOf(listOf<Page>(Page.Root)) }
    val page = stack.last()
    val open: (Page) -> Unit = { stack = stack + it }
    val back = { stack = stack.dropLast(1) }

    LaunchedEffect(vm.requestedPage) {
        vm.requestedPage?.let {
            stack = listOf(Page.Root, it)
            vm.requestedPage = null
        }
    }

    BackHandler(enabled = stack.size > 1, onBack = back)

    Column(Modifier.fillMaxSize().background(Palette.bg.copy(alpha = 0.94f)).systemBarsPadding()) {
        // Each page gets its own scroll position.
        key(page) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp)) {
                Header(stack.drop(1).map { it.title }, onBack = if (stack.size > 1) back else null)
                when (page) {
                    Page.Root -> RootPage(vm, open)
                    Page.Log -> LogPage(vm, open)
                    Page.Appearance -> AppearancePage(vm, open)
                    is Page.Element -> ElementPage(vm, page.element)
                    Page.About -> AboutPage()
                    Page.Crt -> CrtPage(vm)
                    Page.Shizuku -> ShizukuPage(vm)
                }
            }
        }
    }
}

private enum class Picking { LEFT, RIGHT }

@Composable
private fun RootPage(vm: LauncherViewModel, open: (Page) -> Unit) {
    val context = LocalContext.current
    val prefs by vm.prefs.collectAsState()
    val apps by vm.apps.collectAsState()
    val monitor by vm.monitor.collectAsState()
    val shizuku by vm.shizuku.state.collectAsState()
    val safe by vm.safeMode.collectAsState()
    var picking by remember { mutableStateOf<Picking?>(null) }

    fun appName(key: AppKey?): String =
        key?.let { k -> apps.firstOrNull { it.key == k }?.let(vm::label) } ?: "none"

    if (safe) {
        Section("safe mode")
        Note(
            "sysreadout crashed twice right after starting, so the log, the system monitor, shizuku, " +
                "the dns monitor and your look are paused. the crash report is under about.",
        )
        Link("resume", "switch everything back on ›") { vm.leaveSafeMode() }
    }

    if (!SystemActions.isDefaultLauncher(context)) {
        Section("launcher")
        Link("set as default home app") { SystemActions.openHomeSettings(context) }
    }

    Section("screens")
    Link("appearance", "fonts · colours · presets ›") { open(Page.Appearance) }
    Link("log", "${prefs.logRows.size} rows ›") { open(Page.Log) }
    Link("shizuku", (if (monitor.shizuku) shizuku.label else "off · optional") + " ›") { open(Page.Shizuku) }

    Section("home")
    Toggle("clock", prefs.showClock) { v -> vm.update { it.copy(showClock = v) } }
    Toggle("date", prefs.showDate) { v -> vm.update { it.copy(showDate = v) } }
    Toggle("log background", prefs.showLog) { v -> vm.update { it.copy(showLog = v) } }
    Cycle("entry style", prefs.entryStyle, EntryStyle.entries) { v -> vm.update { it.copy(entryStyle = v) } }
    Cycle("horizontal align", prefs.hAlign, HAlign.entries) { v -> vm.update { it.copy(hAlign = v) } }
    Cycle("vertical position", prefs.vAlign, VAlign.entries) { v -> vm.update { it.copy(vAlign = v) } }

    LockScreenSection(vm)

    Section("drawer")
    Toggle("open keyboard", prefs.autoKeyboard) { v -> vm.update { it.copy(autoKeyboard = v) } }
    Toggle("launch single match", prefs.autoLaunch) { v -> vm.update { it.copy(autoLaunch = v) } }

    Section("gestures")
    Link("swipe left", appName(prefs.swipeLeft)) { picking = Picking.LEFT }
    Link("swipe right", appName(prefs.swipeRight)) { picking = Picking.RIGHT }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        Toggle("double-tap to lock", prefs.doubleTapLock) { v ->
            vm.update { it.copy(doubleTapLock = v) }
            if (v && !LockService.isRunning) SystemActions.openAccessibilitySettings(context)
        }
    } else {
        Note("double-tap to lock needs Android 9 or newer.")
    }

    Section("hidden apps")
    val hidden = apps.filter { it.key in prefs.hidden }
    if (hidden.isEmpty()) Text("  (none)", style = Type.row, color = Palette.dim)
    hidden.forEach { app -> Link(vm.label(app), "[unhide]") { vm.unhide(app.key) } }

    Section("about")
    Link("about & licenses") { open(Page.About) }

    picking?.let { side ->
        AppPickerDialog(
            apps = apps.filter { it.key !in prefs.hidden },
            label = vm::label,
            onDismiss = { picking = null },
        ) { app ->
            vm.update { if (side == Picking.LEFT) it.copy(swipeLeft = app?.key) else it.copy(swipeRight = app?.key) }
            picking = null
        }
    }
}

@Composable
private fun LockScreenSection(vm: LauncherViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs by vm.prefs.collectAsState()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val ok = withContext(Dispatchers.IO) { LockScreen.setImage(context, uri) }
                Toast.makeText(context, if (ok) "lock screen set" else "couldn't use that image", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Section("lock screen")
    Cycle(
        "wallpaper",
        prefs.lockMode,
        LockMode.entries,
        show = {
            when (it) {
                LockMode.OFF -> "leave alone"
                LockMode.IMAGE -> "your image"
                LockMode.SNAPSHOT -> "log snapshot"
            }
        },
    ) { v ->
        val was = prefs.lockMode
        vm.update { it.copy(lockMode = v) }
        if (v == LockMode.SNAPSHOT) vm.updateLockSnapshot()
        // Leaving image or snapshot: take SysReadout's picture off the lock screen again.
        if (v == LockMode.OFF && was != LockMode.OFF) scope.launch(Dispatchers.IO) { LockScreen.clear(context) }
    }
    when (prefs.lockMode) {
        LockMode.OFF -> Note("SysReadout doesn't touch your lock-screen wallpaper. switching here from image or snapshot gives the lock screen your home wallpaper back.")
        LockMode.IMAGE -> Link("choose image") { picker.launch(arrayOf("image/*")) }
        LockMode.SNAPSHOT -> {
            Link("update now") {
                vm.updateLockSnapshot()
                Toast.makeText(context, "lock screen updated", Toast.LENGTH_SHORT).show()
            }
            Note("the log, drawn with your theme and crt effects, redrawn each time the screen turns off (at most once a minute). android draws its clock and notifications on top.")
        }
    }
}
