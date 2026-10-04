package com.falcon.tripingly

import androidx.compose.ui.window.ComposeUIViewController
import com.falcon.tripingly.core.data.account.LocalDataCleaner
import com.falcon.tripingly.di.initKoin
import com.falcon.tripingly.feature.auth.domain.SocialSignIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.core.qualifier.named
import org.koin.dsl.module
import platform.UIKit.UIViewController

private var isKoinStarted = false

fun MainViewController(nativeSignIn: NativeSignIn): UIViewController {
    if (!isKoinStarted) {
        initKoin {
            modules(
                module {
                    single<SocialSignIn> { NativeSocialSignIn(nativeSignIn) }
                    single(named("googleSignInState")) {
                        LocalDataCleaner { withContext(Dispatchers.Main) { nativeSignIn.signOut() } }
                    }
                },
            )
        }
        isKoinStarted = true
    }
    return ComposeUIViewController { App() }
}
