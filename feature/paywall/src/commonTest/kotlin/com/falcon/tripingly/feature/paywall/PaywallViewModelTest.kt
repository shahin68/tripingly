package com.falcon.tripingly.feature.paywall

import com.falcon.tripingly.core.common.error.DataError
import com.falcon.tripingly.core.common.result.AppResult
import com.falcon.tripingly.core.data.account.AccountRepository
import com.falcon.tripingly.core.data.account.FakeAccountBackend
import com.falcon.tripingly.core.data.billing.FakeProRepository
import com.falcon.tripingly.core.data.billing.ProRepository
import com.falcon.tripingly.core.data.billing.PurchaseOutcome
import com.falcon.tripingly.core.model.account.LegalDocument
import com.falcon.tripingly.core.model.account.ProOffer
import com.falcon.tripingly.core.model.account.ProPeriod
import com.falcon.tripingly.core.model.account.ProfileUpdate
import com.falcon.tripingly.core.ui.UiText
import com.falcon.tripingly.feature.paywall.presentation.PaywallViewModel
import com.falcon.tripingly.feature.paywall.presentation.PaywallViewModel.Action
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class PaywallViewModelTest {

    @BeforeTest
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private val monthly = ProOffer("\$rc_monthly", ProPeriod.Monthly, "€3.99")
    private val yearly = ProOffer("\$rc_annual", ProPeriod.Yearly, "€24.99")
    private val lifetime = ProOffer("\$rc_lifetime", ProPeriod.Lifetime, "€59.99")

    /** The store; its Pro follows its own signed-in account with [entitlements]. */
    private suspend fun TestScope.fakePro(
        isAvailable: Boolean = true,
        offers: AppResult<List<ProOffer>, DataError.Purchase> = AppResult.Success(emptyList()),
        outcome: AppResult<PurchaseOutcome, DataError.Purchase> = AppResult.Success(PurchaseOutcome.Active),
        entitlements: Set<String> = emptySet(),
    ) = FakeProRepository(signedIn(entitlements), backgroundScope, isAvailable = isAvailable, offers = offers, outcome = outcome)

    /** Onboarded, so the session is SignedIn; [entitlements] are its Pro features. */
    private suspend fun signedIn(entitlements: Set<String> = emptySet()) =
        FakeAccountBackend(today = { LocalDate(2026, 10, 20) }, entitlements = entitlements).also {
            it.signInForDevelopment("me", "Me")
            it.updateProfile(ProfileUpdate(username = "me", birthDate = LocalDate(1990, 1, 1)))
            it.accept((it.legalDocuments("en") as AppResult.Success).data)
        }

    private suspend fun viewModel(pro: ProRepository, account: FakeAccountBackend? = null) =
        PaywallViewModel(proRepository = pro, accountRepository = account ?: signedIn(), languageTag = { "en" })

    @Test
    fun opening_showsTheOffers_withYearlyPicked() = runTest {
        val viewModel = viewModel(fakePro(offers = AppResult.Success(listOf(lifetime, yearly, monthly))))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(listOf(monthly, yearly, lifetime), state.offers)
        assertEquals(yearly.id, state.selectedOfferId)
        assertFalse(state.hasPro)
        assertTrue(state.legalDocuments.isNotEmpty())
    }

    @Test
    fun buying_theChosenOffer_welcomesToPro() = runTest {
        val pro = fakePro(offers = AppResult.Success(listOf(monthly, yearly)))
        val viewModel = viewModel(pro)
        advanceUntilIdle()

        viewModel.onAction(Action.OnSelectOffer(monthly.id))
        viewModel.onAction(Action.OnBuy)
        assertTrue(viewModel.uiState.value.isBuying)
        advanceUntilIdle()

        assertEquals(listOf(monthly), pro.bought)
        assertFalse(viewModel.uiState.value.isBuying)
        assertIs<UiText.Note>(viewModel.uiState.value.message)
    }

    @Test
    fun aSecondTapWhileBuying_buysOnce() = runTest {
        val pro = fakePro(offers = AppResult.Success(listOf(yearly)))
        val viewModel = viewModel(pro)
        advanceUntilIdle()

        viewModel.onAction(Action.OnBuy)
        viewModel.onAction(Action.OnBuy)
        viewModel.onAction(Action.OnRestore)
        advanceUntilIdle()

        assertEquals(1, pro.bought.size)
        assertEquals(0, pro.restores)
    }

    @Test
    fun closingTheStoreSheet_saysNothing() = runTest {
        val pro = fakePro(offers = AppResult.Success(listOf(yearly)), outcome = AppResult.Success(PurchaseOutcome.Cancelled))
        val viewModel = viewModel(pro)
        advanceUntilIdle()

        viewModel.onAction(Action.OnBuy)
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.message)
    }

    @Test
    fun aPendingPayment_isANote_andARefusalAnError() = runTest {
        val pro = fakePro(offers = AppResult.Success(listOf(yearly)), outcome = AppResult.Error(DataError.Purchase.Pending))
        val viewModel = viewModel(pro)
        advanceUntilIdle()

        viewModel.onAction(Action.OnBuy)
        advanceUntilIdle()
        assertIs<UiText.Note>(viewModel.uiState.value.message)

        viewModel.onAction(Action.OnDismissMessage)
        pro.outcome = AppResult.Error(DataError.Purchase.NotAllowed)
        viewModel.onAction(Action.OnRestore)
        advanceUntilIdle()
        assertIs<UiText.Resource>(viewModel.uiState.value.message)
    }

    @Test
    fun pricesThatFailToLoad_canBeRetried() = runTest {
        val pro = fakePro(offers = AppResult.Error(DataError.Purchase.NoInternet))
        val viewModel = viewModel(pro)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.hasOffersFailed)

        pro.offers = AppResult.Success(listOf(yearly))
        viewModel.onAction(Action.OnRetryOffers)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.hasOffersFailed)
        assertEquals(listOf(yearly), viewModel.uiState.value.offers)
    }

    @Test
    fun termsThatFailToLoad_holdBackTheOffers_untilARetryBringsBoth() = runTest {
        val account = signedIn()
        var legalFails = true
        val legal = object : AccountRepository by account {
            override suspend fun legalDocuments(locale: String): AppResult<List<LegalDocument>, DataError.Network> =
                if (legalFails) AppResult.Error(DataError.Network.NoInternet) else account.legalDocuments(locale)
        }
        val viewModel = PaywallViewModel(fakePro(offers = AppResult.Success(listOf(yearly))), legal, languageTag = { "en" })
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.hasOffersFailed)
        assertTrue(viewModel.uiState.value.offers.isEmpty())

        legalFails = false
        viewModel.onAction(Action.OnRetryOffers)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.hasOffersFailed)
        assertTrue(viewModel.uiState.value.legalDocuments.isNotEmpty())
        assertEquals(listOf(yearly), viewModel.uiState.value.offers)
    }

    @Test
    fun aProAccount_seesItActive_withTheStoresManagePage() = runTest {
        val viewModel = viewModel(fakePro(entitlements = setOf("routes")))
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.hasPro)
        assertEquals("https://play.google.com/store/account/subscriptions", viewModel.uiState.value.managementUrl)
    }

    @Test
    fun aProAccount_seesItActive_fromTheFirstFrame() = runTest {
        val pro = fakePro(entitlements = setOf("routes"))
        runCurrent()

        // Read at once, so the screen doesn't swap the offers for "active" while it slides in.
        assertTrue(viewModel(pro).uiState.value.hasPro)
    }

    @Test
    fun proEndingWhileOpen_showsTheOffersAgain_atOnce() = runTest {
        val account = signedIn(entitlements = setOf("routes"))
        val start = Instant.parse("2026-11-21T10:00:00Z")
        val clock = object : Clock {
            override fun now() = start + testScheduler.currentTime.milliseconds
        }
        account.updatePro(setOf("routes"), proExpiresAt = start + 1.minutes)
        val viewModel = viewModel(FakeProRepository(account, backgroundScope, clock))
        advanceTimeBy(59.seconds)
        assertTrue(viewModel.uiState.value.hasPro)

        // Ends on time, with nothing asked.
        advanceTimeBy(2.seconds)
        assertFalse(viewModel.uiState.value.hasPro)

        // Renewed (pro.changed): active again.
        account.updatePro(setOf("routes"), proExpiresAt = start + 31.days)
        runCurrent()
        assertTrue(viewModel.uiState.value.hasPro)
    }

    @Test
    fun aBuildWithoutPurchases_loadsNoOffers() = runTest {
        val viewModel = viewModel(fakePro(isAvailable = false))
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isAvailable)
        assertFalse(viewModel.uiState.value.isLoadingOffers)
        assertTrue(viewModel.uiState.value.offers.isEmpty())
    }
}
