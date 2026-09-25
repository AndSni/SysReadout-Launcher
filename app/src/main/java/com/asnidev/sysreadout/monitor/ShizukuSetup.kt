package com.asnidev.sysreadout.monitor

import android.os.Build
import com.asnidev.sysreadout.system.LockService

/** Access SysReadout can switch on for itself through Shizuku's shell, only when the user asks. */
enum class SetupItem(val title: String, val detail: String) {
    USAGE("usage access", "screen time, traffic per app, app switches"),
    NOTIFICATIONS("notification access", "notification log and table, what's playing"),
    LOCK("double-tap lock service", "lock the screen with a double-tap on home"),
}

/**
 * The shell commands behind the one-tap setup. They only switch on SysReadout's
 * own access (the same switches the user would flip in Android's settings) and
 * the grants stay after Shizuku stops.
 */
object ShizukuSetup {

    /** Items that make sense on this Android version. */
    val items: List<SetupItem> = SetupItem.entries.filter {
        it != SetupItem.LOCK || Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
    }

    fun listenerComponent(pkg: String) = "$pkg/${NotifListener::class.java.name}"

    fun lockComponent(pkg: String) = "$pkg/${LockService::class.java.name}"

    /**
     * The accessibility-service list with [component] added. Other enabled
     * services are kept as they are; `settings get` prints "null" for no list.
     */
    fun withService(current: String?, component: String): String {
        val list = current.orEmpty().trim().takeUnless { it == "null" }.orEmpty()
            .split(':').map { it.trim() }.filter { it.isNotEmpty() }
        return (if (list.any { it.equals(component, ignoreCase = true) }) list else list + component).joinToString(":")
    }

    /** Runs the commands for [item]; true when the shell ran them (the caller checks the result). */
    suspend fun apply(item: SetupItem, bridge: ShizukuBridge, pkg: String, userId: Int): Boolean = when (item) {
        SetupItem.USAGE -> bridge.exec("appops set --user $userId $pkg GET_USAGE_STATS allow") != null
        SetupItem.NOTIFICATIONS -> bridge.exec("cmd notification allow_listener ${listenerComponent(pkg)} $userId") != null
        SetupItem.LOCK -> {
            val current = bridge.exec("settings --user $userId get secure enabled_accessibility_services")
            if (current == null) {
                false
            } else {
                val list = withService(current, lockComponent(pkg))
                bridge.exec(
                    "settings --user $userId put secure enabled_accessibility_services '$list'" +
                        " && settings --user $userId put secure accessibility_enabled 1",
                ) != null
            }
        }
    }
}
