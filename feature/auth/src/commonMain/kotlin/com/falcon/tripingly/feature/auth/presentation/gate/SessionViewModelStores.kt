package com.falcon.tripingly.feature.auth.presentation.gate

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * One [ViewModelStore] per session phase and account. Moving to another key
 * (sign-out, another account, onboarding finished) clears every view model
 * of the previous one, so no screen state carries over to the next user.
 * Held in a view model itself so it survives configuration changes.
 */
internal class SessionViewModelStores : ViewModel() {
    private var key: String? = null
    private var store = ViewModelStore()

    fun storeFor(key: String): ViewModelStore {
        if (key != this.key) {
            store.clear()
            store = ViewModelStore()
            this.key = key
        }
        return store
    }

    override fun onCleared() {
        store.clear()
    }
}

/** View models created inside [content] belong to the session phase named by [key]. */
@Composable
internal fun SessionViewModelScope(key: String, content: @Composable () -> Unit) {
    val stores: SessionViewModelStores = viewModel { SessionViewModelStores() }
    val owner = remember(key) {
        val store = stores.storeFor(key)
        object : ViewModelStoreOwner {
            override val viewModelStore = store
        }
    }
    CompositionLocalProvider(LocalViewModelStoreOwner provides owner, content = content)
}
