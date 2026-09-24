package com.asnidev.sysreadout.ui.settings

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.asnidev.sysreadout.LauncherViewModel
import com.asnidev.sysreadout.data.Crt
import kotlin.math.roundToInt

@Composable
fun CrtPage(vm: LauncherViewModel) {
    val theme by vm.theme.collectAsState()
    val c = theme.crt
    val modern = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    fun set(transform: (Crt) -> Crt) = vm.updateTheme { it.copy(crt = transform(it.crt)) }

    Section("crt effects")
    Note("an old monitor's look over the log. part of the theme, so presets carry it.")
    Strength("glow", c.glow) { v -> set { it.copy(glow = v) } }
    Strength("scanlines", c.scanlines) { v -> set { it.copy(scanlines = v) } }
    Strength("vignette", c.vignette) { v -> set { it.copy(vignette = v) } }
    Strength("curvature", c.curvature, enabled = modern) { v -> set { it.copy(curvature = v) } }
    Strength("colour fringe", c.fringe, enabled = modern) { v -> set { it.copy(fringe = v) } }
    if (!modern) Note("curvature and colour fringe need Android 13 or newer.")

    Section("animated")
    Note("these redraw the screen about 20 times a second while it's visible, which costs battery.")
    Strength("flicker", c.flicker) { v -> set { it.copy(flicker = v) } }
    Strength("grain", c.grain) { v -> set { it.copy(grain = v) } }

    Section("scope")
    Toggle("menu, clock and date too", c.menuToo) { v -> set { it.copy(menuToo = v) } }
    Link("switch all effects off", "reset") { set { Crt() } }
}

/** 0..100% in steps of 10. */
@Composable
private fun Strength(label: String, value: Float, enabled: Boolean = true, onChange: (Float) -> Unit) {
    val pct = (value * 100).roundToInt()
    if (!enabled) {
        SettingRow(label, "n/a") {}
        return
    }
    Stepper(
        label,
        if (pct == 0) "off" else "$pct%",
        { onChange(((pct - 10).coerceAtLeast(0)) / 100f) },
    ) { onChange(((pct + 10).coerceAtMost(100)) / 100f) }
}
