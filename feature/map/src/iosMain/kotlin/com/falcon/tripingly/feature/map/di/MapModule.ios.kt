package com.falcon.tripingly.feature.map.di

import com.falcon.tripingly.core.data.account.SignedInStart
import com.falcon.tripingly.feature.map.presentation.component.GoogleMapListener
import com.falcon.tripingly.feature.map.presentation.component.GoogleMapsSdk
import com.falcon.tripingly.feature.map.presentation.component.LIGHT_MAP_STYLE
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

internal actual val mapPlatformModule: Module = module {
    single(named("mapWarmUp")) {
        // Apple's map, without the Google key, needs no warm-up.
        val googleMaps = getOrNull<GoogleMapsSdk>()
        SignedInStart {
            // After the current frame: making the first map takes the main thread for a moment.
            if (googleMaps != null) dispatch_async(dispatch_get_main_queue()) { googleMaps.createMap(NoListener).setStyle(LIGHT_MAP_STYLE) }
        }
    }
}

/** The warm-up map is never shown, so nothing happens on it. */
private object NoListener : GoogleMapListener {
    override fun onCameraIdle(latitude: Double, longitude: Double, zoom: Float, south: Double, west: Double, north: Double, east: Double) = Unit
    override fun onGestureStart() = Unit
    override fun onZoomChange(zoom: Float) = Unit
    override fun onTap(latitude: Double, longitude: Double) = Unit
    override fun onLongPress(latitude: Double, longitude: Double) = Unit
    override fun onMarkerTap(key: String) = Unit
}
