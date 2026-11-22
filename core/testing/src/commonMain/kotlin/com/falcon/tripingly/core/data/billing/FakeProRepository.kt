package com.falcon.tripingly.core.data.billing

import com.falcon.tripingly.core.common.error.DataError
import com.falcon.tripingly.core.common.result.AppResult
import com.falcon.tripingly.core.data.account.SessionRepository
import com.falcon.tripingly.core.model.account.ProOffer
import kotlin.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/** The store in memory; [isPro] follows [session]'s account exactly like the app's (until it ends). */
class FakeProRepository(
    session: SessionRepository,
    scope: CoroutineScope,
    clock: Clock = Clock.System,
    override val isAvailable: Boolean = true,
    var offers: AppResult<List<ProOffer>, DataError.Purchase> = AppResult.Success(emptyList()),
    var outcome: AppResult<PurchaseOutcome, DataError.Purchase> = AppResult.Success(PurchaseOutcome.Active),
) : ProRepository {
    val bought = mutableListOf<ProOffer>()
    var restores = 0

    override val isPro: StateFlow<Boolean> =
        session.session.proNow(clock).stateIn(scope, SharingStarted.Eagerly, session.session.value.hasProNow(clock))

    override fun start() = Unit

    override suspend fun offers() = offers

    override suspend fun buy(offer: ProOffer): AppResult<PurchaseOutcome, DataError.Purchase> {
        bought += offer
        return outcome
    }

    override suspend fun restore(): AppResult<PurchaseOutcome, DataError.Purchase> {
        restores++
        return outcome
    }

    override suspend fun managementUrl() = "https://play.google.com/store/account/subscriptions"
}
