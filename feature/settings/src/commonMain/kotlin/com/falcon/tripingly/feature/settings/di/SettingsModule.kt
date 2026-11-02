package com.falcon.tripingly.feature.settings.di

import androidx.compose.ui.text.intl.Locale
import com.falcon.tripingly.core.network.ApiConfig
import com.falcon.tripingly.feature.settings.presentation.BlockedPeopleViewModel
import com.falcon.tripingly.feature.settings.presentation.SettingsViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val settingsModule = module {
    viewModelOf(::BlockedPeopleViewModel)
    viewModel {
        SettingsViewModel(
            sessionRepository = get(),
            accountRepository = get(),
            socialSignIn = get(),
            // The client header carries it: `android/1.0`.
            appVersion = get<ApiConfig>().client.substringAfter('/'),
            languageTag = { Locale.current.toLanguageTag() },
        )
    }
}
