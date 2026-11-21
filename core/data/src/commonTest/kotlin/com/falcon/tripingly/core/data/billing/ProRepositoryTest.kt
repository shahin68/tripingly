package com.falcon.tripingly.core.data.billing

import com.falcon.tripingly.core.common.error.DataError
import com.falcon.tripingly.core.common.result.AppResult
import com.falcon.tripingly.core.data.account.SessionRepository
import com.falcon.tripingly.core.model.account.Account
import com.falcon.tripingly.core.model.account.OnboardingState
import com.falcon.tripingly.core.model.account.ProOffer
import com.falcon.tripingly.core.model.account.ProPeriod
import com.falcon.tripingly.core.model.account.SessionState
import com.falcon.tripingly.core.model.account.SignOutReason
import com.falcon.tripingly.core.model.account.TripVisibility
import com.falcon.tripingly.core.network.realtime.FakeRealtimeClient
import com.falcon.tripingly.core.network.realtime.RealtimeEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class ProRepositoryTest {

    private val yearly = ProOffer(id = "\$rc_annual", period = ProPeriod.Yearly, price = "€29.99")
    private val start = Instant.parse("2026-11-21T10:00:00Z")

    private class Store : StorePurchases {
        val calls = mutableListOf<String>()
        var bought = true
        var restoredPro = true
        var failure: DataError.Purchase? = null

        override suspend fun logIn(userId: String) {
            calls += "logIn $userId"
        }

        override suspend fun logOut() {
            calls += "logOut"
        }

        override suspend fun offers() = AppResult.Success(emptyList<ProOffer>())

        override suspend fun buy(offerId: String): AppResult<Boolean, DataError.Purchase> {
            calls += "buy $offerId"
            return failure?.let { AppResult.Error(it) } ?: AppResult.Success(bought)
        }

        override suspend fun restore(): AppResult<Boolean, DataError.Purchase> {
            calls += "restore"
            return AppResult.Success(restoredPro)
        }

        override suspend fun managementUrl() = null
    }

    /** The server's side: `POST /me/pro/sync` answers [storePro] (what the store has), or fails with [syncFailure]. */
    private class Session(userId: String = "u1") : SessionRepository {
        var syncs = 0
        var refreshes = 0
        var storePro: Pair<Set<String>, Instant?> = emptySet<String>() to null
        var syncFailure: DataError.Network? = null

        private fun account(id: String) = Account(
            id = id,
            username = "jonas",
            displayName = null,
            birthDate = null,
            locale = "en",
            defaultTripVisibility = TripVisibility.PUBLIC,
            isAdmin = false,
            onboarding = OnboardingState(completed = true, missingProfileFields = emptySet(), missingConsents = emptySet()),
            entitlements = emptySet(),
        )

        override val session = MutableStateFlow<SessionState>(SessionState.SignedIn(account(userId)))

        override fun updatePro(entitlements: Set<String>, proExpiresAt: Instant?) {
            val state = session.value as? SessionState.SignedIn ?: return
            session.value = state.copy(account = state.account.copy(entitlements = entitlements, proExpiresAt = proExpiresAt))
        }

        override suspend fun syncPro(): AppResult<Unit, DataError.Network> {
            syncs++
            syncFailure?.let { return AppResult.Error(it) }
            updatePro(storePro.first, storePro.second)
            return AppResult.Success(Unit)
        }

        override suspend fun refreshAccount(): AppResult<Account, DataError.Network> {
            refreshes++
            return AppResult.Success((session.value as SessionState.SignedIn).account)
        }

        override suspend fun restore() = AppResult.Success(Unit)
        override suspend fun signInWithGoogle(idToken: String) = AppResult.Success(Unit)
        override suspend fun signInWithApple(identityToken: String, authorizationCode: String, givenName: String?, familyName: String?) =
            AppResult.Success(Unit)
        override suspend fun signInForDevelopment(subject: String, name: String?) = AppResult.Success(Unit)
        override val developerSignInAvailable = false
        override suspend fun signOut(reason: SignOutReason?) {
            session.value = SessionState.SignedOut(reason)
        }
    }

    /** The test's virtual time as the wall clock, so ends come exactly when the test moves time on. */
    private fun TestScope.clock() = object : Clock {
        override fun now() = start + testScheduler.currentTime.milliseconds
    }

    private fun TestScope.pro(
        store: StorePurchases? = Store(),
        session: Session = Session(),
        realtime: FakeRealtimeClient = FakeRealtimeClient(),
    ) = DefaultProRepository(store, session, realtime, backgroundScope, clock())

    // Buying and restoring

    @Test
    fun buying_linksTheUserFirst_andAsksTheServerOnce() = runTest {
        val store = Store()
        val session = Session().apply { storePro = setOf("routes") to start + 365.days }
        val pro = pro(store, session)

        assertEquals(AppResult.Success(PurchaseOutcome.Active), pro.buy(yearly))
        assertEquals(listOf("logIn u1", "buy \$rc_annual"), store.calls)
        assertEquals(1, session.syncs)
        assertEquals(0, session.refreshes)
        runCurrent()
        assertTrue(pro.isPro.value)
    }

    @Test
    fun aPurchaseTheServerCantConfirmYet_isConfirming_andNeverPolled() = runTest {
        val session = Session().apply { syncFailure = DataError.Network.ServerError }
        val realtime = FakeRealtimeClient()
        val pro = pro(session = session, realtime = realtime)
        runCurrent()

        assertEquals(AppResult.Success(PurchaseOutcome.Confirming), pro.buy(yearly))
        advanceTimeBy(30.minutes)
        assertEquals(1, session.syncs)
        assertEquals(0, session.refreshes)
        assertFalse(pro.isPro.value)

        // The webhook reached the server: pro.changed turns Pro on here.
        realtime.send(RealtimeEvent.ProChanged(setOf("routes"), start + 365.days))
        runCurrent()
        assertTrue(pro.isPro.value)
    }

    @Test
    fun aPurchaseTheStoreHasntPassedOnYet_isConfirming() = runTest {
        val session = Session()
        val pro = pro(session = session)

        assertEquals(AppResult.Success(PurchaseOutcome.Confirming), pro.buy(yearly))
        assertEquals(1, session.syncs)
    }

    @Test
    fun closingTheStoreSheet_isCancelled_withoutAskingTheServer() = runTest {
        val store = Store().apply { bought = false }
        val session = Session()
        val pro = pro(store, session)

        assertEquals(AppResult.Success(PurchaseOutcome.Cancelled), pro.buy(yearly))
        assertEquals(0, session.syncs)
    }

    @Test
    fun storeErrors_comeBackAsTheyAre() = runTest {
        val store = Store().apply { failure = DataError.Purchase.Pending }
        val session = Session()
        val pro = pro(store, session)

        assertEquals(AppResult.Error(DataError.Purchase.Pending), pro.buy(yearly))
        assertEquals(0, session.syncs)
    }

    @Test
    fun restoringWithoutAPurchase_saysSo_andRestoringOne_isActive() = runTest {
        val store = Store().apply { restoredPro = false }
        val session = Session()
        val pro = pro(store, session)

        assertEquals(AppResult.Success(PurchaseOutcome.NothingToRestore), pro.restore())
        assertEquals(0, session.syncs)

        store.restoredPro = true
        session.storePro = setOf("routes") to null
        assertEquals(AppResult.Success(PurchaseOutcome.Active), pro.restore())
        assertEquals(1, session.syncs)
    }

    @Test
    fun theStoreCustomerIsLinkedOnce_andLoggedOutAtSignOut() = runTest {
        val store = Store()
        val pro = pro(store)

        pro.restore()
        pro.restore()
        pro.clear()
        // Nothing linked: nothing to log out.
        pro.clear()

        assertEquals(listOf("logIn u1", "restore", "restore", "logOut"), store.calls)
    }

    @Test
    fun withoutAStore_thereAreNoPurchases() = runTest {
        val pro = pro(store = null)

        assertFalse(pro.isAvailable)
        assertEquals(AppResult.Error(DataError.Purchase.Unavailable), pro.offers())
        assertEquals(AppResult.Error(DataError.Purchase.Unavailable), pro.buy(yearly))
        assertTrue(pro().isAvailable)
    }

    // isPro, the one answer

    @Test
    fun withoutEntitlements_thereIsNoPro() = runTest {
        val pro = pro()
        runCurrent()

        assertFalse(pro.isPro.value)
    }

    @Test
    fun aProAccountAtStart_isProFromTheFirstRead() = runTest {
        val session = Session().apply { updatePro(setOf("routes"), start + 30.days) }

        // No collection has run yet: the first value is already right.
        assertTrue(pro(session = session).isPro.value)
    }

    @Test
    fun proEndsByItself_exactlyWhenItRunsOut_withoutAskingTheServer() = runTest {
        val session = Session().apply { updatePro(setOf("routes"), start + 10.minutes) }
        val pro = pro(session = session)
        runCurrent()

        advanceTimeBy(10.minutes - 1.seconds)
        assertTrue(pro.isPro.value)
        advanceTimeBy(2.seconds)
        assertFalse(pro.isPro.value)
        assertEquals(0, session.syncs)
        assertEquals(0, session.refreshes)
    }

    @Test
    fun aRenewalBeforeTheEnd_keepsPro_untilTheNewEnd() = runTest {
        val session = Session().apply { updatePro(setOf("routes"), start + 10.minutes) }
        val realtime = FakeRealtimeClient()
        val pro = pro(session = session, realtime = realtime)
        runCurrent()

        advanceTimeBy(5.minutes)
        // The store renewed it: the server sends the new end.
        realtime.send(RealtimeEvent.ProChanged(setOf("routes"), start + 31.days))
        runCurrent()
        advanceTimeBy(10.minutes)
        assertTrue(pro.isPro.value)

        advanceTimeBy(31.days)
        assertFalse(pro.isPro.value)
    }

    @Test
    fun anEndAlreadyPassed_isNoPro() = runTest {
        val session = Session().apply { updatePro(setOf("routes"), start - 1.seconds) }
        val pro = pro(session = session)

        assertFalse(pro.isPro.value)
        runCurrent()
        assertFalse(pro.isPro.value)
    }

    @Test
    fun lifetime_neverEnds() = runTest {
        val session = Session().apply { updatePro(setOf("routes"), proExpiresAt = null) }
        val pro = pro(session = session)
        runCurrent()

        advanceTimeBy(3650.days)
        assertTrue(pro.isPro.value)
    }

    @Test
    fun aCancellationOrRefund_endsProAtOnce() = runTest {
        val session = Session().apply { updatePro(setOf("routes"), start + 30.days) }
        val realtime = FakeRealtimeClient()
        val pro = pro(session = session, realtime = realtime)
        runCurrent()

        realtime.send(RealtimeEvent.ProChanged(emptySet(), proExpiresAt = null))
        runCurrent()
        assertFalse(pro.isPro.value)
    }

    @Test
    fun theSnapshotOnConnect_catchesUpOnWhatChangedWhileAway() = runTest {
        val session = Session()
        val realtime = FakeRealtimeClient()
        val pro = pro(session = session, realtime = realtime)
        runCurrent()

        // Bought on another device while this one was offline: the reconnect's snapshot brings it.
        realtime.send(RealtimeEvent.ProChanged(setOf("routes"), start + 365.days))
        runCurrent()
        assertTrue(pro.isPro.value)
        assertEquals(0, session.syncs)
    }

    @Test
    fun aRefusedProCall_endsPro_likeTheServer() = runTest {
        val session = Session().apply { updatePro(setOf("routes"), start + 30.days) }
        val pro = pro(session = session)
        runCurrent()

        // What the HTTP client does on 403 PREMIUM_REQUIRED.
        session.updatePro(emptySet(), proExpiresAt = null)
        runCurrent()
        assertFalse(pro.isPro.value)
        assertEquals(0, session.refreshes)
    }

    @Test
    fun signingOut_endsPro() = runTest {
        val session = Session().apply { updatePro(setOf("routes"), proExpiresAt = null) }
        val pro = pro(session = session)
        runCurrent()

        session.signOut()
        runCurrent()
        assertFalse(pro.isPro.value)
    }
}
