package com.falcon.tripingly

import androidx.compose.ui.window.ComposeUIViewController
import com.falcon.tripingly.core.data.account.LocalDataCleaner
import com.falcon.tripingly.di.initKoin
import com.falcon.tripingly.feature.auth.domain.SocialSignIn
import com.falcon.tripingly.feature.map.presentation.component.GoogleMapsSdk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.core.qualifier.named
import org.koin.dsl.module
import platform.UIKit.UIViewController

private var isKoinStarted = false

fun MainViewController(nativeSignIn: NativeSignIn, googleMaps: GoogleMapsSdk): UIViewController {
    if (!isKoinStarted) {
        // Without the key the map is Apple's.
        val hasGoogleMaps = googleMaps.start(AppBuildConfig.MAPS_API_KEY_IOS)
        initKoin {
            modules(
                module {
                    single<SocialSignIn> { NativeSocialSignIn(nativeSignIn) }
                    single(named("googleSignInState")) {
                        LocalDataCleaner { withContext(Dispatchers.Main) { nativeSignIn.signOut() } }
                    }
                    if (hasGoogleMaps) single { googleMaps }
                },
            )
        }
        isKoinStarted = true
    }
    return ComposeUIViewController { App() }
}
