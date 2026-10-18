package com.falcon.tripingly.core.data.trips

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Tracks local changes to a trip that the server hasn't confirmed yet, so a
 * reload from the server doesn't overwrite them. A reload saves only when no
 * change is pending and none started while it was loading. A reload or a live
 * change that couldn't be saved has the trip marked stale, and the marker queue
 * reloads it once it has sent everything ([takeStale]).
 */
internal class PendingTripWrites {
    private val mutex = Mutex()
    private val pending = mutableMapOf<String, Int>()
    private val versions = mutableMapOf<String, Long>()
    private val stale = mutableSetOf<String>()

    /** Trips with changes on their way. */
    private val unsent = MutableStateFlow(emptySet<String>())

    /** Marks a change as started and applies [applyLocally] in the same step. */
    suspend fun begin(tripId: String, applyLocally: suspend () -> Unit) = mutex.withLock {
        pending[tripId] = (pending[tripId] ?: 0) + 1
        versions[tripId] = (versions[tripId] ?: 0) + 1
        unsent.update { it + tripId }
        applyLocally()
    }

    /** Marks a change as done; true when it was the trip's last pending one. */
    suspend fun end(tripId: String): Boolean = mutex.withLock {
        val left = (pending[tripId] ?: 1) - 1
        if (left <= 0) pending.remove(tripId) else pending[tripId] = left
        if (left <= 0) unsent.update { it - tripId }
        left <= 0
    }

    /** Returns once every change to the trip has reached the server, or failed and was undone. */
    suspend fun awaitSent(tripId: String) {
        unsent.first { tripId !in it }
    }

    suspend fun version(tripId: String): Long = mutex.withLock { versions[tripId] ?: 0 }

    /** Runs [save] unless a change is pending or started after [since]; true when it ran. */
    suspend fun saveIfQuiet(tripId: String, since: Long, save: suspend () -> Unit): Boolean = mutex.withLock {
        val quiet = (pending[tripId] ?: 0) == 0 && (versions[tripId] ?: 0) == since
        if (quiet) save()
        quiet
    }

    /** Has the trip reloaded once every pending change is sent (see [takeStale]). */
    suspend fun reloadWhenSent(tripId: String) = mutex.withLock { stale += tripId }

    /** Runs [save] unless a change is pending (a change someone else made, from the live connection). */
    suspend fun saveIfNothingPending(tripId: String, save: suspend () -> Unit): Boolean = mutex.withLock {
        val quiet = (pending[tripId] ?: 0) == 0
        if (quiet) save() else stale += tripId
        quiet
    }

    /** True once after something from the server couldn't be saved; the trip needs a reload. */
    suspend fun takeStale(tripId: String): Boolean = mutex.withLock { stale.remove(tripId) }
}
