package com.asnidev.sysreadout.ui.settings

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.asnidev.sysreadout.data.Theme
import com.asnidev.sysreadout.ui.Palette
import com.asnidev.sysreadout.ui.Type

// Settings chrome keeps a fixed look so no theme can make it unreadable.

@Composable
fun Section(title: String) =
    Text("# $title", style = Type.row, color = Palette.log, modifier = Modifier.padding(top = 20.dp, bottom = 4.dp))

@Composable
fun Note(text: String) =
    Text(text, style = Type.small, color = Palette.dim, modifier = Modifier.padding(bottom = 6.dp))

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SettingRow(
    label: String,
    value: String,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = Type.row, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(value, style = Type.row, color = Palette.log, maxLines = 1)
    }
}

@Composable
fun Toggle(label: String, value: Boolean, onChange: (Boolean) -> Unit) =
    SettingRow(label, if (value) "[x]" else "[ ]") { onChange(!value) }

/** Tapping cycles through [options]. */
@Composable
fun <T> Cycle(label: String, value: T, options: List<T>, show: (T) -> String = { it.toString().lowercase() }, onChange: (T) -> Unit) =
    SettingRow(label, show(value)) { onChange(options[(options.indexOf(value) + 1) % options.size]) }

@Composable
fun Link(label: String, value: String = "›", onClick: () -> Unit) = SettingRow(label, value, onClick = onClick)

/** label ....... value  [−] [+] */
@Composable
fun Stepper(label: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = Type.row, modifier = Modifier.weight(1f))
        Text(value, style = Type.row, color = Palette.log)
        Text(" [−] ", style = Type.row, modifier = Modifier.clickable(onClick = onMinus).padding(vertical = 6.dp))
        Text("[+]", style = Type.row, modifier = Modifier.clickable(onClick = onPlus).padding(vertical = 6.dp))
    }
}

@Composable
fun ColorRow(label: String, color: Long, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(label, style = Type.row, modifier = Modifier.weight(1f))
        Swatch(color, 18)
        Text(Theme.hex(color), style = Type.row, color = Palette.log)
    }
}

@Composable
fun Swatch(color: Long, sizeDp: Int, modifier: Modifier = Modifier, selected: Boolean = false) {
    Box(
        modifier
            .size(sizeDp.dp)
            .background(Color(color))
            .border(if (selected) 2.dp else 1.dp, if (selected) Palette.fg else Palette.dim.copy(alpha = 0.5f)),
    )
}

@Composable
fun Header(path: List<String>, onBack: (() -> Unit)?) {
    Column(Modifier.padding(bottom = 4.dp)) {
        Text(
            "sysreadout --config" + path.joinToString("") { " › $it" },
            style = Type.entry,
            color = Palette.log,
            modifier = if (onBack != null) Modifier.clickable(onClick = onBack) else Modifier,
        )
        if (onBack != null) Note("tap here or press back to go up")
    }
}
