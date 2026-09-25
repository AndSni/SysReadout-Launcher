package com.asnidev.sysreadout.system

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.provider.Settings
import android.util.Log

object SystemActions {

    /** No public API for this; every launcher uses the same StatusBarManager reflection. */
    @SuppressLint("WrongConstant")
    fun expandNotifications(context: Context) {
        runCatching {
            val sbm = context.getSystemService("statusbar")
            Class.forName("android.app.StatusBarManager").getMethod("expandNotificationsPanel").invoke(sbm)
        }
    }

    fun openAlarms(context: Context) = open(context, Intent(AlarmClock.ACTION_SHOW_ALARMS))

    fun openCalendar(context: Context) =
        open(context, Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALENDAR))

    fun openHomeSettings(context: Context) = open(context, Intent(Settings.ACTION_HOME_SETTINGS))

    fun openAccessibilitySettings(context: Context) = open(context, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))

    fun openNotificationAccess(context: Context) = open(context, Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))

    fun openDeveloperOptions(context: Context) =
        open(context, Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)) ||
            open(context, Intent(Settings.ACTION_DEVICE_INFO_SETTINGS))

    fun openUrl(context: Context, url: String) = open(context, Intent(Intent.ACTION_VIEW, Uri.parse(url)))

    fun isDefaultLauncher(context: Context): Boolean {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return context.packageManager.resolveActivity(home, 0)?.activityInfo?.packageName == context.packageName
    }

    /**
     * Starts [intent]; false when nothing handles it. Settings screens are
     * missing or locked down on some phones, which must not crash the launcher.
     */
    fun open(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: Exception) { // ActivityNotFoundException, SecurityException
        Log.w("SystemActions", "couldn't open $intent: $e")
        false
    }
}
