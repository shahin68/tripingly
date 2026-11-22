package com.falcon.tripingly.core.network.auth

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

enum class SessionEndReason { REFRESH_REFUSED, ACCOUNT_SUSPENDED }

/**
 * Emits when the server ends the session, so the app can sign out and show sign-in, and when it says a Pro
 * feature needs Pro the account no longer has ([proEnded]), so Pro ends in the app too, without asking again.
 */
class SessionEvents {
    private val ended = MutableSharedFlow<SessionEndReason>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    val sessionEnded: SharedFlow<SessionEndReason> = ended.asSharedFlow()

    private val pro = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** A request was refused with `403 PREMIUM_REQUIRED`: Pro has ended (or was never there). */
    val proEnded: SharedFlow<Unit> = pro.asSharedFlow()

    internal fun end(reason: SessionEndReason) {
        ended.tryEmit(reason)
    }

    internal fun proEnded() {
        pro.tryEmit(Unit)
    }
}
