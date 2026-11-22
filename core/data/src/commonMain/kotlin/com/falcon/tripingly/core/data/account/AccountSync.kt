package com.falcon.tripingly.core.data.account

import com.falcon.tripingly.core.network.realtime.RealtimeClient
import com.falcon.tripingly.core.network.realtime.RealtimeEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch

/**
 * The account in the background, once the app opened from the saved one (Shahin, 2026-11-22): `GET /me` each time
 * the live connection connects (the start, every return to the app, back online) and on `me.changed` (a change on
 * another device). A failure changes nothing; a refused session signs out through the token refresh as anywhere.
 */
internal class AccountSync(
    private val session: SessionRepository,
    private val realtime: RealtimeClient,
    private val scope: CoroutineScope,
) {
    private var listening: Job? = null

    fun start() {
        if (listening != null) return
        listening = scope.launch {
            merge(realtime.connections, realtime.events.filterIsInstance<RealtimeEvent.MeChanged>().map { })
                .collect { session.refreshAccount() }
        }
    }
}
