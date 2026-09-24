package com.asnidev.sysreadout.ui.settings

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.asnidev.sysreadout.LauncherViewModel
import com.asnidev.sysreadout.data.HAlign
import com.asnidev.sysreadout.data.LauncherPrefs
import com.asnidev.sysreadout.data.LogLayout
import com.asnidev.sysreadout.data.MonitorPrefs
import com.asnidev.sysreadout.data.ProcSort
import com.asnidev.sysreadout.log.Access
import com.asnidev.sysreadout.log.BannerText
import com.asnidev.sysreadout.log.ProbeCatalog
import com.asnidev.sysreadout.log.ProbeInfo
import com.asnidev.sysreadout.log.ProbeReader
import com.asnidev.sysreadout.monitor.DnsLog
import com.asnidev.sysreadout.monitor.DnsVpnService
import com.asnidev.sysreadout.monitor.NotifLog
import com.asnidev.sysreadout.monitor.ShizukuState
import com.asnidev.sysreadout.ui.Palette
import com.asnidev.sysreadout.ui.Type

@Composable
fun LogPage(vm: LauncherViewModel) {
    val context = LocalContext.current
    val prefs by vm.prefs.collectAsState()
    val shown = prefs.logRows.mapNotNull { ProbeCatalog.byId[it] }

    // Rows that need a runtime permission ask for it when switched on.
    var pending by remember { mutableStateOf<String?>(null) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
        val id = pending ?: return@rememberLauncherForActivityResult
        pending = null
        val needs = ProbeCatalog.byId[id]?.needs
        if (needs?.runtimeGranted(context) == true || (id == "sun" && results.values.any { it })) {
            vm.toggleRow(id)
        } else {
            Toast.makeText(context, "without the permission that row can't show anything", Toast.LENGTH_SHORT).show()
        }
    }
    val enable: (ProbeInfo) -> Unit = { probe ->
        val needs = probe.needs
        when {
            needs.isRuntime && !needs.runtimeGranted(context) -> {
                pending = probe.id
                ask.launch(needs.permissions.toTypedArray())
            }
            needs == Access.NOTIFICATIONS && !NotifLog.connected -> {
                vm.toggleRow(probe.id)
                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            needs == Access.USAGE && !vm.engine.usage.hasAccess() -> {
                vm.toggleRow(probe.id)
                vm.engine.usage.openSettings()
            }
            else -> vm.toggleRow(probe.id)
        }
    }

    BannerSection(vm)

    Section("layout")
    Note(
        "classic: rows, monitor tables and the stream each in their own place. feed: one list without " +
            "headers. new rows push older ones off the screen; rows already showing update where they are, " +
            "and a row that fell off comes back when its value changes.",
    )
    Cycle("layout", prefs.logLayout, LogLayout.entries) { v -> vm.update { it.copy(logLayout = v) } }
    if (prefs.logLayout == LogLayout.FEED) {
        Cycle("new rows appear at", prefs.feedNewestAtTop, listOf(true, false), show = { if (it) "top" else "bottom" }) { v ->
            vm.update { it.copy(feedNewestAtTop = v) }
        }
    }

    Section("pinned rows · shown")
    Note("fixed rows at the top of the log, top to bottom. tap to remove, ↑ ↓ to reorder.")
    if (shown.isEmpty()) Text("  (none)", style = Type.row, color = Palette.dim)
    shown.forEachIndexed { i, probe ->
        ProbeRow(probe, on = true, onToggle = { vm.toggleRow(probe.id) }) {
            Arrow("↑", enabled = i > 0) { vm.moveRow(probe.id, -1) }
            Arrow("↓", enabled = i < shown.lastIndex) { vm.moveRow(probe.id, 1) }
        }
    }

    Section("pinned rows · available")
    Note("tap to add to the end of the list.")
    ProbeCatalog.all.filter { it.id !in prefs.logRows }.groupBy { it.group }.forEach { (group, probes) ->
        Text("  ## ${group.title}", style = Type.small, color = Palette.log, modifier = Modifier.padding(top = 8.dp))
        probes.forEach { probe -> ProbeRow(probe, on = false, onToggle = { enable(probe) }) {} }
    }

    Section("pinned rows · sampling")
    Cycle("refresh every", prefs.logIntervalSec, LauncherPrefs.INTERVALS, show = { "${it}s" }) { v ->
        vm.update { it.copy(logIntervalSec = v) }
    }
    Note("the log only samples while the home screen is visible.")

    MonitorSection(vm)

    Section("stream")
    Note("events scroll up from the bottom of the screen.")
    Toggle("show stream", prefs.showStream) { v -> vm.update { it.copy(showStream = v) } }
    Toggle("timestamps", prefs.streamTimestamps) { v -> vm.update { it.copy(streamTimestamps = v) } }
}

@Composable
private fun MonitorSection(vm: LauncherViewModel) {
    val context = LocalContext.current
    val m by vm.monitor.collectAsState()
    val shizuku by vm.shizuku.state.collectAsState()
    // Re-check access whenever we come back from system settings or the Shizuku app.
    var usageAccess by remember { mutableStateOf(vm.engine.usage.hasAccess()) }
    LifecycleResumeEffect(Unit) {
        usageAccess = vm.engine.usage.hasAccess()
        vm.shizuku.refresh()
        onPauseOrDispose {}
    }
    fun set(transform: (MonitorPrefs) -> MonitorPrefs) = vm.updateMonitor(transform)

    Section("system monitor")
    Note("tables and events from what Android lets SysReadout see. processes and connections need Shizuku (shell access you start yourself); app activity needs usage access.")
    Link("usage access", if (usageAccess) "granted" else "grant ›") { vm.engine.usage.openSettings() }
    Link("notification access", if (NotifLog.connected) "granted" else "grant ›") {
        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
    Link(
        "shizuku",
        when (shizuku) {
            ShizukuState.NOT_INSTALLED -> "get it ›"
            ShizukuState.NOT_RUNNING -> "start it ›"
            ShizukuState.NO_PERMISSION -> "grant ›"
            else -> shizuku.label
        },
    ) {
        when (shizuku) {
            ShizukuState.NOT_INSTALLED -> context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/download/")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            ShizukuState.NOT_RUNNING -> vm.shizuku.openApp()
            ShizukuState.NO_PERMISSION -> vm.shizuku.requestPermission()
            else -> vm.shizuku.refresh()
        }
    }
    if (shizuku == ShizukuState.NOT_RUNNING) {
        Note("open Shizuku and start it with wireless debugging (or adb, or root). it has to be restarted after every reboot unless you have root.")
    }
    Cycle("monitor refresh", m.intervalSec, MonitorPrefs.INTERVALS, show = { "${it}s" }) { v -> set { it.copy(intervalSec = v) } }

    Text("  ## tables", style = Type.small, color = Palette.log, modifier = Modifier.padding(top = 8.dp))
    Toggle("processes · shizuku", m.procs) { v -> set { it.copy(procs = v) } }
    if (m.procs) {
        Cycle("  sort by", m.procSort, ProcSort.entries) { v -> set { it.copy(procSort = v) } }
        Rows("  rows", m.procRows) { v -> set { it.copy(procRows = v) } }
        Toggle("  apps only", m.appsOnly) { v -> set { it.copy(appsOnly = v) } }
    }
    Toggle("connections · shizuku", m.conns) { v -> set { it.copy(conns = v) } }
    if (m.conns) Rows("  rows", m.connRows) { v -> set { it.copy(connRows = v) } }
    Toggle("  hostnames (reverse dns)", m.resolveHosts) { v -> set { it.copy(resolveHosts = v) } }
    Toggle("screen time today · usage", m.screenTime) { v -> set { it.copy(screenTime = v) } }
    if (m.screenTime) Rows("  rows", m.screenRows) { v -> set { it.copy(screenRows = v) } }
    Toggle("traffic today · usage", m.traffic) { v -> set { it.copy(traffic = v) } }
    if (m.traffic) Rows("  rows", m.trafficRows) { v -> set { it.copy(trafficRows = v) } }
    Toggle("wakelocks · shizuku", m.wakelocks) { v -> set { it.copy(wakelocks = v) } }
    if (m.wakelocks) Rows("  rows", m.wakeRows) { v -> set { it.copy(wakeRows = v) } }
    Toggle("battery drain per app · shizuku", m.battery) { v -> set { it.copy(battery = v) } }
    if (m.battery) Rows("  rows", m.batteryRows) { v -> set { it.copy(batteryRows = v) } }
    Toggle("notifications today · notification access", m.notifTable) { v -> set { it.copy(notifTable = v) } }
    if (m.notifTable) Rows("  rows", m.notifRows) { v -> set { it.copy(notifRows = v) } }

    Text("  ## stream events", style = Type.small, color = Palette.log, modifier = Modifier.padding(top = 8.dp))
    Toggle("app switches · usage", m.evApps) { v -> set { it.copy(evApps = v) } }
    Toggle("foreground services · usage", m.evServices) { v -> set { it.copy(evServices = v) } }
    Toggle("screen & lock · usage", m.evScreen) { v -> set { it.copy(evScreen = v) } }
    Toggle("process start / exit · shizuku", m.evProcs) { v -> set { it.copy(evProcs = v) } }
    Toggle("new connections · shizuku", m.evConns) { v -> set { it.copy(evConns = v) } }
    Toggle("dns lookups · dns monitor", m.evDns) { v -> set { it.copy(evDns = v) } }
    Toggle("notifications · notification access", m.evNotif) { v -> set { it.copy(evNotif = v) } }
    if (m.evNotif) Toggle("  show titles", m.notifTitles) { v -> set { it.copy(notifTitles = v) } }
    Toggle("system errors (logcat) · shizuku", m.evLogcat) { v -> set { it.copy(evLogcat = v) } }
    if (m.evLogcat) Toggle("  include warnings", m.logcatWarnings) { v -> set { it.copy(logcatWarnings = v) } }
    Toggle("network, power, battery, installs", m.evSystem) { v -> set { it.copy(evSystem = v) } }

    DnsMonitorSection(vm)
}

@Composable
private fun DnsMonitorSection(vm: LauncherViewModel) {
    val context = LocalContext.current
    val m by vm.monitor.collectAsState()
    val running by DnsLog.running.collectAsState()
    val consent = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            vm.updateMonitor { it.copy(dnsVpn = true) }
            DnsVpnService.start(context)
        }
    }
    val strictPrivateDns = Settings.Global.getString(context.contentResolver, "private_dns_mode") == "hostname"

    Section("dns monitor")
    Note(
        "shows which app looks up which server name (imap.gmail.com, not just an IP). it's a local vpn that " +
            "routes only dns: each lookup is noted and passed on unchanged to your network's dns server. " +
            "nothing else goes through it and nothing is sent anywhere else. android allows one vpn at a time, " +
            "so it can't run next to another vpn.",
    )
    Toggle("dns monitor", m.dnsVpn && running) { on ->
        if (on) {
            val ask = DnsVpnService.consentIntent(context)
            if (ask == null) {
                vm.updateMonitor { it.copy(dnsVpn = true) }
                DnsVpnService.start(context)
            } else {
                consent.launch(ask)
            }
        } else {
            vm.updateMonitor { it.copy(dnsVpn = false) }
            DnsVpnService.stop(context)
        }
    }
    if (m.dnsVpn && !running) Note("switched on but not running: another vpn may have taken over.")
    if (strictPrivateDns) {
        Note("private dns is set to a specific provider, so android sends lookups there directly and the monitor sees none. set private dns to automatic or off to use it.")
    }
    Note("apps with their own encrypted dns (some browsers) bypass it too.")
}

