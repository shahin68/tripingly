package com.falcon.tripingly

import androidx.compose.ui.window.ComposeUIViewController
import com.falcon.tripingly.di.initKoin
import com.falcon.tripingly.feature.auth.domain.SocialSignIn
import org.koin.dsl.module
import platform.UIKit.UIViewController

private var isKoinStarted = false

fun MainViewController(nativeSignIn: NativeSignIn): UIViewController {
    if (!isKoinStarted) {
        initKoin {
            modules(module { single<SocialSignIn> { NativeSocialSignIn(nativeSignIn) } })
        }
        isKoinStarted = true
    }
    return ComposeUIViewController { App() }
}
