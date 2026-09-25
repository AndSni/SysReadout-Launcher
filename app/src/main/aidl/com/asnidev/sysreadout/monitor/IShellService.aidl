package com.asnidev.sysreadout.monitor;

// Runs inside Shizuku's shell-uid process (see ShellService).
interface IShellService {
    // Transaction code reserved by Shizuku for tearing the service down.
    void destroy() = 16777114;

    // stdout+stderr of `sh -c command`; null when it couldn't run or took longer than timeoutMs.
    String exec(String command, long timeoutMs) = 1;

    // Contents of a file readable by the shell user; null if unreadable.
    String readFile(String path) = 2;

    // Newline-separated IPs in, "ip<TAB>hostname" lines out (reverse DNS; unresolved IPs omitted).
    String resolve(String ips) = 3;

    // ShellProtocol.VERSION of the running helper; an older helper answers 0.
    int protocol() = 4;
}
