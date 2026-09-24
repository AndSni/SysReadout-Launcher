package com.asnidev.sysreadout.ui.settings

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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.asnidev.sysreadout.BuildConfig
import com.asnidev.sysreadout.ui.FontOption
import com.asnidev.sysreadout.ui.Fonts
import com.asnidev.sysreadout.ui.Type

@Composable
fun AboutPage() {
    var showing by remember { mutableStateOf<FontOption?>(null) }

    Section("sysreadout launcher")
    Note("v${BuildConfig.VERSION_NAME} · free software under the GNU GPL v3")

    Section("bundled fonts")
    Note("tap for the license text.")
    Fonts.bundled.filter { it.licenseFile != null }.forEach { font ->
        Link(font.name, font.license ?: "") { showing = font }
    }

    showing?.let { font ->
        val context = LocalContext.current
        val text = remember(font) {
            runCatching { context.assets.open("licenses/${font.licenseFile}").bufferedReader().readText() }
                .getOrDefault("license text missing")
        }
        AlertDialog(
            onDismissRequest = { showing = null },
            title = { Text(font.name, style = Type.row) },
            text = {
                Text(text, style = Type.small, modifier = Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()))
            },
            confirmButton = { TextButton(onClick = { showing = null }) { Text("close", style = Type.row) } },
        )
    }
}
