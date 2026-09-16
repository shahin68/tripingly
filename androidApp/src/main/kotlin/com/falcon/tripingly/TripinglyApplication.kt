package com.falcon.tripingly

import android.app.Application
import com.falcon.tripingly.di.initKoin
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger

class TripinglyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        initKoin {
            androidContext(this@TripinglyApplication)
            androidLogger()
        }
    }
}
