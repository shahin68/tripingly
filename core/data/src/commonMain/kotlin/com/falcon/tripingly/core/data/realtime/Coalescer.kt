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
            try {
                do {
                    load(key)
                    val repeat = lock.withLock { again.remove(key) }
                } while (repeat)
            } finally {
                lock.withLock {
                    running -= key
                    again -= key
                }
            }
        }
    }
}
