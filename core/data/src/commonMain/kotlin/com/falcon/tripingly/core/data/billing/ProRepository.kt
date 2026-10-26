package com.falcon.tripingly.core.data.billing

import com.falcon.tripingly.core.common.error.DataError
import com.falcon.tripingly.core.common.result.AppResult
import com.falcon.tripingly.core.data.account.SessionRepository
import com.falcon.tripingly.core.model.account.ProOffer
import com.falcon.tripingly.core.model.account.SessionState
import com.falcon.tripingly.core.model.account.hasPro
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Buying Tripinly Pro. The store takes the payment, RevenueCat tells the server (webhook), and the
 * server's `GET /me` turns Pro on: the app never unlocks anything itself.
 */
interface ProRepository {
    /** False where this build has no purchases (no RevenueCat key, or the fake API). */
    val isAvailable: Boolean

    /** Links the signed-in user to the store's customer; called once signed in. */
    fun start()

    suspend fun offers(): AppResult<List<ProOffer>, DataError.Purchase>

    suspend fun buy(offer: ProOffer): AppResult<PurchaseOutcome, DataError.Purchase>

    suspend fun restore(): AppResult<PurchaseOutcome, DataError.Purchase>

    suspend fun managementUrl(): String?
}

enum class PurchaseOutcome {
    /** The server has Pro on: `GET /me` says so. */
    Active,

    /** Paid, but the server hasn't heard from the store yet; checked again in the background until Pro turns on. */
    Confirming,

    /** The person closed the store's sheet. */
    Cancelled,

    /** Restoring found no purchase on this store account. */
    NothingToRestore,
}

internal class DefaultProRepository(
    /** Null where the build has no purchases. */
    private val store: StorePurchases?,
    private val session: SessionRepository,
    private val scope: CoroutineScope,
) : ProRepository {

    private val lock = Mutex()
    private var linkedUserId: String? = null
    private var laterChecks: Job? = null

    override val isAvailable: Boolean get() = store != null

    override fun start() {
        val store = store ?: return
        scope.launch { link(store) }
    }

    override suspend fun offers(): AppResult<List<ProOffer>, DataError.Purchase> =
        store?.offers() ?: AppResult.Error(DataError.Purchase.Unavailable)

    override suspend fun buy(offer: ProOffer): AppResult<PurchaseOutcome, DataError.Purchase> {
        val store = store ?: return AppResult.Error(DataError.Purchase.Unavailable)
        // A purchase must belong to this user, never to the store's anonymous customer.
        link(store)
        return when (val result = store.buy(offer.id)) {
            is AppResult.Success -> AppResult.Success(if (result.data) awaitPro() else PurchaseOutcome.Cancelled)
            is AppResult.Error -> result
        }
    }

    override suspend fun restore(): AppResult<PurchaseOutcome, DataError.Purchase> {
        val store = store ?: return AppResult.Error(DataError.Purchase.Unavailable)
        link(store)
        return when (val result = store.restore()) {
            is AppResult.Success -> AppResult.Success(if (result.data) awaitPro() else PurchaseOutcome.NothingToRestore)
            is AppResult.Error -> result
        }
    }

    override suspend fun managementUrl(): String? = store?.managementUrl()

    /** The next account starts as the store's anonymous customer. */
    suspend fun clear() = lock.withLock {
        laterChecks?.cancel()
        if (linkedUserId == null) return@withLock
        store?.logOut()
        linkedUserId = null
    }

    private suspend fun link(store: StorePurchases) = lock.withLock {
        val userId = currentUserId() ?: return@withLock
        if (userId == linkedUserId) return@withLock
        store.logIn(userId)
        linkedUserId = userId
    }

    /**
     * The webhook usually reaches the server within seconds; `GET /me` is asked a few times until it says Pro.
     * When it's late (RevenueCat retries a failed delivery), `GET /me` is asked less and less often in the
     * background, and the session turns Pro on wherever it's shown.
     */
    private suspend fun awaitPro(): PurchaseOutcome {
        repeat(PRO_CHECKS) { check ->
            if (check > 0) delay(PRO_CHECK_INTERVAL_MILLIS)
            if (hasPro()) return PurchaseOutcome.Active
        }
        if (laterChecks?.isActive != true) {
            laterChecks = scope.launch {
                for (wait in LATER_CHECK_WAITS_MILLIS) {
                    delay(wait)
                    if (hasPro()) return@launch
                }
            }
        }
        return PurchaseOutcome.Confirming
    }

    private suspend fun hasPro(): Boolean = (session.refreshAccount() as? AppResult.Success)?.data?.hasPro == true

    private fun currentUserId(): String? = when (val state = session.session.value) {
        is SessionState.SignedIn -> state.account.id
        is SessionState.Onboarding -> state.account.id
        else -> null
    }

    private companion object {
        const val PRO_CHECKS = 5
        const val PRO_CHECK_INTERVAL_MILLIS = 2_000L

        /** About 10 minutes in all, which covers RevenueCat's first retries. */
        val LATER_CHECK_WAITS_MILLIS = listOf(10_000L, 20_000L, 30_000L, 60_000L, 120_000L, 360_000L)
    }
}
