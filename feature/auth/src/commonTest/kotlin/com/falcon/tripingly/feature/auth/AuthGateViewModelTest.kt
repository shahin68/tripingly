package com.falcon.tripingly.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import com.falcon.tripingly.core.data.account.FakeAccountBackend
import com.falcon.tripingly.core.model.account.ProfileUpdate
import com.falcon.tripingly.feature.auth.presentation.gate.AuthGateViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlin.reflect.KClass
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AuthGateViewModelTest {

    /** Stands in for a screen's ViewModel, such as My Trips. */
    private class ScreenViewModel : ViewModel() {
        var isCleared = false
        override fun onCleared() {
            isCleared = true
        }
    }

    private val factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: KClass<T>, extras: CreationExtras): T = ScreenViewModel() as T
    }

    private val backend = FakeAccountBackend(today = { LocalDate(2026, 10, 19) })

    @BeforeTest
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun AuthGateViewModel.screen(): ScreenViewModel =
        ViewModelProvider.create(session.viewModelStore, factory)[ScreenViewModel::class]

    @Test
    fun signingOut_clearsTheScreensOfTheSession() = runTest {
        val gate = AuthGateViewModel(backend)
        advanceUntilIdle()
        backend.signInForDevelopment("tester", name = null)
        advanceUntilIdle()
        val screen = gate.screen()

        backend.signOut()
        advanceUntilIdle()

        assertTrue(screen.isCleared)
        backend.signInForDevelopment("tester2", name = null)
        advanceUntilIdle()
        assertNotSame(screen, gate.screen())
    }

    @Test
    fun theSessionChangingInside_keepsItsScreens() = runTest {
        val gate = AuthGateViewModel(backend)
        advanceUntilIdle()
        backend.signInForDevelopment("tester", name = null)
        advanceUntilIdle()
        val screen = gate.screen()

        // Onboarding goes on: still the same session.
        backend.updateProfile(ProfileUpdate(username = "tester"))
        advanceUntilIdle()

        assertFalse(screen.isCleared)
        assertSame(screen, gate.screen())
    }
}
