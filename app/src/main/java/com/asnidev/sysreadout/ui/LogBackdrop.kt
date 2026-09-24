package com.asnidev.sysreadout.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.asnidev.sysreadout.LauncherViewModel
import com.asnidev.sysreadout.data.HAlign
import com.asnidev.sysreadout.data.LogLayout
import com.asnidev.sysreadout.data.StyleElement
import com.asnidev.sysreadout.log.FeedItem
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val STAMP = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault())

/**
 * The log behind the home screen: the banner, then either the classic layout
 * (pinned rows, monitor tables, event stream) or the feed. The engine only
 * runs while the launcher is visible.
 */
@Composable
fun LogBackdrop(vm: LauncherViewModel, modifier: Modifier = Modifier) {
    val styled = LocalStyled.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val prefs by vm.prefs.collectAsState()
    val frame by vm.engine.frame.collectAsState()

    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { vm.engine.run() }
    }

    val log = styled.log
    val dim = log.color.copy(alpha = 0.55f)
    val layout = vm.previewLayout ?: prefs.logLayout
    val newestAtTop = vm.previewFeedTop ?: prefs.feedNewestAtTop
    // A curved CRT pushes the corners outwards; keep the text clear of them.
    val bend = styled.theme.crt.curvature
    Column(modifier.systemBarsPadding().padding(horizontal = 12.dp + 24.dp * bend, vertical = 8.dp + 48.dp * bend)) {
        if (frame.banner.isNotEmpty()) {
            val align = when (prefs.bannerAlign) {
                HAlign.START -> TextAlign.Start
                HAlign.CENTER -> TextAlign.Center
                HAlign.END -> TextAlign.End
            }
            frame.banner.forEach { line ->
                Text(
                    styled.text(StyleElement.BANNER, line),
                    style = styled.banner,
                    textAlign = align,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Clip,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(10.dp))
        }
        if (layout == LogLayout.FEED) {
            Feed(vm, frame.feed, newestAtTop, prefs.streamTimestamps, log)
            return@Column
        }
        frame.pinned.forEach { Line(it, log) }
        frame.tables.forEach { table ->
            Line("── ${table.title} " + "─".repeat(60), log, dim)
            if (table.header.isNotEmpty()) Line(table.header, log, dim)
            table.rows.forEach { Line(it, log) }
        }
        if (prefs.showStream) {
            if (frame.pinned.isNotEmpty() || frame.tables.isNotEmpty()) Line("─".repeat(80), log, dim)
            // Newest line at the bottom; older lines scroll off the top.
            LazyColumn(Modifier.fillMaxWidth().fillMaxSize(), reverseLayout = true, userScrollEnabled = false) {
                itemsIndexed(frame.stream.asReversed()) { i, line ->
                    val text = if (prefs.streamTimestamps) "${STAMP.format(Instant.ofEpochMilli(line.time))} ${line.text}" else line.text
                    Line(text, log, log.color.copy(alpha = if (i == 0) 1f else 0.6f))
                }
            }
        }
    }
}

/**
 * The feed layout: one list, no headers. Index 0 is the newest row, shown at
 * the top or the bottom. Tells the engine how many lines fit, which is where
 * rows fall off.
 */
@Composable
private fun ColumnScope.Feed(vm: LauncherViewModel, items: List<FeedItem>, newestAtTop: Boolean, stamps: Boolean, log: TextStyle) {
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
        val lineHeight = with(density) { (if (log.lineHeight.isSp) log.lineHeight else log.fontSize * 1.25f).toDp() }
        val fits = (maxHeight / lineHeight).toInt().coerceAtLeast(1)
        LaunchedEffect(fits) { vm.engine.feedCapacity = fits }
        Column(Modifier.fillMaxSize(), verticalArrangement = if (newestAtTop) Arrangement.Top else Arrangement.Bottom) {
            val shown = items.take(fits)
            (if (newestAtTop) shown else shown.asReversed()).forEach { item ->
                val text = if (stamps && item.time != null) "${STAMP.format(Instant.ofEpochMilli(item.time))} ${item.text}" else item.text
                Line(text, log)
            }
        }
    }
}

@Composable
private fun Line(text: String, style: TextStyle, color: Color = style.color) = Text(
    LocalStyled.current.text(StyleElement.LOG, text),
    style = style,
    color = color,
    maxLines = 1,
    softWrap = false,
    overflow = TextOverflow.Clip,
)
