package com.asnidev.sysreadout.log

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SupervisorTest {

    @Test
    fun restartsAfterAFailureWithGrowingPauses() = runTest {
        val retries = mutableListOf<Long>()
        var runs = 0
        supervised("x", { _, _, retry -> retries += retry }, firstBackoffMs = 1_000, maxBackoffMs = 3_000, now = { currentTime }) {
            runs++
            if (runs <= 4) error("boom $runs")
        }
        assertEquals(5, runs)
        assertEquals(listOf(1_000L, 2_000L, 3_000L, 3_000L), retries)
    }

    @Test
    fun cleanFinishEndsIt() = runTest {
        var runs = 0
        supervised("x", { _, _, _ -> error("no failure expected") }) { runs++ }
        assertEquals(1, runs)
    }

    @Test
    fun cancellationIsNotAFailure() = runTest {
        var failures = 0
        val job = launch {
            supervised("x", { _, _, _ -> failures++ }) { awaitCancellation() }
        }
        runCurrent()
        job.cancel()
        runCurrent()
        assertTrue(job.isCancelled)
        assertEquals(0, failures)
    }

    @Test
    fun aLoopThatRanCleanlyForLongStartsOverWithAShortPause() = runTest {
        val retries = mutableListOf<Long>()
        var runs = 0
        supervised("x", { _, _, retry -> retries += retry }, firstBackoffMs = 1_000, maxBackoffMs = 4_000, now = { currentTime }) {
            runs++
            when (runs) {
                1, 2 -> error("early")
                3 -> {
                    delay(60_000) // healthy for a minute, then fails again
                    error("late")
                }
            }
        }
        assertEquals(listOf(1_000L, 2_000L, 1_000L), retries)
    }
}
