@file:OptIn(ExperimentalObjCName::class)

package com.falcon.tripingly.feature.map.presentation.component

import platform.UIKit.UIImage
import platform.UIKit.UIView
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * The Google Maps SDK for iOS. It has no Kotlin binding, so the app's Swift code drives it
 * (`iosApp/GoogleMapsSdk.swift`) and the shared code tells it what to show.
 */
@ObjCName("GoogleMapsSdk", exact = true)
interface GoogleMapsSdk {
    /** Hands the SDK its key at launch. False without the GoogleMaps package or a key: the map is Apple's then. */
    fun start(apiKey: String): Boolean

    fun createMap(listener: GoogleMapListener): GoogleMap
}

/** One map view. Called on the main thread. */
@ObjCName("GoogleMap", exact = true)
interface GoogleMap {
    val view: UIView

    /** A JSON map style, as on Android. */
    fun setStyle(json: String)

    fun setMyLocationEnabled(enabled: Boolean)

    /** Points at the top and bottom the map keeps its logo and controls out of. */
    fun setPadding(top: Double, bottom: Double)

    fun moveCamera(latitude: Double, longitude: Double, zoom: Float, animated: Boolean)

    /** Replaces the markers; a marker with the same key is moved or updated. */
    fun setMarkers(markers: List<GoogleMapMarker>)
}

@ObjCName("GoogleMapMarker", exact = true)
class GoogleMapMarker(
    val key: String,
    val latitude: Double,
    val longitude: Double,
    val title: String,
    val icon: UIImage,
    /** Where the icon touches its point, as a share of its width and height. */
    val anchorX: Double,
    val anchorY: Double,
    val zIndex: Int,
    /** A tap shows the title and centers the marker; else the tap is only reported. */
    val showsInfoWindow: Boolean,
    /** Fades in when it first shows, as Google's own pins do. */
    val fadesIn: Boolean,
)

@ObjCName("GoogleMapListener", exact = true)
interface GoogleMapListener {
    /** The camera stopped: its center, zoom and the area the whole view shows. */
    fun onCameraIdle(latitude: Double, longitude: Double, zoom: Float, south: Double, west: Double, north: Double, east: Double)

    /** A finger started moving the camera. */
    fun onGestureStart()

    fun onTap(latitude: Double, longitude: Double)

    fun onLongPress(latitude: Double, longitude: Double)

    fun onMarkerTap(key: String)
}
