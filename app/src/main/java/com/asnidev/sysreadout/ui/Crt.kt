package com.asnidev.sysreadout.ui

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.asnidev.sysreadout.data.Crt
import kotlinx.coroutines.delay

/**
 * Runs a layer through the CRT effects. Android 13+ gets the full shader;
 * older versions get scanlines and vignette drawn on top. Glow is a text
 * shadow (see [Styled]), so it works everywhere.
 */
@Composable
fun Modifier.crt(c: Crt): Modifier {
    if (c.isOff) return this
    val density = LocalDensity.current
    val spacing = with(density) { SCANLINE_SPACING.toPx() }
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return crtFallback(c, spacing)

    // A shader that fails to compile on some GPU driver must not take the launcher down.
    val shader = remember {
        runCatching { RuntimeShader(CRT_SHADER) }.onFailure { Log.e("Crt", "CRT shader unavailable", it) }.getOrNull()
    } ?: return crtFallback(c, spacing)
    var time by remember { mutableFloatStateOf(0f) }
    if (c.animated) {
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        LaunchedEffect(lifecycle) {
            // Flicker and grain need redraws; ~20 fps is plenty and only while visible.
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    withFrameMillis { time = (it % 100_000L) / 1000f }
                    delay(50)
                }
            }
        }
    }
    return graphicsLayer { renderEffect = crtEffect(shader, c, size.width, size.height, spacing, time) }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun crtEffect(shader: RuntimeShader, c: Crt, w: Float, h: Float, spacing: Float, time: Float) =
    shader.run {
        setFloatUniform("size", w, h)
        setFloatUniform("time", time)
        setFloatUniform("curvature", c.curvature)
        setFloatUniform("scanlines", c.scanlines)
        setFloatUniform("spacing", spacing)
        setFloatUniform("vignette", c.vignette)
        setFloatUniform("fringe", c.fringe * 3f)
        setFloatUniform("flicker", c.flicker)
        setFloatUniform("grain", c.grain)
        RenderEffect.createRuntimeShaderEffect(this, "content").asComposeRenderEffect()
    }

private fun Modifier.crtFallback(c: Crt, spacing: Float) = drawWithContent {
    drawContent()
    if (c.scanlines > 0f) {
        val line = Color.Black.copy(alpha = 0.45f * c.scanlines)
        var y = 0f
        while (y < size.height) {
            drawLine(line, Offset(0f, y), Offset(size.width, y), strokeWidth = spacing / 2f)
            y += spacing
        }
    }
    if (c.vignette > 0f) {
        drawRect(
            Brush.radialGradient(
                0.45f to Color.Transparent,
                1f to Color.Black.copy(alpha = 0.75f * c.vignette),
                center = center,
                radius = size.maxDimension * 0.75f,
            ),
        )
    }
}

private val SCANLINE_SPACING = 3.dp

/** AGSL. `content` is the layer being drawn; all effect strengths are 0..1. */
private const val CRT_SHADER = """
uniform shader content;
uniform float2 size;
uniform float time;
uniform float curvature;
uniform float scanlines;
uniform float spacing;
uniform float vignette;
uniform float fringe;
uniform float flicker;
uniform float grain;

half4 main(float2 coord) {
    float2 uv = coord / size;
    float2 c = uv - 0.5;
    // Barrel distortion: the further from the centre, the more a point is pushed out.
    // Normalised so the middle of each edge stays put: only the corners round off.
    float k = curvature * 0.6;
    float2 warped = 0.5 + c * (1.0 + k * dot(c, c)) / (1.0 + k * 0.25);
    if (warped.x < 0.0 || warped.x > 1.0 || warped.y < 0.0 || warped.y > 1.0) {
        return half4(0.0);
    }
    float2 p = warped * size;
    half4 col = content.eval(p);
    if (fringe > 0.0) {
        col.r = content.eval(p + float2(fringe, 0.0)).r;
        col.b = content.eval(p - float2(fringe, 0.0)).b;
    }
    // Scanlines: a soft dark band every `spacing` pixels.
    float band = 0.5 + 0.5 * cos(p.y * 6.2831853 / spacing);
    col.rgb *= 1.0 - scanlines * 0.55 * band;
    // Vignette: darker towards the edges, more so on a curved screen.
    float v = smoothstep(0.9, 0.3, length(c) * (1.0 + 0.3 * curvature));
    col.rgb *= mix(1.0, v, vignette);
    // Flicker and grain (only animate when the app redraws for them).
    col.rgb *= 1.0 - flicker * 0.08 * (0.5 + 0.5 * sin(time * 47.0));
    float n = fract(sin(dot(coord + time * 61.0, float2(12.9898, 78.233))) * 43758.5453);
    col.rgb += half3((n - 0.5) * grain * 0.14) * col.a;
    return col;
}
"""
