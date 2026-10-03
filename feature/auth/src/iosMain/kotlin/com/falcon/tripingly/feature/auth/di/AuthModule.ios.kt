package com.falcon.tripingly.feature.auth.di

import com.falcon.tripingly.feature.auth.domain.SocialSignIn
import com.falcon.tripingly.feature.auth.domain.UnconfiguredSocialSignIn
import org.koin.core.module.Module
import org.koin.dsl.module

// GoogleSignIn and Sign in with Apple come with the iOS client ID and the Apple Developer account.
internal actual val authPlatformModule: Module = module {
    single<SocialSignIn> { UnconfiguredSocialSignIn(appleAvailable = true) }
}
