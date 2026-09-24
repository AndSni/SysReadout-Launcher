package com.asnidev.sysreadout.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp

/** Fixed palette until Phase 2 makes colours and fonts user-configurable. */
object Palette {
    val bg = Color(0xFF050807)
    val fg = Color(0xFFE8ECE9)
    val dim = Color(0xFF8A938D)
    val log = Color(0xFF3DDC84)
}

val Mono = FontFamily.Monospace

object Type {
    val entry = TextStyle(fontFamily = Mono, fontSize = 20.sp, color = Palette.fg)
    val clock = TextStyle(fontFamily = Mono, fontSize = 48.sp, color = Palette.fg)
    val date = TextStyle(fontFamily = Mono, fontSize = 16.sp, color = Palette.dim)
    val log = TextStyle(fontFamily = Mono, fontSize = 11.sp, lineHeight = 14.sp, color = Palette.log)
    val row = TextStyle(fontFamily = Mono, fontSize = 16.sp, color = Palette.fg)
    val small = TextStyle(fontFamily = Mono, fontSize = 12.sp, color = Palette.dim)
}

@Composable
fun SysReadoutTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Palette.log,
            background = Palette.bg,
            surface = Color(0xFF0C120F),
            onSurface = Palette.fg,
        ),
        content = content,
    )
}
