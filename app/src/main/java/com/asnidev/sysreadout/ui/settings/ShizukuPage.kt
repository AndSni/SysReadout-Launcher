package com.asnidev.sysreadout.ui.settings

import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.asnidev.sysreadout.LauncherViewModel
import com.asnidev.sysreadout.monitor.SetupItem
import com.asnidev.sysreadout.monitor.ShizukuSetup
import com.asnidev.sysreadout.monitor.ShizukuState
import com.asnidev.sysreadout.system.SystemActions
import com.asnidev.sysreadout.ui.Type
import kotlinx.coroutines.launch

private const val DOWNLOAD = "https://shizuku.rikka.app/download/"

private val FEATURES = listOf(
    "processes, busiest first, by cpu or memory",
    "every connection per app, with server names",
    "wakelocks: what keeps the phone awake",
    "battery drain per app since the last charge",
    "temperatures, per-core load, load average",
    "system errors from android's log as they happen",
)

/**
 * Everything about Shizuku in one place: what it adds, a live checklist from
 * "installed" to "connected" with a button for each step, how to start it
 * without a computer and keep it running after a reboot, and the one-tap
 * setup of SysReadout's other access once it's connected.
 */
@Composable
fun ShizukuPage(vm: LauncherViewModel) {
    val context = LocalContext.current
    val m by vm.monitor.collectAsState()
    val state by vm.shizuku.state.collectAsState()
    val safe by vm.safeMode.collectAsState()
    var setup by remember { mutableStateOf(false) }

    // Coming back from the Shizuku app or Android's settings: look again.
    LifecycleResumeEffect(Unit) {
        vm.shizuku.refresh()
        onPauseOrDispose {}
    }

    Section("what it adds")
    Note(
        "shizuku is a free app that lends sysreadout the access a computer's adb connection has, " +
            "only while you allow it. with it the log also shows:",
    )
    FEATURES.forEach { Note("  · $it") }
    Note("it's optional: everything else works without it, and what it shows stays on the phone.")
    Toggle("use shizuku", m.shizuku) { v -> vm.updateMonitor { it.copy(shizuku = v) } }
    if (safe) Note("paused while sysreadout is in safe mode (settings › safe mode › resume).")
    if (!m.shizuku || safe) return

    val installed = state !in setOf(ShizukuState.OFF, ShizukuState.NOT_INSTALLED)
    val running = installed && state != ShizukuState.NOT_RUNNING
    val allowed = state in setOf(ShizukuState.CONNECTING, ShizukuState.FAILING, ShizukuState.READY)

    Section("status")
    Step(1, "shizuku installed", installed, "get it ›") {
        if (!SystemActions.openUrl(context, DOWNLOAD)) toast(context, "no browser to open $DOWNLOAD")
    }
    Step(2, "shizuku running", running, "open shizuku ›") {
        if (!vm.shizuku.openApp()) toast(context, "shizuku isn't installed")
    }
    Step(3, "sysreadout allowed", allowed, "allow ›") { vm.shizuku.requestPermission() }
    Step(4, "connected", state == ShizukuState.READY, if (state == ShizukuState.FAILING) "retry now ›" else "…") {
        vm.shizuku.restartHelper()
    }
    when {
        state == ShizukuState.UNSUPPORTED -> Note("this shizuku is too old for sysreadout: update it.")
        state == ShizukuState.FAILING ->
            Note("shizuku's helper kept failing, so sysreadout left it alone for a while. it retries by itself.")
        state == ShizukuState.NO_PERMISSION ->
            Note("tap allow, then choose \"allow all the time\" in shizuku's dialog.")
        running && vm.shizuku.isSui -> Note("running through sui (root): nothing to start after a reboot.")
    }

    if (!running) {
        Section("start shizuku")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Note("no computer needed; pairing is done once.")
            Note("1. open developer options. hidden? settings › about phone › tap \"build number\" 7 times.")
            Link("open developer options", "›") { SystemActions.openDeveloperOptions(context) }
            Note("2. connect to wi-fi and switch \"wireless debugging\" on.")
            Note(
                "3. in shizuku, tap \"pairing\" under \"start via wireless debugging\". then in wireless " +
                    "debugging tap \"pair device with pairing code\" and type the code into shizuku's notification.",
            )
            Note("4. in shizuku, tap \"start\". back here, allow sysreadout when shizuku asks.")
        } else {
            Note("on android 10 and older shizuku is started from a computer with adb, once per boot; the shizuku app shows the command.")
        }
        Note("rooted phone: shizuku starts itself, or use sui instead.")
    }

    Section("after a reboot")
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Note(
            "without root, android stops shizuku at every reboot. to have it start by itself: in shizuku › " +
                "settings, switch \"start on boot\" on. on android 13 and newer that works without root, over " +
                "wireless debugging, whenever the phone is on a wi-fi where you chose \"always allow on this network\".",
        )
    } else {
        Note("without root, android stops shizuku at every reboot: start it again in the shizuku app.")
    }
    Note("sysreadout notices when shizuku comes back and reconnects by itself.")

    if (state == ShizukuState.READY) {
        Section("one-tap setup")
        Note("shizuku can also switch on sysreadout's other access for you. those stay on after shizuku stops.")
        Link("set up access with shizuku", "›") { setup = true }
    }
    if (setup) SetupDialog(vm) { setup = false }
}

@Composable
private fun Step(n: Int, label: String, done: Boolean, action: String, onAction: () -> Unit) =
    SettingRow("${if (done) "[x]" else "[ ]"} $n $label", if (done) "ok" else action) { if (!done) onAction() }

/** Lists what the setup would switch on, with the already-granted ones marked; runs only on "switch on". */
@Composable
private fun SetupDialog(vm: LauncherViewModel, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val already = remember { ShizukuSetup.items.filter(vm::isGranted).toSet() }
    var chosen by remember { mutableStateOf(ShizukuSetup.items.toSet() - already) }
    var results by remember { mutableStateOf<Map<SetupItem, Boolean>?>(null) }
    var working by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!working) onDismiss() },
        title = { Text("set up with shizuku", style = Type.row) },
        text = {
            Column {
                Note("switches these on for sysreadout, the same as you would in android's settings:")
                ShizukuSetup.items.forEach { item ->
                    val result = results?.get(item)
                    val value = when {
                        item in already -> "on already"
                        result == true -> "done"
                        result == false -> "failed"
                        item in chosen -> "[x]"
                        else -> "[ ]"
                    }
                    SettingRow(item.title, value) {
                        if (item !in already && results == null && !working) {
                            chosen = if (item in chosen) chosen - item else chosen + item
                        }
                    }
                    Note("  ${item.detail}")
                }
                if (results?.containsValue(false) == true) {
                    Note("what failed can still be switched on by hand in android's settings.")
                }
            }
        },
        confirmButton = {
            if (results == null) {
                TextButton(
                    enabled = chosen.isNotEmpty() && !working,
                    onClick = {
                        working = true
                        scope.launch {
                            results = vm.setUpWithShizuku(chosen)
                            working = false
                        }
                    },
                ) { Text(if (working) "working…" else "switch on", style = Type.row) }
            } else {
                TextButton(onClick = onDismiss) { Text("done", style = Type.row) }
            }
        },
        dismissButton = {
            if (results == null) TextButton(onClick = onDismiss, enabled = !working) { Text("cancel", style = Type.row) }
        },
    )
}

private fun toast(context: android.content.Context, text: String) =
    Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
