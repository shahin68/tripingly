package com.falcon.tripingly.core.data.realtime

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Runs [load] for a key at most once at a time. Asks that come while it runs make it run once more
 * afterwards, however many they are, so a burst of live events costs two requests at most.
 */
internal class Coalescer(private val scope: CoroutineScope, private val load: suspend (String) -> Unit) {
    private val lock = Mutex()
    private val running = mutableSetOf<String>()
    private val again = mutableSetOf<String>()

    fun request(key: String) {
        scope.launch {
            val first = lock.withLock {
                if (key in running) again += key
                running.add(key)
            }
            if (!first) return@launch
            var done = false
            try {
                // Deciding to stop and leaving [running] happen under one lock, so an ask that comes in between isn't lost.
                do {
                    load(key)
                    done = lock.withLock { (!again.remove(key)).also { stop -> if (stop) running -= key } }
                } while (!done)
            } finally {
                if (!done) lock.withLock {
                    running -= key
                    again -= key
                }
            }
        }
    }
}
