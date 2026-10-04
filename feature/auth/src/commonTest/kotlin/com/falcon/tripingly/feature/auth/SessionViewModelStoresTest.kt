package com.falcon.tripingly.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import com.falcon.tripingly.feature.auth.presentation.gate.SessionViewModelStores
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SessionViewModelStoresTest {

    private class Screen : ViewModel() {
        var cleared = false
        override fun onCleared() {
            cleared = true
        }
    }

    private val factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: KClass<T>, extras: CreationExtras): T = Screen() as T
    }

    private fun SessionViewModelStores.screen(key: String): Screen =
        ViewModelProvider.create(storeFor(key), factory)[Screen::class]

    @Test
    fun sameSession_keepsItsViewModels() {
        val stores = SessionViewModelStores()

        assertSame(stores.screen("onboarding:a"), stores.screen("onboarding:a"))
    }

    @Test
    fun anotherAccount_clearsThePreviousViewModels() {
        val stores = SessionViewModelStores()
        val first = stores.screen("onboarding:a")

        stores.storeFor("signed-out")
        val second = stores.screen("onboarding:b")

        assertTrue(first.cleared)
        assertNotSame(first, second)
    }
}
