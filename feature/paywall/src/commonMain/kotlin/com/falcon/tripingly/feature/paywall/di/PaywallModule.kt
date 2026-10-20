package com.falcon.tripingly.feature.paywall.di

import androidx.compose.ui.text.intl.Locale
import com.falcon.tripingly.feature.paywall.presentation.PaywallViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val paywallModule = module {
    viewModel {
        PaywallViewModel(
            sessionRepository = get(),
            proRepository = get(),
            accountRepository = get(),
            languageTag = { Locale.current.toLanguageTag() },
        )
    }
}
