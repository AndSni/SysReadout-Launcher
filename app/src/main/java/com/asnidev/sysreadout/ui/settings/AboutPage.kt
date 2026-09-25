package com.asnidev.sysreadout.ui.settings

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.asnidev.sysreadout.BuildConfig
import com.asnidev.sysreadout.system.CrashGuard
import com.asnidev.sysreadout.system.SystemActions
import com.asnidev.sysreadout.ui.FontOption
import com.asnidev.sysreadout.ui.Fonts
import com.asnidev.sysreadout.ui.Type

private const val REPO = "https://github.com/AndSni/SysReadout-Launcher"

@Composable
fun AboutPage() {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var showing by remember { mutableStateOf<FontOption?>(null) }
    var report by remember { mutableStateOf(CrashGuard.report(context)) }
    var reading by remember { mutableStateOf(false) }

    Section("sysreadout launcher")
    Note("v${BuildConfig.VERSION_NAME} · free software under the GNU GPL v3")
    Link("source code and issues", "github ›") { SystemActions.openUrl(context, REPO) }

    report?.let { text ->
        Section("last crash")
        Note(text.lineSequence().take(2).joinToString("\n"))
        Link("read the report", "›") { reading = true }
        Link("copy it", "›") {
            clipboard.setText(AnnotatedString(text))
            Toast.makeText(context, "crash report copied", Toast.LENGTH_SHORT).show()
        }
        Link("share it", "›") {
            val send = Intent(Intent.ACTION_SEND).setType("text/plain")
                .putExtra(Intent.EXTRA_SUBJECT, "SysReadout crash report")
                .putExtra(Intent.EXTRA_TEXT, text)
            SystemActions.open(context, Intent.createChooser(send, "share the crash report"))
        }
        Link("delete it", "›") {
            CrashGuard.clearReport(context)
            report = null
        }
        Note("only the last crash is kept, on this phone. nothing is sent unless you share it.")
    }

    Section("bundled fonts")
    Note("tap for the license text.")
    Fonts.bundled.filter { it.licenseFile != null }.forEach { font ->
        Link(font.name, font.license ?: "") { showing = font }
    }

    if (reading) {
        report?.let { TextDialog("last crash", it) { reading = false } }
    }
    showing?.let { font ->
        val text = remember(font) {
            runCatching { context.assets.open("licenses/${font.licenseFile}").bufferedReader().use { it.readText() } }
                .getOrDefault("license text missing")
        }
        TextDialog(font.name, text) { showing = null }
    }
}

@Composable
private fun TextDialog(title: String, text: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = Type.row) },
        text = {
            Text(text, style = Type.small, modifier = Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()))
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("close", style = Type.row) } },
    )
}
