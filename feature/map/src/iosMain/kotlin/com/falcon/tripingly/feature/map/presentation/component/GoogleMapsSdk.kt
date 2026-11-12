@file:OptIn(ExperimentalObjCName::class)

package com.falcon.tripingly.feature.map.presentation.component

import platform.UIKit.UIColor
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

    /**
     * Replaces the line drawn under the markers (a route, or the stops joined in order) and its [arrows]: flat
     * markers showing [arrowIcon] (pointing north) turned by their bearing. [capIcon], a dot as wide as the line,
     * sits on both ends to round them. [width] is in points; fewer than two points draw none.
     */
    fun setLine(points: List<GoogleMapPoint>, color: UIColor, width: Double, arrows: List<GoogleMapArrow>, arrowIcon: UIImage?, capIcon: UIImage?)

    /** Replaces the streak running along the line, on top of it; fewer than two points draw none. */
    fun setLineSweep(points: List<GoogleMapPoint>, color: UIColor, width: Double)

    /** Where a point is on the view now, in points from its top left. */
    fun screenPoint(latitude: Double, longitude: Double): GoogleMapScreenPoint

    /**
     * Pans the camera at this speed, points per second (right and down for positive values), moving it on each of the
     * map's own display frames so it stays smooth; zero stops it.
     */
    fun setPanVelocity(dx: Double, dy: Double)
}

@ObjCName("GoogleMapPoint", exact = true)
class GoogleMapPoint(val latitude: Double, val longitude: Double)

/** A point on the map view, in points from its top left. */
@ObjCName("GoogleMapScreenPoint", exact = true)
class GoogleMapScreenPoint(val x: Double, val y: Double)

/** [bearing] in degrees clockwise from north. */
@ObjCName("GoogleMapArrow", exact = true)
class GoogleMapArrow(val latitude: Double, val longitude: Double, val bearing: Double)

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

    /** The camera's zoom and the area the whole view shows while it moves, every frame: keep it cheap. */
    fun onCameraChange(zoom: Float, south: Double, west: Double, north: Double, east: Double)

    fun onTap(latitude: Double, longitude: Double)

    /**
     * A long press at this point on the map, and at [x], [y] on the view (points). True when it was on a pin, which is
     * then held: the map's own gestures stop until [onPinHoldEnd], and [onPinHoldMove] reports the finger.
     */
    fun onLongPress(latitude: Double, longitude: Double, x: Double, y: Double): Boolean

    fun onMarkerTap(key: String)

    /** A quick tap at [x], [y] on the view (points), as the finger lifted. True when it was on a pin, which opened. */
    fun onPinTap(x: Double, y: Double): Boolean

    /** The finger holding a pin moved to [x], [y] on the view (points). */
    fun onPinHoldMove(x: Double, y: Double)

    /** The finger holding a pin was lifted. */
    fun onPinHoldEnd()
}
