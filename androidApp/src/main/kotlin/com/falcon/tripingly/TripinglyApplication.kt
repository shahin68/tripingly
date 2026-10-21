package com.falcon.tripingly

import android.app.Application
import com.falcon.tripingly.billing.RevenueCatPurchases
import com.falcon.tripingly.core.data.billing.StorePurchases
import com.falcon.tripingly.core.data.push.PushMessaging
import com.falcon.tripingly.di.initKoin
import com.falcon.tripingly.push.FcmPushMessaging
import com.falcon.tripingly.push.TripinglyMessagingService
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.dsl.module

class TripinglyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val hasPush = FcmPushMessaging.initialize(this)
        if (hasPush) TripinglyMessagingService.createChannel(this)
        val purchases = RevenueCatPurchases.initialize(this)
        initKoin {
            androidContext(this@TripinglyApplication)
            androidLogger()
            if (hasPush) modules(module { single<PushMessaging> { FcmPushMessaging() } })
            if (purchases != null) modules(module { single<StorePurchases> { purchases } })
        }
    }
}
