package com.falcon.tripingly.feature.auth.di

import android.app.Application
import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import com.falcon.tripingly.core.data.account.LocalDataCleaner
import com.falcon.tripingly.core.network.ApiConfig
import com.falcon.tripingly.feature.auth.domain.SocialSignIn
import com.falcon.tripingly.feature.auth.platform.CredentialManagerSignIn
import com.falcon.tripingly.feature.auth.platform.CurrentActivity
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module

internal actual val authPlatformModule: Module = module {
    // Eager: it has to see the first activity resume, which happens before any screen asks for it.
    single(createdAtStart = true) { CurrentActivity(get<Context>().applicationContext as Application) }
    single<SocialSignIn> {
        CredentialManagerSignIn(currentActivity = get(), webClientId = get<ApiConfig>().googleWebClientId)
    }
    // Forget the chosen Google account, so the next sign-in asks again instead of reusing it.
    single(named("googleCredentialState")) {
        val context = get<Context>().applicationContext
        LocalDataCleaner { CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest()) }
    }
}
