package com.falcon.tripingly.core.data.billing

import com.falcon.tripingly.core.model.account.Account
import com.falcon.tripingly.core.model.account.SessionState
import com.falcon.tripingly.core.model.account.hasPro
import kotlin.time.Clock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transformLatest

/**
 * Whether the signed-in account has Pro right now: what the server last sent, until it ends. At [Account.proExpiresAt]
 * Pro turns off by itself, without asking the server; a renewal reaches the account before that (`pro.changed`).
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun Flow<SessionState>.proNow(clock: Clock): Flow<Boolean> =
    map { it.proAccount() }
        .distinctUntilChanged { old, new -> old?.entitlements == new?.entitlements && old?.proExpiresAt == new?.proExpiresAt }
        .transformLatest { account ->
            val end = account?.proExpiresAt
            when {
                account == null -> emit(false)
                end == null -> emit(true)
                else -> {
                    val left = end - clock.now()
                    if (left.isPositive()) {
                        emit(true)
                        delay(left)
                    }
                    emit(false)
                }
            }
        }
        .distinctUntilChanged()

/** [proNow] at this moment. */
fun SessionState.hasProNow(clock: Clock): Boolean {
    val account = proAccount() ?: return false
    return account.proExpiresAt.let { it == null || it > clock.now() }
}

private fun SessionState.proAccount(): Account? = (this as? SessionState.SignedIn)?.account?.takeIf(Account::hasPro)
