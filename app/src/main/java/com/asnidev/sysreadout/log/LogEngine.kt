package com.asnidev.sysreadout.log

import android.content.Context
import com.asnidev.sysreadout.apps.AppEntry
import com.asnidev.sysreadout.data.LauncherPrefs
import com.asnidev.sysreadout.data.LogLayout
import com.asnidev.sysreadout.data.MonitorPrefs
import com.asnidev.sysreadout.data.ProcSort
import com.asnidev.sysreadout.log.ProbeReader.Companion.bytes
import com.asnidev.sysreadout.monitor.CoreTicks
import com.asnidev.sysreadout.monitor.DnsLog
import com.asnidev.sysreadout.monitor.FIRST_APP_UID
import com.asnidev.sysreadout.monitor.NotifLog
import com.asnidev.sysreadout.monitor.Parsers
import com.asnidev.sysreadout.monitor.Proc
import com.asnidev.sysreadout.monitor.ShizukuBridge
import com.asnidev.sysreadout.monitor.Sock
import com.asnidev.sysreadout.monitor.UsageEvent
import com.asnidev.sysreadout.monitor.UsageSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/** One line of the feed layout; [key] identifies what it describes, [time] is set for events. */
data class FeedItem(val key: String, val text: String, val time: Long? = null)

/** [rows] are the classic layout's columns; [items] the same data as self-describing feed lines. */
data class LogTable(val title: String, val header: String, val rows: List<String>, val items: List<FeedItem> = emptyList())
data class StreamLine(val time: Long, val text: String, val seq: Long = 0)
data class LogFrame(
    val banner: List<String> = emptyList(),
    val pinned: List<String> = emptyList(),
    val tables: List<LogTable> = emptyList(),
    val stream: List<StreamLine> = emptyList(),
    val feed: List<FeedItem> = emptyList(),
)

