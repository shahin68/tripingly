package com.falcon.tripingly

import androidx.compose.ui.window.ComposeUIViewController
import com.falcon.tripingly.core.data.account.LocalDataCleaner
import com.falcon.tripingly.core.data.push.PushMessaging
import com.falcon.tripingly.di.initKoin
import com.falcon.tripingly.core.data.account.SocialSignIn
import com.falcon.tripingly.feature.map.presentation.component.GoogleMapsSdk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.core.qualifier.named
import org.koin.dsl.module
import platform.UIKit.UIViewController

private var isKoinStarted = false

fun MainViewController(nativeSignIn: NativeSignIn, googleMaps: GoogleMapsSdk, push: NativePush): UIViewController {
    if (!isKoinStarted) {
        // Without the key the map is Apple's.
        val hasGoogleMaps = googleMaps.start(AppBuildConfig.MAPS_API_KEY_IOS)
        val hasPush = push.start(
            projectId = AppBuildConfig.FIREBASE_PROJECT_ID,
            senderId = AppBuildConfig.FIREBASE_SENDER_ID,
            appId = AppBuildConfig.FIREBASE_IOS_APP_ID,
            apiKey = AppBuildConfig.FIREBASE_IOS_API_KEY,
        )
        initKoin {
            modules(
                module {
                    single<SocialSignIn> { NativeSocialSignIn(nativeSignIn) }
                    single(named("googleSignInState")) {
                        LocalDataCleaner { withContext(Dispatchers.Main) { nativeSignIn.signOut() } }
                    }
                    if (hasGoogleMaps) single { googleMaps }
                    // At start, so a push tapped to launch the app is opened.
                    if (hasPush) single<PushMessaging>(createdAtStart = true) { IosPushMessaging(push, get()).also(push::setListener) }
                },
            )
        }
        isKoinStarted = true
    }
    return ComposeUIViewController { App() }
}
