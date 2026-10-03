package com.falcon.tripingly.feature.auth.di

import com.falcon.tripingly.feature.auth.domain.SocialSignIn
import com.falcon.tripingly.feature.auth.domain.UnconfiguredSocialSignIn
import org.koin.core.module.Module
import org.koin.dsl.module

// Credential Manager comes with the Google client ID (stage 3, once the keys exist).
internal actual val authPlatformModule: Module = module {
    single<SocialSignIn> { UnconfiguredSocialSignIn(appleAvailable = false) }
}
