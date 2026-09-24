package com.asnidev.sysreadout.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.asnidev.sysreadout.apps.AppEntry

/** Blank input resets to the app's own label. */
@Composable
fun RenameDialog(original: String, current: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("rename", style = Type.row) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                textStyle = Type.row,
                placeholder = { Text(original, style = Type.row, color = Palette.dim) },
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(text) }) { Text("ok", style = Type.row) } },
        dismissButton = { TextButton(onClick = { onConfirm("") }) { Text("reset", style = Type.row) } },
    )
}

@Composable
fun AppPickerDialog(apps: List<AppEntry>, label: (AppEntry) -> String, onDismiss: () -> Unit, onPick: (AppEntry?) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = { TextButton(onClick = { onPick(null) }) { Text("none", style = Type.row) } },
        title = { Text("choose app", style = Type.row) },
        text = {
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                items(apps, key = { it.key.encode() }) { app ->
                    Text(
                        label(app) + if (app.isWork) " [w]" else "",
                        style = Type.row,
                        modifier = Modifier.fillMaxWidth().clickable { onPick(app) }.padding(vertical = 8.dp),
                    )
                }
            }
        },
    )
}
