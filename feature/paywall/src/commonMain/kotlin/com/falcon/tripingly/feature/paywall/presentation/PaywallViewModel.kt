package com.falcon.tripingly.feature.paywall.presentation

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.falcon.tripingly.core.common.error.DataError
import com.falcon.tripingly.core.common.result.AppResult
import com.falcon.tripingly.core.data.account.AccountRepository
import com.falcon.tripingly.core.data.account.SessionRepository
import com.falcon.tripingly.core.data.billing.ProRepository
import com.falcon.tripingly.core.data.billing.PurchaseOutcome
import com.falcon.tripingly.core.model.account.LegalDocument
import com.falcon.tripingly.core.model.account.ProOffer
import com.falcon.tripingly.core.model.account.ProPeriod
import com.falcon.tripingly.core.model.account.SessionState
import com.falcon.tripingly.core.model.account.hasPro
import com.falcon.tripingly.core.ui.UiText
import com.falcon.tripingly.feature.paywall.generated.resources.Res
import com.falcon.tripingly.feature.paywall.generated.resources.paywall_confirming
import com.falcon.tripingly.feature.paywall.generated.resources.paywall_error_no_internet
import com.falcon.tripingly.feature.paywall.generated.resources.paywall_error_not_allowed
import com.falcon.tripingly.feature.paywall.generated.resources.paywall_error_purchase
import com.falcon.tripingly.feature.paywall.generated.resources.paywall_error_restore
import com.falcon.tripingly.feature.paywall.generated.resources.paywall_nothing_to_restore
import com.falcon.tripingly.feature.paywall.generated.resources.paywall_pending
import com.falcon.tripingly.feature.paywall.generated.resources.paywall_welcome
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource

/**
 * Tripinly Pro: what it unlocks, the store's offers, buying and restoring. Pro is on only once the
 * server's `GET /me` says so; the screen then shows it as active.
 */
class PaywallViewModel(
    private val sessionRepository: SessionRepository,
    private val proRepository: ProRepository,
    private val accountRepository: AccountRepository,
    private val languageTag: () -> String,
) : ViewModel() {

    private val _uiState = MutableStateFlow(State(isAvailable = proRepository.isAvailable))
    val uiState: StateFlow<State> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            sessionRepository.session.collect { session ->
                val hasPro = (session as? SessionState.SignedIn)?.account?.hasPro == true
                _uiState.update { it.copy(hasPro = hasPro) }
                if (hasPro) loadManagementUrl()
            }
        }
        if (proRepository.isAvailable) loadOffers()
        viewModelScope.launch {
            val documents = accountRepository.legalDocuments(languageTag())
            if (documents is AppResult.Success) _uiState.update { it.copy(legalDocuments = documents.data.toImmutableList()) }
        }
    }

    fun onAction(action: Action) {
        when (action) {
            is Action.OnSelectOffer -> _uiState.update { it.copy(selectedOfferId = action.offerId) }
            Action.OnBuy -> buy()
            Action.OnRestore -> restore()
            Action.OnRetryOffers -> loadOffers()
            Action.OnDismissMessage -> _uiState.update { it.copy(message = null) }
        }
    }

    private fun loadOffers() {
        _uiState.update { it.copy(isLoadingOffers = true, hasOffersFailed = false) }
        viewModelScope.launch {
            when (val result = proRepository.offers()) {
                is AppResult.Success -> {
                    val offers = result.data.sortedBy { it.period.ordinal }
                    _uiState.update { state ->
                        state.copy(
                            offers = offers.toImmutableList(),
                            // The yearly plan is the one most people pick.
                            selectedOfferId = state.selectedOfferId ?: (offers.firstOrNull { it.period == ProPeriod.Yearly } ?: offers.firstOrNull())?.id,
                            isLoadingOffers = false,
                        )
                    }
                }
                is AppResult.Error -> _uiState.update { it.copy(isLoadingOffers = false, hasOffersFailed = true) }
            }
        }
    }

    private fun buy() {
        val state = _uiState.value
        if (state.isBusy) return
        val offer = state.offers.firstOrNull { it.id == state.selectedOfferId } ?: return
        _uiState.update { it.copy(isBuying = true) }
        viewModelScope.launch {
            val result = proRepository.buy(offer)
            _uiState.update { it.copy(isBuying = false, message = result.message(failed = Res.string.paywall_error_purchase)) }
        }
    }

    private fun restore() {
        if (_uiState.value.isBusy) return
        _uiState.update { it.copy(isRestoring = true) }
        viewModelScope.launch {
            val result = proRepository.restore()
            _uiState.update { it.copy(isRestoring = false, message = result.message(failed = Res.string.paywall_error_restore)) }
        }
    }

    private fun loadManagementUrl() {
        if (_uiState.value.managementUrl != null) return
        viewModelScope.launch {
            val url = proRepository.managementUrl()
            _uiState.update { it.copy(managementUrl = url) }
        }
    }

    private fun AppResult<PurchaseOutcome, DataError.Purchase>.message(failed: StringResource): UiText? = when (this) {
        is AppResult.Success -> when (data) {
            PurchaseOutcome.Active -> UiText.Note(UiText.Resource(Res.string.paywall_welcome))
            PurchaseOutcome.Confirming -> UiText.Note(UiText.Resource(Res.string.paywall_confirming))
            PurchaseOutcome.NothingToRestore -> UiText.Note(UiText.Resource(Res.string.paywall_nothing_to_restore))
            PurchaseOutcome.Cancelled -> null
        }
        is AppResult.Error -> when (val error = error) {
            DataError.Purchase.Pending -> UiText.Note(UiText.Resource(Res.string.paywall_pending))
            DataError.Purchase.NotAllowed -> UiText.Resource(Res.string.paywall_error_not_allowed)
            DataError.Purchase.NoInternet -> UiText.Joined(UiText.Resource(failed), UiText.Resource(Res.string.paywall_error_no_internet))
            DataError.Purchase.Unavailable -> UiText.Resource(failed)
            // The store's own explanation, already in the person's language.
            is DataError.Purchase.Store -> error.message?.let { UiText.Joined(UiText.Resource(failed), UiText.Server(it)) } ?: UiText.Resource(failed)
        }
    }

    @Immutable
    data class State(
        /** False where this build has no purchases. */
        val isAvailable: Boolean,
        val hasPro: Boolean = false,
        val offers: ImmutableList<ProOffer> = persistentListOf(),
        val selectedOfferId: String? = null,
        val isLoadingOffers: Boolean = false,
        val hasOffersFailed: Boolean = false,
        val isBuying: Boolean = false,
        val isRestoring: Boolean = false,
        /** The store's page to manage or cancel the subscription, once Pro is on. */
        val managementUrl: String? = null,
        val legalDocuments: ImmutableList<LegalDocument> = persistentListOf(),
        val message: UiText? = null,
    ) {
        val isBusy: Boolean get() = isBuying || isRestoring
    }

    sealed interface Action {
        data class OnSelectOffer(val offerId: String) : Action
        data object OnBuy : Action
        data object OnRestore : Action
        data object OnRetryOffers : Action
        data object OnDismissMessage : Action
    }
}
