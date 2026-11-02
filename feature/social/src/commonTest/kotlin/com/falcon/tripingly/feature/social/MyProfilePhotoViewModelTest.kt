package com.falcon.tripingly.feature.social

import com.falcon.tripingly.core.common.error.DataError
import com.falcon.tripingly.core.common.result.AppResult
import com.falcon.tripingly.core.data.account.AccountRepository
import com.falcon.tripingly.core.data.account.FakeAccountBackend
import com.falcon.tripingly.core.model.account.Account
import com.falcon.tripingly.core.model.account.ProfileUpdate
import com.falcon.tripingly.core.model.photo.PickedImage
import com.falcon.tripingly.feature.social.presentation.profile.MyProfilePhotoViewModel
import com.falcon.tripingly.feature.social.presentation.profile.MyProfilePhotoViewModel.Action
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class MyProfilePhotoViewModelTest {

    @BeforeTest
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    /** Onboarded, so the session is signed in. */
    private suspend fun signedIn() = FakeAccountBackend(today = { LocalDate(2026, 10, 31) }).also {
        it.signInForDevelopment("me", "Me")
        it.updateProfile(ProfileUpdate(username = "me", birthDate = LocalDate(1990, 1, 1)))
        it.accept((it.legalDocuments("en") as AppResult.Success).data)
    }

    @Test
    fun choosingAPhoto_makesItMyAvatar() = runTest {
        val backend = signedIn()
        val viewModel = MyProfilePhotoViewModel(backend, backend)
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.photo)

        viewModel.onAction(Action.Choose { byteArrayOf(1) })
        assertTrue(viewModel.uiState.value.isSaving)
        advanceUntilIdle()

        assertEquals("photo-1", viewModel.uiState.value.photo?.id)
        assertFalse(viewModel.uiState.value.isSaving)

        viewModel.onAction(Action.Remove)
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.photo)
    }

    @Test
    fun aFailedUpload_keepsThePhoto_withAMessage() = runTest {
        val backend = signedIn()
        val failing = object : AccountRepository by backend {
            override suspend fun setProfilePhoto(image: PickedImage): AppResult<Account, DataError.Network> =
                AppResult.Error(DataError.Network.NoInternet)
        }
        val viewModel = MyProfilePhotoViewModel(failing, backend)

        viewModel.onAction(Action.Choose { byteArrayOf(1) })
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.photo)
        assertFalse(viewModel.uiState.value.isSaving)
        assertNotNull(viewModel.uiState.value.message)
    }
}
