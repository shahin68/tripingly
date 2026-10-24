package com.falcon.tripingly.feature.paywall

import com.falcon.tripingly.core.common.error.DataError
import com.falcon.tripingly.core.common.result.AppResult
import com.falcon.tripingly.core.data.account.AccountRepository
import com.falcon.tripingly.core.data.account.FakeAccountBackend
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
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
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

@OptIn(ExperimentalCoroutinesApi::class)
class PaywallViewModelTest {

    @BeforeTest
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private val monthly = ProOffer("\$rc_monthly", ProPeriod.Monthly, "€3.99")
    private val yearly = ProOffer("\$rc_annual", ProPeriod.Yearly, "€24.99")
    private val lifetime = ProOffer("\$rc_lifetime", ProPeriod.Lifetime, "€59.99")

    private class FakePro(
        override val isAvailable: Boolean = true,
        var offers: AppResult<List<ProOffer>, DataError.Purchase> = AppResult.Success(emptyList()),
        var outcome: AppResult<PurchaseOutcome, DataError.Purchase> = AppResult.Success(PurchaseOutcome.Active),
    ) : ProRepository {
        val bought = mutableListOf<ProOffer>()
        var restores = 0
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

    /** Onboarded, so the session is SignedIn; [entitlements] are its Pro features. */
    private suspend fun signedIn(entitlements: Set<String> = emptySet()) =
        FakeAccountBackend(today = { LocalDate(2026, 10, 20) }, entitlements = entitlements).also {
            it.signInForDevelopment("me", "Me")
            it.updateProfile(ProfileUpdate(username = "me", birthDate = LocalDate(1990, 1, 1)))
            it.accept((it.legalDocuments("en") as AppResult.Success).data)
        }

    private suspend fun viewModel(pro: ProRepository, account: FakeAccountBackend? = null) =
        (account ?: signedIn()).let { PaywallViewModel(sessionRepository = it, proRepository = pro, accountRepository = it, languageTag = { "en" }) }

    @Test
    fun opening_showsTheOffers_withYearlyPicked() = runTest {
        val viewModel = viewModel(FakePro(offers = AppResult.Success(listOf(lifetime, yearly, monthly))))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(listOf(monthly, yearly, lifetime), state.offers)
        assertEquals(yearly.id, state.selectedOfferId)
        assertFalse(state.hasPro)
        assertTrue(state.legalDocuments.isNotEmpty())
    }

    @Test
    fun buying_theChosenOffer_welcomesToPro() = runTest {
        val pro = FakePro(offers = AppResult.Success(listOf(monthly, yearly)))
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
        val pro = FakePro(offers = AppResult.Success(listOf(yearly)))
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
        val pro = FakePro(offers = AppResult.Success(listOf(yearly)), outcome = AppResult.Success(PurchaseOutcome.Cancelled))
        val viewModel = viewModel(pro)
        advanceUntilIdle()

        viewModel.onAction(Action.OnBuy)
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.message)
    }

    @Test
    fun aPendingPayment_isANote_andARefusalAnError() = runTest {
        val pro = FakePro(offers = AppResult.Success(listOf(yearly)), outcome = AppResult.Error(DataError.Purchase.Pending))
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
        val pro = FakePro(offers = AppResult.Error(DataError.Purchase.NoInternet))
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
        val viewModel = PaywallViewModel(account, FakePro(offers = AppResult.Success(listOf(yearly))), legal, languageTag = { "en" })
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
        val viewModel = viewModel(FakePro(), account = signedIn(entitlements = setOf("routes")))
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.hasPro)
        assertEquals("https://play.google.com/store/account/subscriptions", viewModel.uiState.value.managementUrl)
    }

    @Test
    fun aBuildWithoutPurchases_loadsNoOffers() = runTest {
        val viewModel = viewModel(FakePro(isAvailable = false))
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isAvailable)
        assertFalse(viewModel.uiState.value.isLoadingOffers)
        assertTrue(viewModel.uiState.value.offers.isEmpty())
    }
}
