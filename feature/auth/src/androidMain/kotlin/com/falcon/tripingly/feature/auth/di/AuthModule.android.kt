package com.falcon.tripingly.feature.auth.di

import android.app.Application
import android.content.Context
import com.falcon.tripingly.core.network.ApiConfig
import com.falcon.tripingly.feature.auth.domain.SocialSignIn
import com.falcon.tripingly.feature.auth.platform.CredentialManagerSignIn
import com.falcon.tripingly.feature.auth.platform.CurrentActivity
import org.koin.core.module.Module
import org.koin.dsl.module

internal actual val authPlatformModule: Module = module {
    // Eager: it has to see the first activity resume, which happens before any screen asks for it.
    single(createdAtStart = true) { CurrentActivity(get<Context>().applicationContext as Application) }
    single<SocialSignIn> {
        CredentialManagerSignIn(currentActivity = get(), webClientId = get<ApiConfig>().googleWebClientId)
    }
}
