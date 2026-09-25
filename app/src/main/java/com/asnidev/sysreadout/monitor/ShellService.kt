package com.asnidev.sysreadout.monitor

import java.io.File
import kotlin.system.exitProcess

/**
 * Shizuku "user service": Shizuku starts this class in its own process with
 * the shell uid, which can see every process and socket. Keep it free of
 * Android app state: it has no Context and no access to SysReadout's storage.
 * The work itself is in [ShellExec].
 */
class ShellService : IShellService.Stub() {

    override fun destroy() {
        ShellExec.shutdown()
        exitProcess(0)
    }

    override fun protocol(): Int = ShellProtocol.VERSION

    override fun exec(command: String, timeoutMs: Long): String? = ShellExec.run(command, timeoutMs)

    override fun readFile(path: String): String? = runCatching { File(path).readText() }.getOrNull()

    override fun resolve(ips: String): String =
        ShellExec.resolve(ips.lines().filter { it.isNotBlank() }, RESOLVE_BUDGET_MS)
            .entries.joinToString("\n") { (ip, host) -> "$ip\t$host" }

    private companion object {
        /** All lookups of one call together; the caller gives up a little after this. */
        const val RESOLVE_BUDGET_MS = 3_000L
    }
}
