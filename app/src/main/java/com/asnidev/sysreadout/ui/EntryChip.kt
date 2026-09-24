package com.asnidev.sysreadout.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.asnidev.sysreadout.data.EntryStyle
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeChild

val EntryPadH = 10.dp
val EntryPadV = 4.dp
private val EntryShape = RoundedCornerShape(2.dp)

/**
 * The backing that keeps a home-screen object (entry, clock, date) legible
 * over the log. [ink] is the object's text colour, used as the fill when inverted.
 */
fun Modifier.backing(style: EntryStyle, haze: HazeState, styled: Styled, ink: Color): Modifier =
    clip(EntryShape).let {
        when (style) {
            EntryStyle.FROSTED -> it.hazeChild(
                haze,
                HazeStyle(
                    backgroundColor = styled.background,
                    tint = HazeTint(styled.backing.copy(alpha = 0.45f)),
                    blurRadius = 14.dp,
                    noiseFactor = 0.06f,
                ),
            )
            EntryStyle.HIGHLIGHT -> it.background(styled.backing.copy(alpha = 0.88f))
            EntryStyle.INVERTED -> it.background(ink)
            EntryStyle.BARE -> it
        }
    }

/** One home-menu entry. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EntryChip(
    text: String,
    style: EntryStyle,
    haze: HazeState,
    styled: Styled,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ink = styled.menu.color
    Text(
        text = text,
        style = styled.menu,
        color = styled.ink(style, ink),
        maxLines = 1,
        modifier = modifier
            .backing(style, haze, styled, ink)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = EntryPadH, vertical = EntryPadV),
    )
}
