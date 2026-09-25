package com.asnidev.sysreadout.apps

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import com.asnidev.sysreadout.data.AppKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.text.Collator

/** [uid] is the app's uid in its own profile (work-profile apps have their own). */
data class AppEntry(val key: AppKey, val label: String, val isWork: Boolean, val uid: Int = -1)

/** Every launchable activity across the user's profiles, kept fresh via LauncherApps callbacks. */
class AppRepository(private val context: Context, private val scope: CoroutineScope) {

    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val userManager = context.getSystemService(UserManager::class.java)

    private val _apps = MutableStateFlow<List<AppEntry>>(emptyList())
    val apps: StateFlow<List<AppEntry>> = _apps

    private val callback = object : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String, user: UserHandle) = refresh()
        override fun onPackageAdded(packageName: String, user: UserHandle) = refresh()
        override fun onPackageChanged(packageName: String, user: UserHandle) = refresh()
        override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = refresh()
        override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = refresh()
    }

    init {
        launcherApps.registerCallback(callback, Handler(Looper.getMainLooper()))
        refresh()
    }

    fun close() = launcherApps.unregisterCallback(callback)

    fun refresh() {
        scope.launch(Dispatchers.IO) {
            val me = Process.myUserHandle()
            val collator = Collator.getInstance()
            _apps.value = userManager.userProfiles.flatMap { user ->
                val serial = userManager.getSerialNumberForUser(user)
                // A locked or removed profile throws; the other profiles still count.
                runCatching { launcherApps.getActivityList(null, user) }.getOrDefault(emptyList())
                    .filter { it.componentName.packageName != context.packageName }
                    .map {
                        AppEntry(
                            key = AppKey(it.componentName.packageName, it.componentName.className, serial),
                            label = it.label.toString(),
                            isWork = user != me,
                            uid = it.applicationInfo.uid,
                        )
                    }
            }.sortedWith { a, b -> collator.compare(a.label, b.label) }
        }
    }

    private fun handle(key: AppKey): UserHandle? = runCatching { userManager.getUserForSerialNumber(key.user) }.getOrNull()

    fun launch(key: AppKey): Boolean {
        val user = handle(key) ?: return false
        return try {
            launcherApps.startMainActivity(ComponentName(key.pkg, key.cls), user, null, null)
            true
        } catch (e: RuntimeException) { // SecurityException / ActivityNotFoundException
            false
        }
    }

    fun openInfo(key: AppKey) {
        val user = handle(key) ?: return
        try {
            launcherApps.startAppDetailsActivity(ComponentName(key.pkg, key.cls), user, null, null)
        } catch (_: RuntimeException) { // SecurityException / ActivityNotFoundException (paused work profile)
        }
    }

    fun uninstall(key: AppKey) {
        val intent = Intent(Intent.ACTION_DELETE, Uri.parse("package:${key.pkg}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(Intent.EXTRA_USER, handle(key))
        try {
            context.startActivity(intent)
        } catch (_: RuntimeException) { // ActivityNotFoundException / SecurityException
        }
    }
}
