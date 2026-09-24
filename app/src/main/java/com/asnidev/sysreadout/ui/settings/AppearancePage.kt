package com.asnidev.sysreadout.ui.settings

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.asnidev.sysreadout.LauncherViewModel
import com.asnidev.sysreadout.data.Preset
import com.asnidev.sysreadout.data.Presets
import com.asnidev.sysreadout.data.StyleElement
import com.asnidev.sysreadout.data.Theme
import com.asnidev.sysreadout.ui.Fonts
import com.asnidev.sysreadout.ui.Palette
import com.asnidev.sysreadout.ui.Type
import com.asnidev.sysreadout.ui.toStyle

private enum class ThemeColor(val label: String) { BACKGROUND("background"), ACCENT("accent"), BACKING("backing") }

@Composable
fun AppearancePage(vm: LauncherViewModel, open: (Page) -> Unit) {
    val context = LocalContext.current
    val theme by vm.theme.collectAsState()
    val userPresets by vm.userPresets.collectAsState()
    var editing by remember { mutableStateOf<ThemeColor?>(null) }
    var saving by remember { mutableStateOf(false) }

    Section("presets")
    Note("tap to apply. your own presets: long-press to delete.")
    Presets.builtIn.forEach { preset -> PresetRow(preset, current = preset.theme == theme) { vm.updateTheme { preset.theme } } }
    userPresets.forEach { preset ->
        PresetRow(preset, current = preset.theme == theme, onLongClick = { vm.deletePreset(preset.name) }) {
            vm.updateTheme { preset.theme }
        }
    }
    Link("save current as preset…", "+") { saving = true }

    Section("screen")
    Link("crt effects", if (theme.crt.isOff) "off ›" else "on ›") { open(Page.Crt) }

    Section("colours")
    ColorRow("background", theme.background) { editing = ThemeColor.BACKGROUND }
    ColorRow("accent", theme.accent) { editing = ThemeColor.ACCENT }
    ColorRow("backing", theme.backing) { editing = ThemeColor.BACKING }
    Note("backing tints the frosted / highlight box behind home-screen text.")

    Section("text")
    StyleElement.entries.forEach { e ->
        val spec = theme.spec(e)
        Link(e.title, "${Fonts.name(context, spec.font)} · ${fmt(spec.size)}sp ›") { open(Page.Element(e)) }
    }

    Section("menu entries")
    Cycle(
        "prefix",
        theme.prefix,
        Presets.prefixes.let { if (theme.prefix in it) it else it + theme.prefix },
        show = { if (it.isEmpty()) "none" else "\"${it.replace("{n}", "1")}\"" },
    ) { v -> vm.updateTheme { it.copy(prefix = v) } }

    editing?.let { which ->
        val initial = when (which) {
            ThemeColor.BACKGROUND -> theme.background
            ThemeColor.ACCENT -> theme.accent
            ThemeColor.BACKING -> theme.backing
        }
        ColorDialog(which.label, initial, onDismiss = { editing = null }) { c ->
            vm.updateTheme {
                when (which) {
                    ThemeColor.BACKGROUND -> it.copy(background = c)
                    ThemeColor.ACCENT -> it.copy(accent = c)
                    ThemeColor.BACKING -> it.copy(backing = c)
                }
            }
            editing = null
        }
    }

    if (saving) {
        NameDialog(onDismiss = { saving = false }) { name ->
            vm.savePreset(name)
            saving = false
        }
    }
}

/** The preset's name drawn in its own menu font and colours. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PresetRow(preset: Preset, current: Boolean, onLongClick: (() -> Unit)? = null, onClick: () -> Unit) {
    val context = LocalContext.current
    val t = preset.theme
    val sample = remember(t) { t.menu.toStyle(context).copy(fontSize = t.menu.size.coerceAtMost(22f).sp) }
    Row(
        Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(if (current) "* " else "  ", style = Type.row, color = Palette.log)
        Box(Modifier.weight(1f).background(Color(t.background)).padding(horizontal = 10.dp, vertical = 6.dp)) {
            Text(
                t.prefix.replace("{n}", "1") + t.menu.case.apply(preset.name),
                style = sample,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(Modifier.width(8.dp))
        Swatch(t.accent, 14)
    }
}

@Composable
private fun NameDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("preset name", style = Type.row) },
        text = { OutlinedTextField(name, { name = it }, singleLine = true, textStyle = Type.row) },
        confirmButton = {
            TextButton(onClick = { if (name.isNotBlank()) onConfirm(name) }) { Text("save", style = Type.row) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("cancel", style = Type.row) } },
    )
}

internal fun fmt(f: Float): String = if (f % 1f == 0f) f.toInt().toString() else "%.1f".format(f)

