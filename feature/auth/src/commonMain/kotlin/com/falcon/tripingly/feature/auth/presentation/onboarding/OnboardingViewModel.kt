package com.falcon.tripingly.feature.auth.presentation.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.falcon.tripingly.core.common.error.DataError
import com.falcon.tripingly.core.common.result.AppResult
import com.falcon.tripingly.core.data.account.AccountRepository
import com.falcon.tripingly.core.data.account.SessionRepository
import com.falcon.tripingly.core.model.account.Account
import com.falcon.tripingly.core.model.account.LegalDocument
import com.falcon.tripingly.core.model.account.MINIMUM_AGE
import com.falcon.tripingly.core.model.account.ProfileField
import com.falcon.tripingly.core.model.account.ProfileUpdate
import com.falcon.tripingly.core.model.account.SessionState
import com.falcon.tripingly.core.model.account.UsernameAvailability
import com.falcon.tripingly.core.model.account.isAtLeast
import com.falcon.tripingly.core.model.account.isUsernameFormatValid
import com.falcon.tripingly.feature.auth.presentation.common.UiMessage
import com.falcon.tripingly.feature.auth.presentation.common.toUiMessage
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

/**
 * Profile (display name, username, birth date), then the required legal documents.
 * The step follows the account's onboarding state, so an interrupted onboarding
 * resumes where it stopped.
 */
