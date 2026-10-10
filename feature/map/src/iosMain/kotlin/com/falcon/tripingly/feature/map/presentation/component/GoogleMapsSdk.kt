@file:OptIn(ExperimentalObjCName::class)

package com.falcon.tripingly.feature.map.presentation.component

import platform.Foundation.NSData
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

/** One map view. Called on the main thread, except [GoogleMapTiles.tile]. */
@ObjCName("GoogleMap", exact = true)
interface GoogleMap {
    val view: UIView

    /** A JSON map style, as on Android. */
    fun setStyle(json: String)

    fun setMyLocationEnabled(enabled: Boolean)

    /** Points at the top and bottom the map keeps its logo and controls out of. */
    fun setPadding(top: Double, bottom: Double)

    fun moveCamera(latitude: Double, longitude: Double, zoom: Float, animated: Boolean)

    /** The place pins, painted into tiles the map asks for on its own background threads. */
    fun setTiles(tiles: GoogleMapTiles)

    /**
     * Paints the tiles again. The map can only drop every tile of a layer, and its pins would
     * blink, so a new layer fades in over the old one, which goes once its tiles are there.
     */
    fun redrawTiles()

    /** Replaces the markers; a marker with the same key is moved or updated. */
    fun setMarkers(markers: List<GoogleMapMarker>)

    /** Shows this marker's info window; null hides it. */
    fun selectMarker(key: String?)

    /** Reports where this point is on the view at once and on every frame the camera moves. */
    fun trackPoint(latitude: Double, longitude: Double)

    fun stopTrackingPoint()
}

@ObjCName("GoogleMapTiles", exact = true)
interface GoogleMapTiles {
    /** Pixels a side. */
    val size: Int

    /** The tile's PNG; empty when it has no pins, null when its places can't be loaded now (the map asks again later). */
    fun tile(x: Int, y: Int, zoom: Int): NSData?
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
)

@ObjCName("GoogleMapListener", exact = true)
interface GoogleMapListener {
    /** The camera stopped: its center, zoom and the area the whole view shows. */
    fun onCameraIdle(latitude: Double, longitude: Double, zoom: Float, south: Double, west: Double, north: Double, east: Double)

    fun onTap(latitude: Double, longitude: Double)

    fun onLongPress(latitude: Double, longitude: Double)

    fun onMarkerTap(key: String)

    /** The tracked point, in points from the view's top left. */
    fun onTrackedPointMoved(x: Double, y: Double)
}
