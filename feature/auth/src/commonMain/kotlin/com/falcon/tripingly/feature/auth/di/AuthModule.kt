package com.falcon.tripingly.feature.auth.di

import androidx.compose.ui.text.intl.Locale
import com.falcon.tripingly.feature.auth.presentation.gate.AuthGateViewModel
import com.falcon.tripingly.feature.auth.presentation.onboarding.OnboardingViewModel
import com.falcon.tripingly.feature.auth.presentation.signin.SignInViewModel
import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val authModule = module {
    includes(authPlatformModule)
    viewModelOf(::AuthGateViewModel)
    viewModelOf(::SignInViewModel)
    viewModel {
        OnboardingViewModel(
            sessionRepository = get(),
            accountRepository = get(),
            today = { Clock.System.todayIn(TimeZone.currentSystemDefault()) },
            languageTag = { Locale.current.toLanguageTag() },
        )
    }
}

/** Binds the platform's [com.falcon.tripingly.feature.auth.domain.SocialSignIn]. */
internal expect val authPlatformModule: Module
