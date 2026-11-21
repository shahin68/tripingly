package com.falcon.tripingly.core.data.billing

import com.falcon.tripingly.core.common.error.DataError
import com.falcon.tripingly.core.common.result.AppResult
import com.falcon.tripingly.core.data.account.SessionRepository
import com.falcon.tripingly.core.model.account.ProOffer
import com.falcon.tripingly.core.model.account.SessionState
import com.falcon.tripingly.core.network.realtime.RealtimeClient
import com.falcon.tripingly.core.network.realtime.RealtimeEvent
import kotlin.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Tripinly Pro: whether it's on ([isPro], the one answer the whole app reads) and buying it. The store takes the
 * payment and the server decides: the app never unlocks anything itself.
 */
interface ProRepository {
    /**
     * Pro right now, live: from the account the server last sent (`GET /me`, `pro.changed` on every connect and
     * change, a refused Pro call), off by itself when it ends. Asks the server nothing.
     */
    val isPro: StateFlow<Boolean>

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
    /** The server has Pro on. */
    Active,

    /** Paid, but the server couldn't confirm it yet; Pro turns on by itself once it does (`pro.changed`). */
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
    realtime: RealtimeClient,
    private val scope: CoroutineScope,
    private val clock: Clock = Clock.System,
) : ProRepository {

    private val lock = Mutex()
    private var linkedUserId: String? = null

    override val isPro: StateFlow<Boolean> =
        session.session.proNow(clock).stateIn(scope, SharingStarted.Eagerly, session.session.value.hasProNow(clock))

    override val isAvailable: Boolean get() = store != null

    init {
        // Sent on every (re)connect, so time offline or in the background is covered too.
        scope.launch {
            realtime.events.filterIsInstance<RealtimeEvent.ProChanged>().collect { session.updatePro(it.entitlements, it.proExpiresAt) }
        }
    }

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
            is AppResult.Success -> AppResult.Success(if (result.data) confirm() else PurchaseOutcome.Cancelled)
            is AppResult.Error -> result
        }
    }

    override suspend fun restore(): AppResult<PurchaseOutcome, DataError.Purchase> {
        val store = store ?: return AppResult.Error(DataError.Purchase.Unavailable)
        link(store)
        return when (val result = store.restore()) {
            is AppResult.Success -> AppResult.Success(if (result.data) confirm() else PurchaseOutcome.NothingToRestore)
            is AppResult.Error -> result
        }
    }

    override suspend fun managementUrl(): String? = store?.managementUrl()

    /** The next account starts as the store's anonymous customer. */
    suspend fun clear() = lock.withLock {
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
     * One ask: the server reads the store now instead of waiting for its webhook. If it can't yet (offline, store
     * slow), the webhook still reaches the server and `pro.changed` turns Pro on here: no polling.
     */
    private suspend fun confirm(): PurchaseOutcome {
        session.syncPro()
        return if (session.session.value.hasProNow(clock)) PurchaseOutcome.Active else PurchaseOutcome.Confirming
    }

    private fun currentUserId(): String? = when (val state = session.session.value) {
        is SessionState.SignedIn -> state.account.id
        is SessionState.Onboarding -> state.account.id
        else -> null
    }
}
