package com.asnidev.sysreadout.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.asnidev.sysreadout.data.EntryStyle
import com.asnidev.sysreadout.data.StyleElement
import com.asnidev.sysreadout.data.TextSpec
import com.asnidev.sysreadout.data.Theme

/** A [Theme] with its fonts resolved into ready-to-use text styles. */
class Styled(val theme: Theme, context: Context) {
    val background = Color(theme.background)
    val accent = Color(theme.accent)
    val backing = Color(theme.backing)

    private val homeGlow = if (theme.crt.menuToo) theme.crt.glow else 0f

    val clock = theme.clock.toStyle(context, glow = homeGlow)
    val date = theme.date.toStyle(context, glow = homeGlow)
    val menu = theme.menu.toStyle(context, glow = homeGlow)
    val drawer = theme.drawer.toStyle(context)
    val log = theme.log.toStyle(context, lineHeight = 1.25f, glow = theme.crt.glow)
    val banner = theme.banner.toStyle(context, lineHeight = 1.25f, glow = theme.crt.glow)

    fun text(e: StyleElement, s: String): String = theme.spec(e).case.apply(s)

    fun prefix(index: Int): String = theme.prefix.replace("{n}", "${index + 1}")

    /** Text colour on a backing: inverted entries swap text and background. */
    fun ink(style: EntryStyle, color: Color): Color = if (style == EntryStyle.INVERTED) background else color
}

/** [glow] 0..1 adds a phosphor bloom: a blurred shadow in the text's own colour. */
fun TextSpec.toStyle(context: Context, lineHeight: Float? = null, glow: Float = 0f): TextStyle = TextStyle(
    fontFamily = Fonts.family(context, font),
    fontSize = size.sp,
    fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
    letterSpacing = spacing.em,
    color = Color(color),
    lineHeight = lineHeight?.let { (size * it).sp } ?: TextUnit.Unspecified,
    shadow = if (glow <= 0f) null else Shadow(
        color = Color(color).copy(alpha = 0.5f + 0.4f * glow),
        offset = Offset.Zero,
        blurRadius = size * context.resources.displayMetrics.density * 0.6f * glow,
    ),
)

val LocalStyled = staticCompositionLocalOf<Styled> { error("LocalStyled not provided") }

@Composable
fun rememberStyled(theme: Theme, fontsVersion: Int): Styled {
    val context = LocalContext.current
    return remember(theme, fontsVersion) { Styled(theme, context.applicationContext) }
}
