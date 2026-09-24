package com.asnidev.sysreadout.log

import android.content.Context
import android.os.BatteryManager
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** Fills `{variables}` in the user's banner text. Unknown names are left as typed. */
object BannerText {

    /** Shown in settings as the list of what can be used. */
    val VARIABLES = listOf(
        "year", "date", "time", "weekday", "device", "model", "maker",
        "android", "api", "kernel", "build", "uptime", "battery",
    )

    private val PATTERN = Regex("\\{(\\w+)\\}")

    fun render(template: String, context: Context): List<String> {
        if (template.isBlank()) return emptyList()
        val values = HashMap<String, String?>()
        fun value(name: String): String? = values.getOrPut(name) { resolve(name, context) }
        return template.lines().map { line -> PATTERN.replace(line) { m -> value(m.groupValues[1]) ?: m.value } }
    }

    private fun resolve(name: String, context: Context): String? = when (name) {
        "year" -> LocalDate.now().year.toString()
        "date" -> LocalDate.now().toString()
        "time" -> LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
        "weekday" -> LocalDate.now().dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
        // The name the user gave the phone (Settings › About), usually its marketing name.
        "device" -> Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME)
            ?.takeIf { it.isNotBlank() } ?: Build.MODEL
        "model" -> Build.MODEL
        "maker" -> Build.MANUFACTURER
        "android" -> Build.VERSION.RELEASE
        "api" -> Build.VERSION.SDK_INT.toString()
        "kernel" -> System.getProperty("os.version")?.substringBefore('-')
        "build" -> Build.DISPLAY
        "uptime" -> (SystemClock.elapsedRealtime() / 60_000).let { m -> "${m / 1440}d ${m % 1440 / 60}h ${m % 60}m" }
        "battery" -> context.getSystemService(BatteryManager::class.java)
            .getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).toString() + "%"
        else -> null
    }
}
