package com.asnidev.sysreadout.log

import org.junit.Assert.assertEquals
import org.junit.Test

class FeedTest {

    private val feed = Feed()
    private val events = mutableListOf<StreamLine>()

    private fun update(vararg live: Pair<String, String>, top: Boolean = true, capacity: Int = 10) =
        feed.update(linkedMapOf(*live), events, top, capacity)

    private fun keys() = feed.rows.map { it.key }
    private fun event(seq: Long) = events.add(StreamLine(seq * 1000, "ev $seq", seq))

    @Test
    fun firstFillReadsInOrder() {
        update("a" to "1", "b" to "1", "c" to "1")
        assertEquals(listOf("a", "b", "c"), keys())
    }

    @Test
    fun bottomModeStoresNewestFirstSoTheReversedViewReadsInOrder() {
        update("a" to "1", "b" to "1", "c" to "1", top = false)
        assertEquals(listOf("c", "b", "a"), keys()) // the UI shows this reversed: a, b, c
    }

    @Test
    fun rowsOnScreenUpdateInPlace() {
        update("a" to "1", "b" to "1", "c" to "1")
        update("a" to "1", "b" to "2", "c" to "1")
        assertEquals(listOf("a", "b", "c"), keys())
        assertEquals("2", feed.rows[1].text)
    }

    @Test
    fun eventsEnterAtTheTopAndPushRowsOff() {
        update("a" to "1", "b" to "1", "c" to "1", capacity = 3)
        event(1)
        update("a" to "1", "b" to "1", "c" to "1", capacity = 3)
        assertEquals(listOf("event:1", "a", "b"), keys())
    }

    @Test
    fun aFallenOffRowReturnsOnlyWhenItChanges() {
        update("a" to "1", "b" to "1", "c" to "1", capacity = 3)
        event(1)
        update("a" to "1", "b" to "1", "c" to "1", capacity = 3) // c falls off
        update("a" to "1", "b" to "1", "c" to "1", capacity = 3) // unchanged: stays off
        assertEquals(listOf("event:1", "a", "b"), keys())
        update("a" to "1", "b" to "1", "c" to "2", capacity = 3) // changed: back at the top
        assertEquals(listOf("c", "event:1", "a"), keys())
    }

    @Test
    fun rowsWhoseSourceIsGoneAreRemoved() {
        update("a" to "1", "proc:42" to "x", "c" to "1")
        update("a" to "1", "c" to "1")
        assertEquals(listOf("a", "c"), keys())
    }

    @Test
    fun onAFreshFillEarlierEventsSitBehindTheRows() {
        event(1)
        event(2)
        update("a" to "1", "b" to "1")
        assertEquals(listOf("a", "b", "event:2", "event:1"), keys())
    }

    @Test
    fun eventsCanBeHidden() {
        update("a" to "1")
        event(1)
        feed.update(linkedMapOf("a" to "1"), events, newestAtTop = true, capacity = 10, showEvents = false)
        assertEquals(listOf("a"), keys())
    }
}
