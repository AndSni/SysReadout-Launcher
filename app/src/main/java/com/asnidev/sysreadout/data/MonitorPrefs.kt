package com.asnidev.sysreadout.data

import org.json.JSONObject

enum class ProcSort { CPU, MEM }

/** What the system monitor shows. Tables whose data source isn't available stay hidden. */
data class MonitorPrefs(
    /** Use Shizuku at all. Off: no Shizuku code runs and its tables, rows and events stay hidden. */
    val shizuku: Boolean = false,
    /** The one-time log tip about what Shizuku adds has been shown. */
    val shizukuTip: Boolean = false,
    val procs: Boolean = true,
    val procSort: ProcSort = ProcSort.CPU,
    val procRows: Int = 6,
    val appsOnly: Boolean = false,
    val conns: Boolean = true,
    val connRows: Int = 6,
    val resolveHosts: Boolean = true,
    val screenTime: Boolean = true,
    val screenRows: Int = 4,
    val traffic: Boolean = false,
    val trafficRows: Int = 4,
    val wakelocks: Boolean = true,
    val wakeRows: Int = 4,
    /** The optional local-VPN DNS monitor: real hostnames per app. */
    val dnsVpn: Boolean = false,
    val evDns: Boolean = true,
    val evLogcat: Boolean = true,
    val logcatWarnings: Boolean = false,
    val evNotif: Boolean = true,
    val notifTitles: Boolean = false,
    val notifTable: Boolean = true,
    val notifRows: Int = 4,
    val battery: Boolean = false,
    val batteryRows: Int = 5,
    val evApps: Boolean = true,
    val evServices: Boolean = true,
    val evScreen: Boolean = false,
    val evProcs: Boolean = true,
    val evConns: Boolean = true,
    val evSystem: Boolean = true,
    val intervalSec: Int = 5,
) {
    fun toJson(): String = JSONObject()
        .put("shizuku", shizuku).put("shizukuTip", shizukuTip)
        .put("procs", procs).put("procSort", procSort.name).put("procRows", procRows).put("appsOnly", appsOnly)
        .put("conns", conns).put("connRows", connRows).put("resolveHosts", resolveHosts)
        .put("screenTime", screenTime).put("screenRows", screenRows)
        .put("traffic", traffic).put("trafficRows", trafficRows)
        .put("wakelocks", wakelocks).put("wakeRows", wakeRows)
        .put("dnsVpn", dnsVpn).put("evDns", evDns)
        .put("evLogcat", evLogcat).put("logcatWarnings", logcatWarnings)
        .put("evNotif", evNotif).put("notifTitles", notifTitles).put("notifTable", notifTable).put("notifRows", notifRows)
        .put("battery", battery).put("batteryRows", batteryRows)
        .put("evApps", evApps).put("evServices", evServices).put("evScreen", evScreen)
        .put("evProcs", evProcs).put("evConns", evConns).put("evSystem", evSystem)
        .put("intervalSec", intervalSec)
        .toString()

    companion object {
        val INTERVALS = listOf(2, 5, 10, 30)

        fun fromJson(s: String?): MonitorPrefs {
            val d = MonitorPrefs()
            val o = runCatching { JSONObject(s ?: return d) }.getOrElse { return d }
            return MonitorPrefs(
                // Settings saved before the switch existed used Shizuku whenever it was there.
                shizuku = o.optBoolean("shizuku", true),
                shizukuTip = o.optBoolean("shizukuTip", d.shizukuTip),
                procs = o.optBoolean("procs", d.procs),
                procSort = runCatching { ProcSort.valueOf(o.optString("procSort")) }.getOrDefault(d.procSort),
                procRows = o.optInt("procRows", d.procRows),
                appsOnly = o.optBoolean("appsOnly", d.appsOnly),
                conns = o.optBoolean("conns", d.conns),
                connRows = o.optInt("connRows", d.connRows),
                resolveHosts = o.optBoolean("resolveHosts", d.resolveHosts),
                screenTime = o.optBoolean("screenTime", d.screenTime),
                screenRows = o.optInt("screenRows", d.screenRows),
                traffic = o.optBoolean("traffic", d.traffic),
                trafficRows = o.optInt("trafficRows", d.trafficRows),
                wakelocks = o.optBoolean("wakelocks", d.wakelocks),
                wakeRows = o.optInt("wakeRows", d.wakeRows),
                dnsVpn = o.optBoolean("dnsVpn", d.dnsVpn),
                evDns = o.optBoolean("evDns", d.evDns),
                evLogcat = o.optBoolean("evLogcat", d.evLogcat),
                logcatWarnings = o.optBoolean("logcatWarnings", d.logcatWarnings),
                evNotif = o.optBoolean("evNotif", d.evNotif),
                notifTitles = o.optBoolean("notifTitles", d.notifTitles),
                notifTable = o.optBoolean("notifTable", d.notifTable),
                notifRows = o.optInt("notifRows", d.notifRows),
                battery = o.optBoolean("battery", d.battery),
                batteryRows = o.optInt("batteryRows", d.batteryRows),
                evApps = o.optBoolean("evApps", d.evApps),
                evServices = o.optBoolean("evServices", d.evServices),
                evScreen = o.optBoolean("evScreen", d.evScreen),
                evProcs = o.optBoolean("evProcs", d.evProcs),
                evConns = o.optBoolean("evConns", d.evConns),
                evSystem = o.optBoolean("evSystem", d.evSystem),
                intervalSec = o.optInt("intervalSec", d.intervalSec),
            )
        }
    }
}
