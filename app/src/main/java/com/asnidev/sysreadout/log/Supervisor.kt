package com.asnidev.sysreadout.log

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/**
 * Runs [loop] and starts it again whenever it throws, after a pause that
 * doubles from [firstBackoffMs] up to [maxBackoffMs] and starts over once the
 * loop has run cleanly for a while. [onError] hears about each failure first.
 * One broken data source must never take the launcher down with it;
 * cancellation and VM errors (out of memory) still pass through.
 */
suspend fun supervised(
    name: String,
    onError: (name: String, error: Throwable, retryMs: Long) -> Unit,
    firstBackoffMs: Long = 5_000,
    maxBackoffMs: Long = 5 * 60_000,
    now: () -> Long = System::currentTimeMillis,
    loop: suspend () -> Unit,
) {
    var backoff = firstBackoffMs
    while (true) {
        val startedAt = now()
        try {
            loop()
            return
        } catch (e: CancellationException) {
            throw e
        } catch (e: VirtualMachineError) {
            throw e
        } catch (e: Throwable) {
            if (now() - startedAt > 2 * maxBackoffMs) backoff = firstBackoffMs
            onError(name, e, backoff)
            delay(backoff)
            backoff = (backoff * 2).coerceAtMost(maxBackoffMs)
        }
    }
}