internal class OnboardingViewModel(
    private val sessionRepository: SessionRepository,
    private val accountRepository: AccountRepository,
    private val today: () -> LocalDate,
    private val languageTag: () -> String,
) : ViewModel() {

    enum class Step { PROFILE, CONSENT }

    enum class UsernameStatus { IDLE, CHECKING, AVAILABLE, TAKEN, INVALID }

    data class DocumentItem(val document: LegalDocument, val accepted: Boolean)

    data class State(
        val step: Step = Step.PROFILE,
        val displayName: String = "",
        val username: String = "",
        val usernameStatus: UsernameStatus = UsernameStatus.IDLE,
        val birthDate: LocalDate? = null,
        /** The quick local check; the server decides. */
        val isUnderAge: Boolean = false,
        val isBirthDatePickerVisible: Boolean = false,
        val isSaving: Boolean = false,
        val isLoadingDocuments: Boolean = false,
        val documents: ImmutableList<DocumentItem> = persistentListOf(),
        val error: UiMessage? = null,
    ) {
        val canSaveProfile: Boolean
            get() = !isSaving && displayName.isNotBlank() && birthDate != null &&
                usernameStatus == UsernameStatus.AVAILABLE

        val canAccept: Boolean
            get() = !isSaving && documents.isNotEmpty() && documents.all { it.accepted }
    }

    sealed interface Action {
        data class OnDisplayNameChange(val value: String) : Action
        data class OnUsernameChange(val value: String) : Action
        data object OnBirthDateClick : Action
        data class OnBirthDatePicked(val date: LocalDate?) : Action
        data object OnSaveProfileClick : Action
        data class OnDocumentAcceptedChange(val document: LegalDocument, val accepted: Boolean) : Action
        data object OnAcceptClick : Action
        data object OnRetryDocumentsClick : Action
        data object OnDismissError : Action
        data object OnSignOutClick : Action
    }

    private val _uiState = MutableStateFlow(State())
    val uiState: StateFlow<State> = _uiState.asStateFlow()

    private var account: Account? = null
    private var usernameCheck: Job? = null

    init {
        viewModelScope.launch {
            sessionRepository.session.filterIsInstance<SessionState.Onboarding>().collect { onAccount(it.account) }
        }
    }

    fun onAction(action: Action) {
        when (action) {
            is Action.OnDisplayNameChange -> _uiState.update { it.copy(displayName = action.value) }
            is Action.OnUsernameChange -> onUsernameChange(action.value)
            Action.OnBirthDateClick -> _uiState.update { it.copy(isBirthDatePickerVisible = true) }
            is Action.OnBirthDatePicked -> _uiState.update { state ->
                val date = action.date ?: state.birthDate
                state.copy(
                    birthDate = date,
                    isUnderAge = date != null && !date.isAtLeast(MINIMUM_AGE, today()),
                    isBirthDatePickerVisible = false,
                )
            }
            Action.OnSaveProfileClick -> saveProfile()
            is Action.OnDocumentAcceptedChange -> _uiState.update { state ->
                state.copy(
                    documents = state.documents.map {
                        if (it.document == action.document) it.copy(accepted = action.accepted) else it
                    }.toImmutableList(),
                )
            }
            Action.OnAcceptClick -> accept()
            Action.OnRetryDocumentsClick -> loadDocuments()
            Action.OnDismissError -> _uiState.update { it.copy(error = null) }
            Action.OnSignOutClick -> viewModelScope.launch { sessionRepository.signOut() }
        }
    }

    private fun onAccount(account: Account) {
        // This view model outlives sign-out, so another account starts from a clean form.
        val isNewAccount = this.account?.id != account.id
        this.account = account
        val step = if (account.onboarding.missingProfileFields.isEmpty()) Step.CONSENT else Step.PROFILE
        if (isNewAccount) {
            usernameCheck?.cancel()
            _uiState.value = State(
                step = step,
                displayName = account.displayName.orEmpty(),
                username = account.username.orEmpty(),
                usernameStatus = if (account.username != null) UsernameStatus.AVAILABLE else UsernameStatus.IDLE,
                birthDate = account.birthDate,
            )
        } else {
            _uiState.update { it.copy(step = step) }
        }
        if (step == Step.CONSENT && _uiState.value.documents.isEmpty()) loadDocuments()
    }

    private fun onUsernameChange(value: String) {
        val username = value.trim().lowercase()
        _uiState.update { it.copy(username = username) }
        usernameCheck?.cancel()
        when {
            username.isEmpty() -> _uiState.update { it.copy(usernameStatus = UsernameStatus.IDLE) }
            username == account?.username -> _uiState.update { it.copy(usernameStatus = UsernameStatus.AVAILABLE) }
            !username.isUsernameFormatValid() -> _uiState.update { it.copy(usernameStatus = UsernameStatus.INVALID) }
            else -> {
                _uiState.update { it.copy(usernameStatus = UsernameStatus.CHECKING) }
                usernameCheck = viewModelScope.launch {
                    delay(USERNAME_CHECK_DEBOUNCE_MILLIS)
                    val status = when (val result = accountRepository.checkUsername(username)) {
                        is AppResult.Success -> when (result.data) {
                            UsernameAvailability.AVAILABLE -> UsernameStatus.AVAILABLE
                            UsernameAvailability.TAKEN -> UsernameStatus.TAKEN
                            UsernameAvailability.INVALID -> UsernameStatus.INVALID
                        }
                        // Offline: let the save decide.
                        is AppResult.Error -> UsernameStatus.AVAILABLE
                    }
                    _uiState.update { if (it.username == username) it.copy(usernameStatus = status) else it }
                }
            }
        }
    }

    private fun saveProfile() {
        val state = _uiState.value
        val account = account ?: return
        if (!state.canSaveProfile) return
        val missing = account.onboarding.missingProfileFields
        val update = ProfileUpdate(
            displayName = state.displayName.trim().takeIf { it != account.displayName },
            username = state.username.takeIf { it != account.username },
            birthDate = state.birthDate.takeIf { ProfileField.BIRTH_DATE in missing || it != account.birthDate },
        )
        _uiState.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            when (val result = accountRepository.updateProfile(update)) {
                // The session moves on: to the consent step, or out of onboarding.
                is AppResult.Success -> _uiState.update { it.copy(isSaving = false) }
                is AppResult.Error -> {
                    val error = result.error
                    _uiState.update {
                        when {
                            error is DataError.Network.Api && error.code == USERNAME_TAKEN ->
                                it.copy(isSaving = false, usernameStatus = UsernameStatus.TAKEN)
                            error is DataError.Network.Api && error.code == USERNAME_INVALID ->
                                it.copy(isSaving = false, usernameStatus = UsernameStatus.INVALID)
                            // Under 16: the session is already signed out and the gate shows why.
                            error is DataError.Network.Api && error.code == AGE_REQUIREMENT_NOT_MET ->
                                it.copy(isSaving = false)
                            else -> it.copy(isSaving = false, error = error.toUiMessage())
                        }
                    }
                }
            }
        }
    }

    private fun loadDocuments() {
        val missing = account?.onboarding?.missingConsents.orEmpty()
        _uiState.update { it.copy(isLoadingDocuments = true, error = null) }
        viewModelScope.launch {
            when (val result = accountRepository.legalDocuments(languageTag())) {
                is AppResult.Success -> _uiState.update { state ->
                    val shown = result.data.filter { it.type in missing || (missing.isEmpty() && it.required) }
                    state.copy(
                        isLoadingDocuments = false,
                        documents = shown.map { DocumentItem(it, accepted = false) }.toImmutableList(),
                    )
                }
                is AppResult.Error -> _uiState.update {
                    it.copy(isLoadingDocuments = false, error = result.error.toUiMessage())
                }
            }
        }
    }

    private fun accept() {
        val state = _uiState.value
        if (!state.canAccept) return
        _uiState.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            when (val result = accountRepository.accept(state.documents.map { it.document })) {
                is AppResult.Success -> _uiState.update { it.copy(isSaving = false) }
                is AppResult.Error -> _uiState.update { it.copy(isSaving = false, error = result.error.toUiMessage()) }
            }
        }
    }

    private companion object {
        const val USERNAME_CHECK_DEBOUNCE_MILLIS = 400L
        const val USERNAME_TAKEN = "USERNAME_TAKEN"
        const val USERNAME_INVALID = "USERNAME_INVALID"
        const val AGE_REQUIREMENT_NOT_MET = "AGE_REQUIREMENT_NOT_MET"
    }
}
