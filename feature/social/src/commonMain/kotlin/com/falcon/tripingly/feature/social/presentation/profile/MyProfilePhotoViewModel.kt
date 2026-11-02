package com.falcon.tripingly.feature.social.presentation.profile

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.falcon.tripingly.core.common.result.AppResult
import com.falcon.tripingly.core.common.error.DataError
import com.falcon.tripingly.core.data.account.AccountRepository
import com.falcon.tripingly.core.data.account.SessionRepository
import com.falcon.tripingly.core.model.account.Account
import com.falcon.tripingly.core.model.account.SessionState
import com.falcon.tripingly.core.model.photo.PickedImage
import com.falcon.tripingly.core.model.trip.ProfilePhoto
import com.falcon.tripingly.core.ui.UiText
import com.falcon.tripingly.core.ui.toUiText
import com.falcon.tripingly.feature.social.generated.resources.Res
import com.falcon.tripingly.feature.social.generated.resources.social_error_profile_photo
import com.falcon.tripingly.feature.social.generated.resources.social_error_profile_photo_remove
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource

/** My profile photo on the Profile tab: the pen changes or removes it; the session's account is the photo shown. */
class MyProfilePhotoViewModel(
    private val accountRepository: AccountRepository,
    private val sessionRepository: SessionRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(State())
    val uiState: StateFlow<State> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            sessionRepository.session.collect { session ->
                val photo = (session as? SessionState.SignedIn)?.account?.photo
                _uiState.update { it.copy(photo = photo) }
            }
        }
    }

    fun onAction(action: Action) {
        when (action) {
            is Action.Choose -> save(Res.string.social_error_profile_photo) { accountRepository.setProfilePhoto(action.image) }
            Action.Remove -> save(Res.string.social_error_profile_photo_remove) { accountRepository.removeProfilePhoto() }
            Action.DismissMessage -> _uiState.update { it.copy(message = null) }
        }
    }

    private fun save(failed: StringResource, call: suspend () -> AppResult<Account, DataError.Network>) {
        if (_uiState.value.isSaving) return
        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            val result = call()
            _uiState.update {
                it.copy(isSaving = false, message = (result as? AppResult.Error)?.error?.toUiText(failed))
            }
        }
    }

    @Immutable
    data class State(
        val photo: ProfilePhoto? = null,
        /** A new photo is going up, or the old one is being removed. */
        val isSaving: Boolean = false,
        val message: UiText? = null,
    )

    sealed interface Action {
        data class Choose(val image: PickedImage) : Action
        data object Remove : Action
        data object DismissMessage : Action
    }
}