@Composable
private fun BannerSection(vm: LauncherViewModel) {
    val prefs by vm.prefs.collectAsState()
    var editing by remember { mutableStateOf(false) }

    Section("banner")
    Note("free text above the log, like a terminal's boot header.")
    Toggle("show banner", prefs.showBanner) { v -> vm.update { it.copy(showBanner = v) } }
    Link("edit text", "›") { editing = true }
    Cycle("alignment", prefs.bannerAlign, HAlign.entries) { v -> vm.update { it.copy(bannerAlign = v) } }

    if (editing) {
        var text by remember { mutableStateOf(prefs.banner) }
        AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text("banner", style = Type.row) },
            text = {
                Column {
                    OutlinedTextField(
                        text,
                        { text = it },
                        textStyle = Type.row,
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Note("")
                    Note("filled in live: " + BannerText.VARIABLES.joinToString(" ") { "{$it}" })
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.update { it.copy(banner = text) }
                    editing = false
                }) { Text("save", style = Type.row) }
            },
            dismissButton = {
                TextButton(onClick = { text = LauncherPrefs.DEFAULT_BANNER }) { Text("default", style = Type.row) }
            },
        )
    }
}

@Composable
private fun Rows(label: String, value: Int, onChange: (Int) -> Unit) =
    Stepper(label, "$value", { onChange((value - 1).coerceAtLeast(1)) }) { onChange((value + 1).coerceAtMost(30)) }

@Composable
private fun ProbeRow(probe: ProbeInfo, on: Boolean, onToggle: () -> Unit, trailing: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.weight(1f).clickable(onClick = onToggle).padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                (if (on) "[x] " else "[ ] ") + probe.id.padEnd(ProbeReader.KEY_WIDTH),
                style = Type.row,
                color = if (on) Palette.log else Palette.fg,
            )
            Column(Modifier.weight(1f)) {
                val needs = if (probe.needs == Access.NONE) "" else " · ${probe.needs.tag}"
                Text(probe.summary + needs, style = Type.small, color = Palette.dim)
            }
        }
        trailing()
    }
}

@Composable
private fun Arrow(glyph: String, enabled: Boolean, onClick: () -> Unit) {
    Text(
        " $glyph ",
        style = Type.row,
        color = if (enabled) Palette.fg else Palette.dim.copy(alpha = 0.3f),
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick).padding(horizontal = 4.dp, vertical = 6.dp),
    )
}
