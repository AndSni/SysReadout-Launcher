package com.asnidev.sysreadout.ui

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.asnidev.sysreadout.LauncherViewModel
import com.asnidev.sysreadout.apps.AppEntry
import com.asnidev.sysreadout.data.StyleElement
import java.text.Normalizer

private fun normalize(s: String) =
    Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").lowercase()

/** Prefix matches first, then word-initial matches ("gm" → Google Maps), then substring. */
private fun rank(label: String, q: String): Int? {
    val l = normalize(label)
    return when {
        l.startsWith(q) -> 0
        l.split(' ', '-', '.').filter { it.isNotEmpty() }.joinToString("") { it.take(1) }.startsWith(q) -> 1
        l.contains(q) -> 2
        else -> null
    }
}

@Composable
fun Drawer(vm: LauncherViewModel) {
    val context = LocalContext.current
    val styled = LocalStyled.current
    val prefs by vm.prefs.collectAsState()
    val apps by vm.apps.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }

    val q = normalize(query.trim())
    val visible = apps.filter { it.key !in prefs.hidden }
    val results = if (q.isEmpty()) visible else visible
        .mapNotNull { app -> rank(vm.label(app), q)?.let { app to it } }
        .sortedBy { it.second }
        .map { it.first }

    val open: (AppEntry) -> Unit = { app ->
        if (!vm.launch(app.key)) Toast.makeText(context, "can't open ${app.label}", Toast.LENGTH_SHORT).show()
    }

    LaunchedEffect(Unit) { if (prefs.autoKeyboard) focus.requestFocus() }
    LaunchedEffect(q, results.size) {
        if (prefs.autoLaunch && q.isNotEmpty() && results.size == 1) open(results.first())
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(styled.background.copy(alpha = 0.86f))
            .systemBarsPadding()
            .imePadding()
            .padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
        BasicTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            textStyle = styled.drawer,
            cursorBrush = SolidColor(styled.accent),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { results.firstOrNull()?.let(open) }),
            modifier = Modifier.fillMaxWidth().focusRequester(focus).padding(bottom = 12.dp),
            decorationBox = { inner ->
                Row {
                    Text("> ", style = styled.drawer, color = styled.accent)
                    Box {
                        if (query.isEmpty()) {
                            Text(
                                styled.text(StyleElement.DRAWER, "search"),
                                style = styled.drawer,
                                color = styled.drawer.color.copy(alpha = 0.45f),
                            )
                        }
                        inner()
                    }
                }
            },
        )
        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            items(results, key = { it.key.encode() }) { app -> DrawerRow(vm, app, styled, open) }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DrawerRow(vm: LauncherViewModel, app: AppEntry, styled: Styled, open: (AppEntry) -> Unit) {
    val context = LocalContext.current
    val prefs by vm.prefs.collectAsState()
    var menu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    val pinned = app.key in prefs.pinned

    Box {
        Text(
            styled.text(StyleElement.DRAWER, vm.label(app)) + if (app.isWork) " [w]" else "",
            style = styled.drawer,
            maxLines = 1,
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = { open(app) }, onLongClick = { menu = true })
                .padding(vertical = 7.dp),
        )
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            if (pinned) {
                MenuItem("remove from home") { menu = false; vm.unpin(app.key) }
            } else {
                MenuItem("pin to home") {
                    menu = false
                    if (!vm.pin(app.key)) Toast.makeText(context, "no vertical space left on home", Toast.LENGTH_SHORT).show()
                }
            }
            MenuItem("rename") { menu = false; renaming = true }
            MenuItem("hide") { menu = false; vm.hide(app.key) }
            MenuItem("app info") { menu = false; vm.repo.openInfo(app.key) }
            MenuItem("uninstall") { menu = false; vm.repo.uninstall(app.key) }
        }
    }
    if (renaming) {
        RenameDialog(app.label, vm.label(app), onDismiss = { renaming = false }) {
            vm.rename(app.key, it)
            renaming = false
        }
    }
}
