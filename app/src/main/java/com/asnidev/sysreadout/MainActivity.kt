package com.asnidev.sysreadout

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import com.asnidev.sysreadout.ui.Drawer
import com.asnidev.sysreadout.ui.HomeScreen
import com.asnidev.sysreadout.ui.LogBackdrop
import com.asnidev.sysreadout.data.LogLayout
import com.asnidev.sysreadout.data.Presets
import com.asnidev.sysreadout.ui.LocalStyled
import com.asnidev.sysreadout.ui.crt
import com.asnidev.sysreadout.ui.SysReadoutTheme
import com.asnidev.sysreadout.ui.rememberStyled
import com.asnidev.sysreadout.ui.settings.Page
import com.asnidev.sysreadout.ui.settings.SettingsScreen
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze

class MainActivity : ComponentActivity() {

    private val vm: LauncherViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        preview(intent)
        setContent { SysReadoutTheme { LauncherRoot(vm, this) } }
    }

    /**
     * Debug aids: look at a preset (e.g. its CRT effects) or a set of log rows without
     * touching saved settings, open a screen (home/drawer/settings) or a settings page,
     * draw the lock-screen snapshot to cache/snapshot.png instead of the wallpaper, or
     * crash on purpose (`--ez crash true`, twice in a row to try safe mode).
     */
    private fun preview(intent: Intent?) {
        if (!BuildConfig.DEBUG) return
        if (intent?.getBooleanExtra("crash", false) == true) error("crash requested over adb")
        intent?.getStringExtra("preview")?.let { name ->
            vm.previewTheme = Presets.builtIn.firstOrNull { it.name == name }?.theme
        }
        intent?.getStringExtra("rows")?.let { rows ->
            vm.engine.rowsOverride = if (rows == "none") null else rows.split(',').map { it.trim() }
        }
        if (intent?.getBooleanExtra("snapshot", false) == true) vm.snapshotToFile()
        intent?.getStringExtra("layout")?.let { name ->
            LogLayout.entries.firstOrNull { it.name.equals(name, ignoreCase = true) }.let {
                vm.previewLayout = it
                vm.engine.layoutOverride = it
            }
        }
        intent?.getStringExtra("feed")?.let { edge ->
            val top = when (edge) { "top" -> true; "bottom" -> false; else -> null }
            vm.previewFeedTop = top
            vm.engine.feedTopOverride = top
        }
        intent?.getStringExtra("screen")?.let { name ->
            Screen.entries.firstOrNull { it.name.equals(name, ignoreCase = true) }?.let { vm.screen = it }
        }
        intent?.getStringExtra("page")?.let { name ->
            Page.byName(name)?.let {
                vm.requestedPage = it
                vm.screen = Screen.SETTINGS
            }
        }
    }

    /** Pressing Home while already home closes the drawer or settings. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        vm.screen = Screen.HOME
        preview(intent)
    }

    override fun onResume() {
        super.onResume()
        vm.repo.refresh()
    }
}

@Composable
private fun LauncherRoot(vm: LauncherViewModel, activity: ComponentActivity) {
    val prefs by vm.prefs.collectAsState()
    val saved by vm.theme.collectAsState()
    val safe by vm.safeMode.collectAsState()
    // Safe mode draws with the default look: an imported font or CRT shader may be what crashed.
    val theme = if (safe) Presets.default.theme else vm.previewTheme ?: saved
    val styled = rememberStyled(theme, vm.fontsVersion)
    val haze = remember { HazeState() }

    // Light backgrounds need dark status/navigation bar icons.
    LaunchedEffect(styled.background) {
        val transparent = android.graphics.Color.TRANSPARENT
        val bars = if (styled.background.luminance() > 0.5f) {
            SystemBarStyle.light(transparent, transparent)
        } else {
            SystemBarStyle.dark(transparent)
        }
        activity.enableEdgeToEdge(bars, bars)
    }

    // A launcher never backs out of itself; Back only closes overlays.
    BackHandler { vm.screen = Screen.HOME }

    CompositionLocalProvider(LocalStyled provides styled) {
        val crt = theme.crt
        Box(Modifier.fillMaxSize().background(styled.background)) {
            // With "menu too", one CRT layer covers log + menu; otherwise only the log.
            Box(Modifier.fillMaxSize().then(if (crt.menuToo) Modifier.crt(crt) else Modifier)) {
                Box(Modifier.fillMaxSize().haze(haze).background(styled.background)) {
                    Box(Modifier.fillMaxSize().then(if (crt.menuToo) Modifier else Modifier.crt(crt)).background(styled.background)) {
                        if (prefs.showLog) LogBackdrop(vm, Modifier.fillMaxSize())
                    }
                }
                AnimatedVisibility(vm.screen == Screen.HOME, enter = fadeIn(), exit = fadeOut()) {
                    HomeScreen(vm, haze)
                }
            }
            AnimatedVisibility(
                vm.screen == Screen.DRAWER,
                enter = fadeIn() + slideInVertically { it / 8 },
                exit = fadeOut() + slideOutVertically { it / 8 },
            ) {
                Drawer(vm)
            }
            AnimatedVisibility(vm.screen == Screen.SETTINGS, enter = fadeIn(), exit = fadeOut()) {
                SettingsScreen(vm)
            }
        }
    }
}
