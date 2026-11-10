package com.falcon.tripingly.feature.map.di

import android.content.Context
import android.os.Looper
import com.falcon.tripingly.core.data.account.SignedInStart
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.MapsInitializer
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module

internal actual val mapPlatformModule: Module = module {
    single(named("mapWarmUp")) {
        val context = get<Context>()
        SignedInStart {
            // Once Home has drawn and the main thread is idle: loading the SDK takes it for a moment.
            Looper.myQueue().addIdleHandler {
                MapsInitializer.initialize(context, MapsInitializer.Renderer.LATEST) {}
                MapView(context).run {
                    onCreate(null)
                    onDestroy()
                }
                false
            }
        }
    }
}
