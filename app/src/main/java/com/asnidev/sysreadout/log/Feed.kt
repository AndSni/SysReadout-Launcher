package com.asnidev.sysreadout.log

/**
 * The feed layout's list: newest first (index 0). Rows already in it update in
 * place; anything new (an event, a row switched on, a row that had fallen off
 * and has changed since) enters at index 0 and pushes the rest along. Whatever
 * doesn't fit [update]'s capacity is dropped; rows whose source no longer
 * reports them (an exited process, a closed connection) are removed.
 */
class Feed {
    private val items = ArrayList<FeedItem>()
    private val seen = HashMap<String, String>()
    private var newestAtTop: Boolean? = null
    private var lastEvent = 0L

    val rows: List<FeedItem> get() = items

    fun clear(eventsUpTo: Long) {
        items.clear()
        seen.clear()
        newestAtTop = null
        lastEvent = eventsUpTo
    }

    /**
     * [live]: every row that currently has a value, key → text, in reading order.
     * [events]: the stream; only those newer than the last update are added.
     */
    fun update(live: Map<String, String>, events: List<StreamLine>, newestAtTop: Boolean, capacity: Int, showEvents: Boolean = true) {
        val fresh = this.newestAtTop != newestAtTop
        if (fresh) {
            // First fill, or switching ends: rebuild so the batch reads in its natural order.
            clear(lastEvent)
            this.newestAtTop = newestAtTop
        }

        items.removeAll { it.time == null && it.key !in live }
        seen.keys.retainAll(live.keys)
        val position = HashMap<String, Int>().apply { items.forEachIndexed { i, it -> put(it.key, i) } }
        val arrivals = ArrayList<FeedItem>()
        for ((key, text) in live) {
            val i = position[key]
            when {
                i != null -> if (items[i].text != text) items[i] = FeedItem(key, text)
                seen[key] != text -> arrivals += FeedItem(key, text) // new, or changed while off screen
            }
            seen[key] = text
        }
        // A batch reads top-down in its natural order whichever end new rows enter at.
        val batch = if (newestAtTop) arrivals else arrivals.asReversed()
        // On a fresh fill the current state goes first and earlier events sit behind
        // it; after that, events are the newest thing and enter in front.
        if (!fresh) items.addAll(0, batch)
        // Events in the order they happened, so the newest ends up nearest the entry edge.
        events.filter { it.seq > lastEvent }.sortedBy { it.seq }.forEach { e ->
            if (showEvents) items.add(0, FeedItem("event:${e.seq}", e.text, e.time))
            lastEvent = e.seq
        }
        if (fresh) items.addAll(0, batch)
        while (items.size > capacity.coerceAtLeast(1)) items.removeAt(items.lastIndex)
    }
}
