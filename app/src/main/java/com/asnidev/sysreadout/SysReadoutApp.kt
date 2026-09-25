package com.asnidev.sysreadout

import android.app.Application
import com.asnidev.sysreadout.system.CrashGuard

class SysReadoutApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // First thing, so even a crash while the launcher starts up is caught and counted.
        CrashGuard.install(this)
    }
}