/**
 * Produces what the log shows. [run] does all the work and only runs while
 * the home screen is visible; on the next visit it reports what changed in
 * between ("while away"), so nothing polls in the background. The DNS
 * monitor, when on, is the one thing that records while hidden.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LogEngine(
    private val context: Context,
    private val prefs: StateFlow<LauncherPrefs>,
    private val monitor: StateFlow<MonitorPrefs>,
    private val apps: StateFlow<List<AppEntry>>,
    val shizuku: ShizukuBridge,
) {
    val usage = UsageSource(context)
    private val reader = ProbeReader(context)
    private val pm = context.packageManager

    // Every mutation below happens on this one thread, so no locking is needed.
    private val serial = Dispatchers.Default.limitedParallelism(1)

    private val _frame = MutableStateFlow(LogFrame())
    val frame: StateFlow<LogFrame> = _frame

    private val stream = ArrayDeque<StreamLine>()
    private var banner = emptyList<String>()
    private var pinned = emptyList<String>()
    private var procTable: LogTable? = null
    private var connTable: LogTable? = null
    private var wakeTable: LogTable? = null
    private var screenTable: LogTable? = null
    private var trafficTable: LogTable? = null
    private var notifTable: LogTable? = null
    private var batteryTable: LogTable? = null
    private var batteryAt = 0L

    /** Debug builds: rows to show instead of the saved ones (adb `--es rows a,b,c`). */
    @Volatile var rowsOverride: List<String>? = null
    private val activeRows: List<String> get() = rowsOverride ?: prefs.value.logRows

    private val feed = Feed()
    private var eventSeq = 0L

    /** How many feed lines fit on screen; set by the UI. */
    @Volatile var feedCapacity = 60

    /** Debug builds: layout to show instead of the saved one. */
    @Volatile var layoutOverride: LogLayout? = null
    @Volatile var feedTopOverride: Boolean? = null

    /** Values for rows the engine gathers itself (usage access / Shizuku), keyed by row id. */
    private val external = HashMap<String, String>()

    private var lastSys: SysState? = null
    private var lastApps: Map<Int, Proc>? = null
    private var lastConns: Set<Pair<Int, String>>? = null
    private var lastSocks = emptyList<Sock>()
    private var lastPackages: Map<String, String>? = null
    private var lastForeground: String? = null
    private var lastTicks: Map<Int, CoreTicks>? = null
    private var usageSince = System.currentTimeMillis() - 15 * 60_000L
    private var usageRowsAt = 0L
    private var slowShellAt = 0L
    private var logcatLast: Double? = null
    private var dnsSeen = 0L
    private var notifSeen = System.currentTimeMillis()
    private val dnsEmitted = HashMap<String, Long>()
    private var started = false

    private val ptr = object : LinkedHashMap<String, String>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?) = size > 1024
    }
    private val unresolvable = HashSet<String>()
    private val resolving = HashSet<String>()
    private val labels = HashMap<String, String?>()
    private val uidNames = HashMap<Int, String>()

    init {
        reader.appsSummary = {
            val list = apps.value
            val work = list.count { it.isWork }
            "${list.size} launchable  ${prefs.value.hidden.size} hidden" + if (work > 0) "  $work work" else ""
        }
        reader.external = { id ->
            external[id] ?: when (ProbeCatalog.byId[id]?.needs) {
                Access.USAGE -> if (usage.hasAccess()) "…" else "needs usage access"
                Access.SHIZUKU -> if (shizuku.ready) "…" else "needs shizuku"
                else -> null
            }
        }
    }

    suspend fun run() = withContext(serial) {
        shizuku.refresh()
        if (!started) {
            started = true
            emit(
                "log",
                "sysreadout up · shizuku ${shizuku.state.value.label}" +
                    " · usage access ${if (usage.hasAccess()) "on" else "off"}" +
                    " · dns monitor ${if (DnsLog.running.value) "on" else "off"}",
            )
        }
        coroutineScope {
            launch { pinnedLoop() }
            launch { shellLoop(this@coroutineScope) }
            launch { usageLoop() }
            launch { dnsLoop() }
            launch { notifLoop() }
            launch { packagesLoop() }
        }
    }

    /** Up-to-date banner and rows plus the last tables and stream, for the lock-screen snapshot. */
    suspend fun freshFrame(): LogFrame = withContext(serial) {
        val p = prefs.value
        banner = if (p.showBanner) BannerText.render(p.banner, context) else emptyList()
        pinned = reader.sample(activeRows)
        publish()
        _frame.value
    }

    // --- banner, pinned rows, system events ---

    private suspend fun pinnedLoop() {
        try {
            while (true) {
                val p = prefs.value
                val rows = activeRows
                reader.updateWatchers(rows)
                banner = if (p.showBanner) BannerText.render(p.banner, context) else emptyList()
                pinned = reader.sample(rows)
                if (monitor.value.evSystem) systemEvents(reader.state())
                publish()
                delay(p.logIntervalSec * 1000L)
            }
        } finally {
            reader.stopWatchers()
        }
    }

    private fun systemEvents(now: SysState) {
        val was = lastSys
        lastSys = now
        if (was == null) return
        if (now.net != was.net) emit("net", "${was.net} → ${now.net}")
        if (now.power != was.power) emit("power", if (now.power == "battery") "unplugged" else "plugged in (${now.power})")
        if (now.level / 5 != was.level / 5) emit("bat", "${now.level}%")
        if (now.thermal != was.thermal) emit("therm", "${was.thermal} → ${now.thermal}")
        if (now.lowMemory != was.lowMemory) emit("mem", if (now.lowMemory) "LOW MEMORY" else "memory ok")
    }

    private suspend fun packagesLoop() {
        apps.collect { list ->
            labels.clear()
            uidNames.clear()
            val now = list.associate { it.key.pkg to it.label }
            val was = lastPackages
            lastPackages = now
            if (was == null || !monitor.value.evSystem) return@collect
            (now.keys - was.keys).forEach { emit("pkg", "+ ${now[it]} installed") }
            (was.keys - now.keys).forEach { emit("pkg", "- ${was[it]} removed") }
            publish()
        }
    }

    // --- Shizuku: processes, connections, wakelocks, logcat, extra rows ---

    private suspend fun shellLoop(scope: CoroutineScope) {
        while (true) {
            val m = monitor.value
            val rows = activeRows
            if (shizuku.ready) {
                if (m.procs || m.evProcs) sampleProcs(m) else procTable = null
                if (m.conns || m.evConns) sampleConns(m, scope) else connTable = null
                if (m.evLogcat) sampleLogcat(m)
                if ("load" in rows) sampleLoad()
                if ("cores" in rows) sampleCores()
                // dumpsys calls cost more; every other round is plenty.
                val now = System.currentTimeMillis()
                if (now - slowShellAt >= 2 * m.intervalSec * 1000L) {
                    slowShellAt = now
                    wakeTable = if (m.wakelocks) sampleWakelocks(m) else null
                    if ("temps" in rows) sampleTemps()
                    if ("media" in rows) sampleMedia()
                }
                // batterystats only moves slowly; every 5 minutes is plenty.
                if (!m.battery) batteryTable = null
                else if (now - batteryAt > 5 * 60_000L || batteryTable == null) {
                    batteryAt = now
                    batteryTable = sampleBattery(m) ?: batteryTable
                }
            } else {
                procTable = null
                connTable = null
                wakeTable = null
                batteryTable = null
                lastApps = null
                lastConns = null
                lastTicks = null
                listOf("load", "cores", "temps", "media").forEach { external.remove(it) }
            }
            publish()
            delay(m.intervalSec * 1000L)
        }
    }

    private suspend fun sampleProcs(m: MonitorPrefs) {
        val out = shizuku.exec("top -b -n 1 -q -o PID,UID,%CPU,RES,NAME") ?: return
        // Hide the sampler itself (sh + top running as shell).
        val procs = Parsers.top(out).filterNot { it.uid == SHELL_UID && (it.name == "top" || it.name == "sh") }

        val appProcs = procs.filter { it.isApp }.associateBy { it.pid }
        val was = lastApps
        lastApps = appProcs
        if (was != null && m.evProcs) {
            (appProcs.keys - was.keys).forEach { emit("proc", "+ ${procLabel(appProcs.getValue(it))}  pid $it") }
            (was.keys - appProcs.keys).forEach { emit("proc", "- ${procLabel(was.getValue(it))}  pid $it") }
        }

        procTable = if (!m.procs) null else {
            val sorted = procs.filter { !m.appsOnly || it.isApp }.sortedWith(
                if (m.procSort == ProcSort.CPU) compareByDescending<Proc> { it.cpu }.thenByDescending { it.resBytes }
                else compareByDescending { it.resBytes },
            )
            val top = sorted.take(m.procRows)
            LogTable(
                "procs · ${procs.size} · by ${m.procSort.name.lowercase()}",
                "  PID  CPU%    RES  PROCESS",
                top.map { String.format(Locale.US, "%5d %5.1f %6s  %s", it.pid, it.cpu, bytes(it.resBytes), procLabel(it)) },
                top.map {
                    item("proc:${it.pid}", "proc", String.format(Locale.US, "%s  cpu %.1f%%  rss %s  pid %d", procLabel(it), it.cpu, bytes(it.resBytes), it.pid))
                },
            )
        }
    }

    private suspend fun sampleConns(m: MonitorPrefs, scope: CoroutineScope) {
        val socks = listOf("tcp", "tcp6", "udp", "udp6").flatMap { proto ->
            Parsers.procNet(shizuku.readFile("/proc/net/$proto").orEmpty(), proto)
        }.distinctBy { it.uid to it.remote }
        lastSocks = socks

        val keys = socks.map { it.uid to it.remote }.toSet()
        val was = lastConns
        lastConns = keys
        val fresh = if (was == null || !m.evConns) emptyList() else socks.filter { (it.uid to it.remote) !in was }

        // Names the DNS monitor saw beat reverse DNS; only look up what it didn't.
        val unknown = if (!m.resolveHosts) emptyList() else socks.map { it.remoteIp }.distinct()
            .filter { DnsLog.host(it) == null && it !in ptr && it !in unresolvable && it !in resolving }.take(32)
        if (unknown.isEmpty()) {
            fresh.forEach { emit("conn", connLine(it)) }
        } else {
            // Lookups can take seconds, so they run beside the loop. New-connection
            // events wait for them so they can show the hostname.
            resolving += unknown
            scope.launch {
                val found = shizuku.resolve(unknown)
                ptr.putAll(found)
                unresolvable.addAll(unknown.filter { it !in found })
                resolving -= unknown.toSet()
                fresh.forEach { emit("conn", connLine(it)) }
                connTable = connTable(lastSocks, monitor.value)
                publish()
            }
        }
        connTable = connTable(socks, m)
    }

    private fun host(ip: String): String? = DnsLog.host(ip) ?: ptr[ip]

    private fun connTable(socks: List<Sock>, m: MonitorPrefs): LogTable? {
        if (!m.conns) return null
        val shown = socks
            .map { it to uidLabel(it.uid) }
            .sortedWith(compareBy({ it.second.lowercase() }, { it.first.remote }))
            .take(m.connRows)
        return LogTable(
            "connections · ${socks.size}",
            "APP           REMOTE                HOST",
            shown.map { (s, app) ->
                String.format(Locale.US, "%-13s %-21s %s", app.take(13), s.remote.take(21), host(s.remoteIp).orEmpty())
            },
            shown.map { (s, _) -> item("conn:${s.uid}/${s.remote}", "conn", connLine(s)) },
        )
    }

    private fun connLine(s: Sock): String =
        "${uidLabel(s.uid)} → ${s.remote}" + (host(s.remoteIp)?.let { "  $it" } ?: "") + if (s.proto == "udp") "  udp" else ""

    private suspend fun sampleWakelocks(m: MonitorPrefs): LogTable? {
        val locks = Parsers.wakeLocks(shizuku.exec("dumpsys power") ?: return wakeTable)
        val shown = locks.take(m.wakeRows)
        return LogTable(
            "wakelocks · ${locks.size}",
            if (locks.isEmpty()) "" else "TYPE          APP              TAG",
            if (locks.isEmpty()) listOf("none held") else shown.map {
                String.format(Locale.US, "%-13s %-16s %s", it.level.take(13), uidLabel(it.uid).take(16), it.tag)
            },
            shown.map { item("wake:${it.uid}/${it.tag}", "wake", "${uidLabel(it.uid)}  ${it.level}  ${it.tag}") },
        )
    }

    private suspend fun sampleLogcat(m: MonitorPrefs) {
        val level = if (m.logcatWarnings) "W" else "E"
        val since = logcatLast
        val command = if (since == null) {
            "logcat -d -v epoch -t 12 '*:$level'"
        } else {
            "logcat -d -v epoch -T ${String.format(Locale.US, "%.3f", since)} '*:$level' | tail -n 60"
        }
        val lines = Parsers.logcat(shizuku.exec(command) ?: return)
        lines.filter { since == null || it.time > since }.forEach {
            emit("log${it.level}", "${it.tag}: ${it.message}", (it.time * 1000).toLong())
        }
        logcatLast = lines.maxOfOrNull { it.time } ?: since ?: (System.currentTimeMillis() / 1000.0)
    }

    private suspend fun sampleLoad() {
        val f = shizuku.readFile("/proc/loadavg")?.trim()?.split(' ') ?: return
        if (f.size >= 4) external["load"] = "${f[0]} ${f[1]} ${f[2]}  run/total ${f[3]}"
    }

    private suspend fun sampleCores() {
        val now = Parsers.cpuTicks(shizuku.readFile("/proc/stat") ?: return)
        val was = lastTicks
        lastTicks = now
        if (was == null) return
        val cores = (0..(now.keys.maxOrNull() ?: 0)).map { core ->
            val a = was[core]
            val b = now[core]
            if (a == null || b == null || b.total <= a.total) null
            else (b.busy - a.busy).toDouble() / (b.total - a.total)
        }
        // Percent per core, "--" for an offline one.
        val each = cores.joinToString(" ") { u -> u?.let { "%2d".format((it * 100).toInt().coerceAtMost(99)) } ?: "--" }
        val avg = cores.filterNotNull().average().takeIf { !it.isNaN() } ?: 0.0
        external["cores"] = "$each  avg ${(avg * 100).toInt()}%"
    }

    private suspend fun sampleTemps() {
        val temps = Parsers.temperatures(shizuku.exec("dumpsys thermalservice") ?: return)
        external["temps"] = temps.entries.joinToString("  ") { (k, v) -> String.format(Locale.US, "%s %.1f°", k, v) }
            .ifEmpty { "no sensors reported" }
    }

    private suspend fun sampleMedia() {
        val playing = Parsers.nowPlaying(shizuku.exec("dumpsys media_session") ?: return)
        external["media"] = playing?.let { "${it.title}" + (it.artist?.let { a -> " — $a" } ?: "") + " · ${pkgLabel(it.pkg)}" }
            ?: "nothing playing"
    }

    private suspend fun sampleBattery(m: MonitorPrefs): LogTable? {
        val drains = Parsers.batteryUsage(shizuku.exec("dumpsys batterystats --usage") ?: return null)
            .filter { it.mah >= 0.01 }.sortedByDescending { it.mah }
        val shown = drains.take(m.batteryRows)
        return LogTable(
            "battery since charge",
            "APP                      mAh  MOSTLY",
            shown.map {
                String.format(Locale.US, "%-20s %8.1f  %s", uidLabel(it.uid).take(20), it.mah, it.mostly.orEmpty())
            }.ifEmpty { listOf("nothing measured yet") },
            shown.map {
                item("drain:${it.uid}", "drain", String.format(Locale.US, "%s  %.1fmAh since charge", uidLabel(it.uid), it.mah) +
                    (it.mostly?.let { m -> "  mostly $m" } ?: ""))
            },
        )
    }

    // --- notifications ---

    private suspend fun notifLoop() {
        while (true) {
            val m = monitor.value
            var changed = false
            if (m.evNotif) {
                NotifLog.since(notifSeen).forEach { n ->
                    notifSeen = maxOf(notifSeen, n.time)
                    val what = n.category?.let { " · $it" } ?: ""
                    val title = if (m.notifTitles) n.title?.let { "  \"$it\"" } ?: "" else ""
                    emit("ntf", pkgLabel(n.pkg) + what + title, n.time)
                    changed = true
                }
            } else {
                notifSeen = System.currentTimeMillis()
            }
            val table = if (m.notifTable && NotifLog.connected) {
                val shown = NotifLog.todayByApp().take(m.notifRows)
                LogTable(
                    "notifications today · ${NotifLog.todayTotal()}",
                    "",
                    shown.map { (pkg, n) -> String.format(Locale.US, "%-20s %8d", pkgLabel(pkg).take(20), n) }
                        .ifEmpty { listOf("none yet") },
                    shown.map { (pkg, n) -> item("ntfs:$pkg", "ntfs", "${pkgLabel(pkg)}  $n notifications today") },
                )
            } else null
            if (table != notifTable) {
                notifTable = table
                changed = true
            }
            if (changed) publish()
            delay(2_000)
        }
    }

    // --- DNS monitor ---

    private suspend fun dnsLoop() {
        while (true) {
            val m = monitor.value
            if (m.evDns) {
                var emitted = false
                DnsLog.since(dnsSeen).forEach { l ->
                    dnsSeen = maxOf(dnsSeen, l.time)
                    // Reverse lookups (…in-addr.arpa) are tools resolving IPs, not apps reaching servers.
                    if (l.name.endsWith(".arpa") || l.name.isEmpty()) return@forEach
                    val key = "${l.uid}/${l.name}"
                    // Apps repeat lookups constantly; show each app+name once every few minutes.
                    if (l.time - (dnsEmitted[key] ?: 0L) > DNS_REPEAT_MS) {
                        dnsEmitted[key] = l.time
                        emit("dns", "${uidLabel(l.uid)} → ${l.name}", l.time)
                        emitted = true
                    }
                }
                dnsEmitted.entries.removeAll { System.currentTimeMillis() - it.value > 2 * DNS_REPEAT_MS }
                if (emitted) publish()
            } else {
                dnsSeen = System.currentTimeMillis()
            }
            delay(2_000)
        }
    }

    // --- usage access: app switches, services, screen time, traffic ---

    private suspend fun usageLoop() {
        while (true) {
            val m = monitor.value
            val rows = activeRows
            if (usage.hasAccess()) {
                val now = System.currentTimeMillis()
                if (m.evApps || m.evServices || m.evScreen) {
                    usage.events(usageSince, now).forEach { usageEvent(it, m) }
                }
                usageSince = now
                if (now - usageRowsAt > 60_000L) {
                    usageRowsAt = now
                    screenTable = if (m.screenTime) screenTable(m) else null
                    trafficTable = if (m.traffic) trafficTable(m) else null
                    if ("today" in rows) {
                        val (onMs, unlocks) = usage.today()
                        external["today"] = "screen on ${duration(onMs)}  $unlocks unlocks"
                    }
                    if ("month" in rows) {
                        val (wifi, mobile) = usage.dataThisMonth()
                        external["month"] = "wifi ${bytes(wifi)}  mobile ${bytes(mobile)}"
                    }
                }
                if (!m.screenTime) screenTable = null
                if (!m.traffic) trafficTable = null
            } else {
                screenTable = null
                trafficTable = null
                external.remove("today")
                external.remove("month")
            }
            publish()
            delay(maxOf(m.intervalSec, 5) * 1000L)
        }
    }

    private fun usageEvent(e: UsageEvent, m: MonitorPrefs) {
        when (e.kind) {
            UsageEvent.Kind.FOREGROUND -> {
                // Skip ourselves and activity changes inside the same app.
                if (e.pkg == context.packageName) {
                    lastForeground = e.pkg
                } else if (m.evApps && e.pkg != lastForeground) {
                    lastForeground = e.pkg
                    emit("fg", pkgLabel(e.pkg), e.time)
                }
            }
            UsageEvent.Kind.SERVICE_START -> if (m.evServices) emit("svc", "+ ${pkgLabel(e.pkg)}", e.time)
            UsageEvent.Kind.SERVICE_STOP -> if (m.evServices) emit("svc", "- ${pkgLabel(e.pkg)}", e.time)
            UsageEvent.Kind.SCREEN_ON -> if (m.evScreen) emit("scrn", "on", e.time)
            UsageEvent.Kind.SCREEN_OFF -> if (m.evScreen) emit("scrn", "off", e.time)
            UsageEvent.Kind.UNLOCKED -> if (m.evScreen) emit("lock", "unlocked", e.time)
            UsageEvent.Kind.LOCKED -> if (m.evScreen) emit("lock", "locked", e.time)
        }
    }

    private fun screenTable(m: MonitorPrefs): LogTable {
        val shown = usage.screenTimeToday().take(m.screenRows)
        return LogTable(
            "screen time today",
            "",
            shown.map { (pkg, ms) -> String.format(Locale.US, "%-20s %8s", pkgLabel(pkg).take(20), duration(ms)) },
            shown.map { (pkg, ms) -> item("scrn:$pkg", "scrn", "${pkgLabel(pkg)}  ${duration(ms)} on screen today") },
        )
    }

    private fun trafficTable(m: MonitorPrefs): LogTable {
        val shown = usage.trafficToday().entries.sortedByDescending { it.value.first + it.value.second }.take(m.trafficRows)
        return LogTable(
            "traffic today",
            "APP                     ↓RX     ↑TX",
            shown.map { (uid, t) -> String.format(Locale.US, "%-20s %7s %7s", uidLabel(uid).take(20), bytes(t.first), bytes(t.second)) },
            shown.map { (uid, t) -> item("traf:$uid", "traf", "${uidLabel(uid)}  ↓${bytes(t.first)} ↑${bytes(t.second)} today") },
        )
    }

    // --- names ---

    /** The name the user sees for a package: their rename, the launcher label, or the app's own label. */
    private fun appLabel(pkg: String): String? {
        apps.value.firstOrNull { it.key.pkg == pkg }?.let { return prefs.value.renames[it.key] ?: it.label }
        if (pkg in labels) return labels[pkg]
        val label = runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrNull()
            ?.takeIf { it.isNotBlank() && it != pkg }
        labels[pkg] = label
        return label
    }

    private fun pkgLabel(pkg: String): String = appLabel(pkg) ?: shortPkg(pkg)

    /** Packages without a label: trim the vendor prefix instead. */
    private fun shortPkg(pkg: String): String =
        PKG_PREFIXES.firstOrNull { pkg.startsWith(it) && pkg.length > it.length }?.let { pkg.removePrefix(it) } ?: pkg

    /**
     * "Google Play services:persistent" for com.google.android.gms.persistent: some
     * processes are named package.part without the usual ":" separator.
     */
    private fun procLabel(p: Proc): String {
        if (!p.isApp) return p.name
        var pkg = p.name.substringBefore(':')
        val parts = ArrayDeque<String>()
        while (appLabel(pkg) == null && parts.size < 2 && '.' in pkg) {
            parts.addFirst(pkg.substringAfterLast('.'))
            pkg = pkg.substringBeforeLast('.')
        }
        val label = appLabel(pkg) ?: return shortPkg(p.name)
        val suffix = listOf(parts.joinToString("."), p.name.substringAfter(':', "")).filter { it.isNotEmpty() }
        return if (suffix.isEmpty()) label else label + ":" + suffix.joinToString(":")
    }

    private fun uidLabel(uid: Int): String = uidNames.getOrPut(uid) {
        when (uid) {
            -1 -> "?"
            0 -> "root"
            1000 -> "system"
            SHELL_UID -> "shell"
            -4 -> "removed apps" // NetworkStats.Bucket.UID_REMOVED
            -5 -> "tethering" // NetworkStats.Bucket.UID_TETHERING
            else -> {
                val pkgs = pm.getPackagesForUid(uid)?.toList().orEmpty()
                // Shared uids list several packages; prefer one with a real name.
                pkgs.firstNotNullOfOrNull { appLabel(it) }
                    ?: pkgs.firstOrNull()?.let(::shortPkg)
                    ?: if (uid < FIRST_APP_UID) "uid $uid" else "app $uid"
            }
        }
    }

    // --- output ---

    private fun emit(key: String, text: String, time: Long = System.currentTimeMillis()) {
        val line = StreamLine(time, key.padEnd(ProbeReader.KEY_WIDTH) + text, ++eventSeq)
        // Late events (usage, DNS, logcat) arrive with their original time: keep the stream in time order.
        var i = stream.size
        while (i > 0 && stream[i - 1].time > time) i--
        stream.add(i, line)
        while (stream.size > STREAM_MAX) stream.removeFirst()
    }

    private fun publish() {
        val tables = listOfNotNull(procTable, connTable, wakeTable, batteryTable, screenTable, trafficTable, notifTable)
        updateFeed(tables)
        _frame.value = LogFrame(banner, pinned, tables, stream.toList(), feed.rows.toList())
    }

    private fun updateFeed(tables: List<LogTable>) {
        if ((layoutOverride ?: prefs.value.logLayout) != LogLayout.FEED) {
            if (feed.rows.isNotEmpty()) feed.clear(eventSeq)
            return
        }
        // Everything with a live value, in reading order.
        val live = LinkedHashMap<String, String>()
        pinned.forEach { row -> live["row:" + row.substringBefore(' ')] = row }
        tables.forEach { t -> t.items.forEach { live[it.key] = it.text } }
        feed.update(live, stream, feedTopOverride ?: prefs.value.feedNewestAtTop, feedCapacity, prefs.value.showStream)
    }

    private fun item(key: String, tag: String, text: String) = FeedItem(key, tag.padEnd(ProbeReader.KEY_WIDTH) + text)

    private fun duration(ms: Long): String {
        val m = ms / 60_000
        return if (m >= 60) "${m / 60}h${"%02d".format(m % 60)}m" else "${m}m"
    }

    companion object {
        private const val STREAM_MAX = 300
        private const val SHELL_UID = 2000
        private const val DNS_REPEAT_MS = 5 * 60_000L
        private val PKG_PREFIXES = listOf("com.google.android.apps.", "com.google.android.", "com.android.", "com.", "org.")
    }
}
