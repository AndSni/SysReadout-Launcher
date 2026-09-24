package com.asnidev.sysreadout.ui.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.asnidev.sysreadout.LauncherViewModel
import com.asnidev.sysreadout.data.StyleElement
import com.asnidev.sysreadout.data.TextCase
import com.asnidev.sysreadout.data.TextSpec
import com.asnidev.sysreadout.data.Theme
import com.asnidev.sysreadout.ui.Fonts
import com.asnidev.sysreadout.ui.MenuItem
import com.asnidev.sysreadout.ui.Palette
import com.asnidev.sysreadout.ui.Type
import com.asnidev.sysreadout.ui.toStyle
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private fun sample(e: StyleElement) = when (e) {
    StyleElement.CLOCK -> "12:34"
    StyleElement.DATE -> "Thu 24 Sep"
    StyleElement.MENU -> "Firefox"
    StyleElement.DRAWER -> "Signal"
    StyleElement.LOG -> "mem   1.2G/2.4G avail  51% used"
    StyleElement.BANNER -> "Unified Operating System"
}

@Composable
fun ElementPage(vm: LauncherViewModel, element: StyleElement) {
    val context = LocalContext.current
    val theme by vm.theme.collectAsState()
    val spec = theme.spec(element)
    var pickingFont by remember { mutableStateOf(false) }
    var pickingColor by remember { mutableStateOf(false) }

    fun set(transform: (TextSpec) -> TextSpec) = vm.updateTheme { it.with(element, transform(it.spec(element))) }

    // Live preview on the theme's own background.
    val lined = element == StyleElement.LOG || element == StyleElement.BANNER
    val preview = remember(spec, vm.fontsVersion) { spec.toStyle(context, lineHeight = if (lined) 1.25f else null) }
    Box(
        Modifier.fillMaxWidth().padding(vertical = 12.dp).background(Color(theme.background)).padding(12.dp),
    ) {
        val prefix = if (element == StyleElement.MENU) theme.prefix.replace("{n}", "1") else ""
        Text(prefix + spec.case.apply(sample(element)), style = preview, maxLines = 2, overflow = TextOverflow.Clip)
    }

    Section(element.title)
    Link("font", "${Fonts.name(context, spec.font)} ›") { pickingFont = true }
    val step = if (spec.size < 14f) 0.5f else 1f
    Stepper("size", "${fmt(spec.size)}sp", { set { it.copy(size = (it.size - step).coerceAtLeast(6f)) } }) {
        set { it.copy(size = (it.size + step).coerceAtMost(160f)) }
    }
    Toggle("bold", spec.bold) { v -> set { it.copy(bold = v) } }
    Stepper(
        "letter spacing",
        "%.2fem".format(spec.spacing),
        { set { it.copy(spacing = ((it.spacing - 0.01f) * 100).roundToInt().coerceAtLeast(-10) / 100f) } },
    ) { set { it.copy(spacing = ((it.spacing + 0.01f) * 100).roundToInt().coerceAtMost(50) / 100f) } }
    Cycle("case", spec.case, TextCase.entries, show = { it.name.lowercase().replace('_', '-') }) { v -> set { it.copy(case = v) } }
    ColorRow("colour", spec.color) { pickingColor = true }

    if (pickingFont) FontDialog(vm, element, spec.font) { pickingFont = false }
    if (pickingColor) {
        ColorDialog("${element.title} colour", spec.color, onDismiss = { pickingColor = false }) { c ->
            set { it.copy(color = c) }
            pickingColor = false
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FontDialog(vm: LauncherViewModel, element: StyleElement, current: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val fonts = remember(vm.fontsVersion) { Fonts.all(context) }
    var deleting by remember { mutableStateOf<String?>(null) }

    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val ok = vm.importFont(uri, element)
                Toast.makeText(context, if (ok) "font imported" else "that file isn't a usable font", Toast.LENGTH_SHORT).show()
                if (ok) onDismiss()
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("font · ${element.title}", style = Type.row) },
        confirmButton = {
            TextButton(onClick = { importer.launch(arrayOf("*/*")) }) { Text("import .ttf / .otf", style = Type.row) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("close", style = Type.row) } },
        text = {
            LazyColumn(Modifier.heightIn(max = 460.dp)) {
                items(fonts, key = { it.id }) { font ->
                    val imported = font.id.startsWith(Fonts.USER_PREFIX)
                    Box {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    onClick = {
                                        vm.updateTheme { it.with(element, it.spec(element).copy(font = font.id)) }
                                        onDismiss()
                                    },
                                    onLongClick = if (imported) ({ deleting = font.id }) else null,
                                )
                                .padding(vertical = 8.dp),
                        ) {
                            Text(
                                (if (font.id == current) "* " else "") + font.name,
                                style = TextStyle(fontFamily = Fonts.family(context, font.id), fontSize = 18.sp, color = Palette.fg),
                                maxLines = 1,
                            )
                            Text(
                                if (imported) "imported · long-press to delete" else font.license ?: "built into Android",
                                style = Type.small,
                            )
                        }
                        DropdownMenu(expanded = deleting == font.id, onDismissRequest = { deleting = null }) {
                            MenuItem("delete ${font.name}") {
                                deleting = null
                                vm.deleteFont(font.id)
                            }
                        }
                    }
                }
            }
        },
    )
}

private val SWATCHES = listOf(
    0xFF1AFF80, 0xFF33FF33, 0xFF3DDC84, 0xFF12B85C, 0xFFB4FF39, 0xFF00F0FF,
    0xFFFFB000, 0xFFFFCC00, 0xFFFF5555, 0xFFFF3EA5, 0xFF7A5CFF, 0xFF5C9DFF,
    0xFFFFFFFF, 0xFFE8ECE9, 0xFFE8F0FF, 0xFF8A938D, 0xFF4A4A4A, 0xFF1E1E1E,
    0xFF000000, 0xFF050807, 0xFF020E05, 0xFF0E0800, 0xFF0A0C10, 0xFF0B0221,
)

@Composable
fun ColorDialog(title: String, initial: Long, onDismiss: () -> Unit, onPick: (Long) -> Unit) {
    var hex by remember { mutableStateOf(Theme.hex(initial)) }
    val parsed = Theme.parseColor(hex)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = Type.row) },
        text = {
            Column {
                SWATCHES.chunked(6).forEach { row ->
                    Row {
                        row.forEach { c ->
                            Swatch(
                                c,
                                36,
                                selected = c == parsed,
                                modifier = Modifier.padding(3.dp).clickable { hex = Theme.hex(c) },
                            )
                        }
                    }
                }
                OutlinedTextField(
                    hex,
                    { hex = it },
                    singleLine = true,
                    textStyle = Type.row,
                    isError = parsed == null,
                    label = { Text("#RRGGBB or #AARRGGBB", style = Type.small) },
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { parsed?.let(onPick) }, enabled = parsed != null) { Text("ok", style = Type.row) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("cancel", style = Type.row) } },
    )
}
