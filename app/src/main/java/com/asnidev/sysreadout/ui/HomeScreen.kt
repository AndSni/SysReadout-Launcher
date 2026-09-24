package com.asnidev.sysreadout.ui

import android.text.format.DateFormat
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.asnidev.sysreadout.LauncherViewModel
import com.asnidev.sysreadout.Screen
import com.asnidev.sysreadout.apps.AppEntry
import com.asnidev.sysreadout.data.EntryStyle
import com.asnidev.sysreadout.data.HAlign
import com.asnidev.sysreadout.data.StyleElement
import com.asnidev.sysreadout.data.VAlign
import com.asnidev.sysreadout.system.LockService
import com.asnidev.sysreadout.system.SystemActions
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.delay
import java.util.Date
import kotlin.math.abs

private val EntryGap = 6.dp

private enum class Swipe { UP, DOWN, LEFT, RIGHT }

@Composable
fun HomeScreen(vm: LauncherViewModel, haze: HazeState) {
    val context = LocalContext.current
    val styled = LocalStyled.current
    val prefs by vm.prefs.collectAsState()
    val apps by vm.apps.collectAsState()
    val byKey = remember(apps) { apps.associateBy { it.key } }
    val entries = prefs.pinned.mapNotNull { byKey[it] }

    val horizontal = when (prefs.hAlign) {
        HAlign.START -> Alignment.Start
        HAlign.CENTER -> Alignment.CenterHorizontally
        HAlign.END -> Alignment.End
    }

    val currentPrefs by rememberUpdatedState(prefs)
    val onSwipe: (Swipe) -> Unit = { dir ->
        when (dir) {
            Swipe.UP -> vm.screen = Screen.DRAWER
            Swipe.DOWN -> SystemActions.expandNotifications(context)
            Swipe.LEFT -> currentPrefs.swipeLeft?.let(vm::launch)
            Swipe.RIGHT -> currentPrefs.swipeRight?.let(vm::launch)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onLongPress = { vm.screen = Screen.SETTINGS },
                    onDoubleTap = {
                        if (currentPrefs.doubleTapLock && !LockService.lock()) {
                            Toast.makeText(context, "enable the lock service in accessibility settings", Toast.LENGTH_SHORT).show()
                            SystemActions.openAccessibilitySettings(context)
                        }
                    },
                )
            }
            .pointerInput(Unit) {
                var total = Offset.Zero
                val threshold = 72.dp.toPx()
                detectDragGestures(
                    onDragStart = { total = Offset.Zero },
                    onDragEnd = {
                        val (dx, dy) = total
                        when {
                            abs(dy) > abs(dx) && dy < -threshold -> onSwipe(Swipe.UP)
                            abs(dy) > abs(dx) && dy > threshold -> onSwipe(Swipe.DOWN)
                            abs(dx) > abs(dy) && dx < -threshold -> onSwipe(Swipe.LEFT)
                            abs(dx) > abs(dy) && dx > threshold -> onSwipe(Swipe.RIGHT)
                        }
                    },
                ) { change, amount ->
                    change.consume()
                    total += amount
                }
            }
            .systemBarsPadding()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = horizontal,
    ) {
        if (prefs.showClock || prefs.showDate) ClockBlock(prefs.showClock, prefs.showDate, horizontal, prefs.entryStyle, haze, styled)

        val measurer = rememberTextMeasurer()
        val density = LocalDensity.current
        val rowHeight = remember(measurer, density, styled) {
            with(density) { measurer.measure("Ag", styled.menu).size.height.toDp() } + EntryPadV * 2 + EntryGap
        }

        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            // The menu takes only the free vertical space: that is the cap on entries.
            val capacity = ((maxHeight + EntryGap) / rowHeight).toInt().coerceAtLeast(1)
            LaunchedEffect(capacity) { vm.menuCapacity = capacity }

            Column(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(
                    EntryGap,
                    when (prefs.vAlign) {
                        VAlign.TOP -> Alignment.Top
                        VAlign.CENTER -> Alignment.CenterVertically
                        VAlign.BOTTOM -> Alignment.Bottom
                    },
                ),
                horizontalAlignment = horizontal,
            ) {
                if (entries.isEmpty()) {
                    val ink = styled.date.color
                    Text(
                        styled.text(StyleElement.DATE, "swipe up for apps · long-press one to pin it"),
                        style = styled.date,
                        color = styled.ink(prefs.entryStyle, ink),
                        modifier = Modifier
                            .backing(prefs.entryStyle, haze, styled, ink)
                            .padding(horizontal = EntryPadH, vertical = EntryPadV),
                    )
                }
                entries.take(capacity).forEachIndexed { i, entry -> HomeEntry(vm, entry, i, haze, styled) }
            }
        }
    }
}

@Composable
private fun HomeEntry(vm: LauncherViewModel, entry: AppEntry, index: Int, haze: HazeState, styled: Styled) {
    val context = LocalContext.current
    val prefs by vm.prefs.collectAsState()
    var menu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }

    Box {
        EntryChip(
            text = styled.prefix(index) + styled.text(StyleElement.MENU, vm.label(entry)),
            style = prefs.entryStyle,
            haze = haze,
            styled = styled,
            onClick = {
                if (!vm.launch(entry.key)) Toast.makeText(context, "can't open ${entry.label}", Toast.LENGTH_SHORT).show()
            },
            onLongClick = { menu = true },
        )
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            MenuItem("rename") { menu = false; renaming = true }
            MenuItem("move up") { menu = false; vm.move(entry.key, -1) }
            MenuItem("move down") { menu = false; vm.move(entry.key, 1) }
            MenuItem("remove from home") { menu = false; vm.unpin(entry.key) }
            MenuItem("app info") { menu = false; vm.repo.openInfo(entry.key) }
        }
    }
    if (renaming) {
        RenameDialog(entry.label, vm.label(entry), onDismiss = { renaming = false }) {
            vm.rename(entry.key, it)
            renaming = false
        }
    }
}

@Composable
private fun ClockBlock(
    showClock: Boolean,
    showDate: Boolean,
    align: Alignment.Horizontal,
    style: EntryStyle,
    haze: HazeState,
    styled: Styled,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    // Restarted on every return to the foreground, so the clock is never stale after sleep.
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                now = System.currentTimeMillis()
                delay(60_000 - now % 60_000)
            }
        }
    }

    val quiet = remember { MutableInteractionSource() }
    Column(horizontalAlignment = align) {
        if (showClock) {
            val pattern = if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm"
            Text(
                styled.text(StyleElement.CLOCK, DateFormat.format(pattern, Date(now)).toString()),
                style = styled.clock,
                color = styled.ink(style, styled.clock.color),
                modifier = Modifier
                    .backing(style, haze, styled, styled.clock.color)
                    .clickable(quiet, null) { SystemActions.openAlarms(context) }
                    .padding(horizontal = EntryPadH),
            )
        }
        if (showDate) {
            Text(
                styled.text(StyleElement.DATE, DateFormat.format("EEE d MMM", Date(now)).toString()),
                style = styled.date,
                color = styled.ink(style, styled.date.color),
                modifier = Modifier
                    .padding(top = 4.dp)
                    .backing(style, haze, styled, styled.date.color)
                    .clickable(quiet, null) { SystemActions.openCalendar(context) }
                    .padding(horizontal = EntryPadH, vertical = EntryPadV),
            )
        }
    }
}

@Composable
fun MenuItem(text: String, onClick: () -> Unit) =
    DropdownMenuItem(text = { Text(text, style = Type.row) }, onClick = onClick)
