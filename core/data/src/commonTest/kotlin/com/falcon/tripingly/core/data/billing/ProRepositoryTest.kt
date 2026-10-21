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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProRepositoryTest {

    private val yearly = ProOffer(id = "\$rc_annual", period = ProPeriod.Yearly, price = "€29.99")

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

    /** `GET /me` answers Pro from its [proAfter]th call on. */
    private class Session(userId: String = "u1", var proAfter: Int = 1) : SessionRepository {
        var refreshes = 0
        private fun account(pro: Boolean) = Account(
            id = "u1",
            username = "jonas",
            displayName = null,
            birthDate = null,
            locale = "en",
            defaultTripVisibility = TripVisibility.PUBLIC,
            isAdmin = false,
            onboarding = OnboardingState(completed = true, missingProfileFields = emptySet(), missingConsents = emptySet()),
            entitlements = if (pro) setOf("routes") else emptySet(),
        )

        override val session = MutableStateFlow<SessionState>(SessionState.SignedIn(account(false).copy(id = userId)))
        override suspend fun refreshAccount(): AppResult<Account, DataError.Network> {
            refreshes++
            return AppResult.Success(account(refreshes >= proAfter))
        }

        override suspend fun restore() = AppResult.Success(Unit)
        override suspend fun signInWithGoogle(idToken: String) = AppResult.Success(Unit)
        override suspend fun signInWithApple(identityToken: String, authorizationCode: String, givenName: String?, familyName: String?) =
            AppResult.Success(Unit)
        override suspend fun signInForDevelopment(subject: String, name: String?) = AppResult.Success(Unit)
        override val developerSignInAvailable = false
        override suspend fun signOut(reason: SignOutReason?) = Unit
    }

    @Test
    fun buying_linksTheUserFirst_andWaitsUntilTheServerHasPro() = runTest {
        val store = Store()
        val session = Session(proAfter = 3)
        val pro = DefaultProRepository(store, session, backgroundScope)

        assertEquals(AppResult.Success(PurchaseOutcome.Active), pro.buy(yearly))
        assertEquals(listOf("logIn u1", "buy \$rc_annual"), store.calls)
        assertEquals(3, session.refreshes)
    }

    @Test
    fun aPurchaseTheServerHasntHeardOfYet_isConfirming() = runTest {
        val session = Session(proAfter = Int.MAX_VALUE)
        val pro = DefaultProRepository(Store(), session, backgroundScope)

        assertEquals(AppResult.Success(PurchaseOutcome.Confirming), pro.buy(yearly))
        assertEquals(5, session.refreshes)
    }

    @Test
    fun closingTheStoreSheet_isCancelled_withoutAskingTheServer() = runTest {
        val store = Store().apply { bought = false }
        val session = Session()
        val pro = DefaultProRepository(store, session, backgroundScope)

        assertEquals(AppResult.Success(PurchaseOutcome.Cancelled), pro.buy(yearly))
        assertEquals(0, session.refreshes)
    }

    @Test
    fun storeErrors_comeBackAsTheyAre() = runTest {
        val store = Store().apply { failure = DataError.Purchase.Pending }
        val pro = DefaultProRepository(store, Session(), backgroundScope)

        assertEquals(AppResult.Error(DataError.Purchase.Pending), pro.buy(yearly))
    }

    @Test
    fun restoringWithoutAPurchase_saysSo() = runTest {
        val store = Store().apply { restoredPro = false }
        val session = Session()
        val pro = DefaultProRepository(store, session, backgroundScope)

        assertEquals(AppResult.Success(PurchaseOutcome.NothingToRestore), pro.restore())
        assertEquals(0, session.refreshes)

        store.restoredPro = true
        assertEquals(AppResult.Success(PurchaseOutcome.Active), pro.restore())
    }

    @Test
    fun theStoreCustomerIsLinkedOnce_andLoggedOutAtSignOut() = runTest {
        val store = Store()
        val pro = DefaultProRepository(store, Session(), backgroundScope)

        pro.restore()
        pro.restore()
        pro.clear()
        // Nothing linked: nothing to log out.
        pro.clear()

        assertEquals(listOf("logIn u1", "restore", "restore", "logOut"), store.calls)
    }

    @Test
    fun withoutAStore_thereAreNoPurchases() = runTest {
        val pro = DefaultProRepository(null, Session(), backgroundScope)

        assertFalse(pro.isAvailable)
        assertEquals(AppResult.Error(DataError.Purchase.Unavailable), pro.offers())
        assertEquals(AppResult.Error(DataError.Purchase.Unavailable), pro.buy(yearly))
        assertTrue(DefaultProRepository(Store(), Session(), backgroundScope).isAvailable)
    }
}
