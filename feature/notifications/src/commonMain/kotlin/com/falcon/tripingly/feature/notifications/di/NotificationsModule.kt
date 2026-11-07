package com.falcon.tripingly.feature.notifications.di

import com.falcon.tripingly.feature.notifications.presentation.NotificationCenterViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val notificationsModule = module {
    viewModelOf(::NotificationCenterViewModel)
}
