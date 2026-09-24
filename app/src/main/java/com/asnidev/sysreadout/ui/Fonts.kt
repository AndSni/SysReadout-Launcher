package com.asnidev.sysreadout.ui

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.res.ResourcesCompat
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.asnidev.sysreadout.R
import java.io.File

/** [license] names the bundled license file under assets/licenses, if any. */
data class FontOption(val id: String, val name: String, val license: String? = null, val licenseFile: String? = null)

object Fonts {

    const val USER_PREFIX = "user:"

    val bundled = listOf(
        FontOption("system-mono", "system mono"),
        FontOption("system-sans", "system sans"),
        FontOption("jetbrains-mono", "JetBrains Mono", "OFL 1.1", "JetBrainsMono-OFL.txt"),
        FontOption("ibm-plex-mono", "IBM Plex Mono", "OFL 1.1", "IBMPlexMono-OFL.txt"),
        FontOption("space-mono", "Space Mono", "OFL 1.1", "SpaceMono-OFL.txt"),
        FontOption("share-tech-mono", "Share Tech Mono", "OFL 1.1", "ShareTechMono-OFL.txt"),
        FontOption("vt323", "VT323", "OFL 1.1", "VT323-OFL.txt"),
        FontOption("px437-vga", "Px437 IBM VGA 8x16", "CC BY-SA 4.0 · VileR, int10h.org", "Px437-CC-BY-SA-4.0.txt"),
        FontOption("major-mono", "Major Mono Display", "OFL 1.1", "MajorMonoDisplay-OFL.txt"),
        FontOption("press-start", "Press Start 2P", "OFL 1.1", "PressStart2P-OFL.txt"),
        FontOption("silkscreen", "Silkscreen", "OFL 1.1", "Silkscreen-OFL.txt"),
    )

    /** Bundled font files: regular, and bold when the family ships one. */
    private val files = mapOf(
        "jetbrains-mono" to (R.font.jetbrainsmono_regular to R.font.jetbrainsmono_bold),
        "ibm-plex-mono" to (R.font.ibmplexmono_regular to R.font.ibmplexmono_bold),
        "space-mono" to (R.font.spacemono_regular to R.font.spacemono_bold),
        "share-tech-mono" to (R.font.sharetechmono_regular to null),
        "vt323" to (R.font.vt323_regular to null),
        "px437-vga" to (R.font.px437_ibm_vga to null),
        "major-mono" to (R.font.majormonodisplay_regular to null),
        "press-start" to (R.font.pressstart2p_regular to null),
        "silkscreen" to (R.font.silkscreen_regular to R.font.silkscreen_bold),
    )

    private val cache = HashMap<String, FontFamily>()

    fun family(context: Context, id: String): FontFamily = synchronized(cache) {
        cache.getOrPut(id) {
            val bundled = files[id]
            when {
                id == "system-sans" -> FontFamily.Default
                bundled != null -> bundled.second?.let { bold ->
                    FontFamily(Font(bundled.first, FontWeight.Normal), Font(bold, FontWeight.Bold))
                } ?: FontFamily(Font(bundled.first))
                else -> userFile(context, id)?.takeIf { it.exists() }?.let { FontFamily(Font(it)) }
                    ?: FontFamily.Monospace
            }
        }
    }

    /** The same fonts for plain Canvas drawing (the lock-screen snapshot). */
    fun typeface(context: Context, id: String, bold: Boolean): Typeface {
        val bundled = files[id]
        val base = when {
            id == "system-sans" -> Typeface.DEFAULT
            bundled != null -> ResourcesCompat.getFont(context, if (bold) bundled.second ?: bundled.first else bundled.first)
            else -> userFile(context, id)?.takeIf { it.exists() }?.let { runCatching { Typeface.createFromFile(it) }.getOrNull() }
        } ?: Typeface.MONOSPACE
        // Families without a bold file get a synthetic bold, as Compose does.
        return if (bold && (bundled?.second == null)) Typeface.create(base, Typeface.BOLD) else base
    }

    // --- imported fonts, kept in files/fonts ---

    private fun dir(context: Context) = File(context.filesDir, "fonts").apply { mkdirs() }

    private fun userFile(context: Context, id: String): File? =
        if (id.startsWith(USER_PREFIX)) File(dir(context), id.removePrefix(USER_PREFIX)) else null

    fun imported(context: Context): List<FontOption> =
        dir(context).listFiles().orEmpty().sortedBy { it.name.lowercase() }.map {
            FontOption(USER_PREFIX + it.name, it.nameWithoutExtension)
        }

    fun all(context: Context): List<FontOption> = bundled + imported(context)

    fun name(context: Context, id: String): String =
        all(context).firstOrNull { it.id == id }?.name ?: "system mono"

    /** Copies a picked .ttf/.otf into app storage. Null when it isn't a usable font. */
    fun import(context: Context, uri: Uri): FontOption? {
        val display = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null } ?: "font.ttf"
        var name = display.replace(Regex("[^A-Za-z0-9._-]"), "_")
        if (!name.endsWith(".ttf", true) && !name.endsWith(".otf", true)) name += ".ttf"
        val target = File(dir(context), name)
        val tmp = File(dir(context), ".$name.part")
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                tmp.outputStream().use { input.copyTo(it) }
            } ?: return null
            // Typeface.Builder returns null for anything that isn't a font it can render.
            Typeface.Builder(tmp).build() ?: return null
            tmp.renameTo(target)
            synchronized(cache) { cache.remove(USER_PREFIX + name) }
            FontOption(USER_PREFIX + name, target.nameWithoutExtension)
        } catch (e: Exception) {
            null
        } finally {
            tmp.delete()
        }
    }

    fun delete(context: Context, id: String) {
        userFile(context, id)?.delete()
        synchronized(cache) { cache.remove(id) }
    }
}
