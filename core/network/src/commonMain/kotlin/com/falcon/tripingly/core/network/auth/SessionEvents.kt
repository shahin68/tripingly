package com.falcon.tripingly.core.network.auth

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

enum class SessionEndReason { REFRESH_REFUSED, ACCOUNT_SUSPENDED }

/** Emits when the server ends the session, so the app can sign out and show sign-in. */
class SessionEvents {
    private val ended = MutableSharedFlow<SessionEndReason>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    val sessionEnded: SharedFlow<SessionEndReason> = ended.asSharedFlow()

    internal fun end(reason: SessionEndReason) {
        ended.tryEmit(reason)
    }
}
