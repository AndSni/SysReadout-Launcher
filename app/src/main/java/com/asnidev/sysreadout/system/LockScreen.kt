package com.asnidev.sysreadout.system

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.hardware.display.DisplayManager
import android.net.Uri
import android.util.Log
import android.view.Display
import com.asnidev.sysreadout.data.HAlign
import com.asnidev.sysreadout.data.LauncherPrefs
import com.asnidev.sysreadout.data.TextSpec
import com.asnidev.sysreadout.data.Theme
import com.asnidev.sysreadout.log.LogFrame
import com.asnidev.sysreadout.ui.Fonts

/**
 * The lock-screen wallpaper. Android lets an app set it separately from the
 * home screen (FLAG_LOCK); the home screen stays SysReadout's own drawing.
 */
object LockScreen {

    private const val TAG = "LockScreen"

    fun setImage(context: Context, uri: Uri): Boolean = runCatching {
        context.contentResolver.openInputStream(uri)?.use {
            WallpaperManager.getInstance(context).setStream(it, null, true, WallpaperManager.FLAG_LOCK)
        } != null
    }.onFailure { Log.w(TAG, "couldn't set the lock-screen image", it) }.getOrDefault(false)

    fun setSnapshot(context: Context, frame: LogFrame, theme: Theme, prefs: LauncherPrefs): Boolean = runCatching {
        val bitmap = render(context, frame, theme, prefs)
        WallpaperManager.getInstance(context).setBitmap(bitmap, null, true, WallpaperManager.FLAG_LOCK)
        bitmap.recycle()
        true
    }.onFailure { Log.w(TAG, "couldn't set the lock-screen snapshot", it) }.getOrDefault(false)

    /**
     * The log as the home screen shows it (banner, rows, tables, the newest
     * stream lines), with the static CRT effects: glow, scanlines, vignette, curvature.
     */
    fun render(context: Context, frame: LogFrame, theme: Theme, prefs: LauncherPrefs): Bitmap {
        val mode = context.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY).mode
        val w = mode.physicalWidth
        val h = mode.physicalHeight
        val dm = context.resources.displayMetrics
        val px = dm.density * context.resources.configuration.fontScale
        val crt = theme.crt

        val flat = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(flat)
        canvas.drawColor(theme.background.toInt())

        fun paint(spec: TextSpec) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Fonts.typeface(context, spec.font, spec.bold)
            textSize = spec.size * px
            letterSpacing = spec.spacing
            color = spec.color.toInt()
            if (crt.glow > 0f) setShadowLayer(textSize * 0.6f * crt.glow, 0f, 0f, spec.color.toInt())
        }

        val margin = 12 * dm.density
        var y = 56 * dm.density // below the status bar

        val banner = paint(theme.banner)
        frame.banner.forEach { raw ->
            val line = theme.banner.case.apply(raw)
            val width = banner.measureText(line)
            val x = when (prefs.bannerAlign) {
                HAlign.START -> margin
                HAlign.CENTER -> (w - width) / 2f
                HAlign.END -> w - margin - width
            }
            y += banner.fontSpacing
            canvas.drawText(line, x, y, banner)
        }
        if (frame.banner.isNotEmpty()) y += 10 * dm.density

        val log = paint(theme.log)
        val dim = paint(theme.log).apply { alpha = 140 }
        val step = theme.log.size * px * 1.25f
        fun line(text: String, p: Paint = log) {
            y += step
            canvas.drawText(theme.log.case.apply(text), margin, y, p)
        }
        frame.pinned.forEach { line(it) }
        frame.tables.forEach { t ->
            line("── ${t.title} " + "─".repeat(60), dim)
            if (t.header.isNotEmpty()) line(t.header, dim)
            t.rows.forEach { line(it) }
        }
        if (prefs.showStream && frame.stream.isNotEmpty()) {
            line("─".repeat(80), dim)
            // Newest lines, as many as fit above the bottom edge.
            val room = ((h - margin * 4 - y) / step).toInt().coerceAtLeast(0)
            frame.stream.takeLast(room).forEach { line(it.text) }
        }

        if (crt.scanlines > 0f) {
            val spacing = 3 * dm.density
            val lines = Paint().apply {
                color = Color.BLACK
                alpha = (115 * crt.scanlines).toInt()
                strokeWidth = spacing / 2f
            }
            var sy = 0f
            while (sy < h) {
                canvas.drawLine(0f, sy, w.toFloat(), sy, lines)
                sy += spacing
            }
        }
        if (crt.vignette > 0f) {
            val shade = Paint().apply {
                shader = RadialGradient(
                    w / 2f, h / 2f, maxOf(w, h) * 0.75f,
                    intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT, Color.argb((190 * crt.vignette).toInt(), 0, 0, 0)),
                    floatArrayOf(0f, 0.45f, 1f),
                    Shader.TileMode.CLAMP,
                )
            }
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), shade)
        }
        if (crt.curvature <= 0f) return flat

        // Curvature: redraw the flat image through a bulging mesh (same curve as the live shader).
        val curved = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        Canvas(curved).apply {
            drawColor(theme.background.toInt())
            drawBitmapMesh(flat, MESH_X, MESH_Y, meshFor(w, h, crt.curvature * 0.6f), 0, null, 0, null)
        }
        flat.recycle()
        return curved
    }

    private const val MESH_X = 24
    private const val MESH_Y = 48

    /** Mesh vertices: each point of the flat image moved to where the curved screen shows it. */
    private fun meshFor(w: Int, h: Int, k: Float): FloatArray {
        val verts = FloatArray((MESH_X + 1) * (MESH_Y + 1) * 2)
        var i = 0
        for (row in 0..MESH_Y) {
            for (col in 0..MESH_X) {
                val cx = col.toFloat() / MESH_X - 0.5f
                val cy = row.toFloat() / MESH_Y - 0.5f
                // Inverse of the shader's mapping (close enough at these strengths).
                val scale = (1f + k * 0.25f) / (1f + k * (cx * cx + cy * cy))
                verts[i++] = (0.5f + cx * scale) * w
                verts[i++] = (0.5f + cy * scale) * h
            }
        }
        return verts
    }
}
