package com.falcon.tripingly.core.data.trips

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Tracks local changes to a trip that the server hasn't confirmed yet, so a
 * reload from the server doesn't overwrite them. A reload saves only when no
 * change is pending and none started while it was loading.
 */
internal class PendingTripWrites {
    private val mutex = Mutex()
    private val pending = mutableMapOf<String, Int>()
    private val versions = mutableMapOf<String, Long>()

    /** Marks a change as started and applies [applyLocally] in the same step. */
    suspend fun begin(tripId: String, applyLocally: suspend () -> Unit) = mutex.withLock {
        pending[tripId] = (pending[tripId] ?: 0) + 1
        versions[tripId] = (versions[tripId] ?: 0) + 1
        applyLocally()
    }

    /** Marks a change as done; true when it was the trip's last pending one. */
    suspend fun end(tripId: String): Boolean = mutex.withLock {
        val left = (pending[tripId] ?: 1) - 1
        if (left <= 0) pending.remove(tripId) else pending[tripId] = left
        left <= 0
    }

    suspend fun version(tripId: String): Long = mutex.withLock { versions[tripId] ?: 0 }

    /** Runs [save] unless a change is pending or started after [since]; true when it ran. */
    suspend fun saveIfQuiet(tripId: String, since: Long, save: suspend () -> Unit): Boolean = mutex.withLock {
        val quiet = (pending[tripId] ?: 0) == 0 && (versions[tripId] ?: 0) == since
        if (quiet) save()
        quiet
    }
}
