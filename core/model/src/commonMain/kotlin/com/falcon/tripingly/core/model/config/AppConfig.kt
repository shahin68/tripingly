package com.falcon.tripingly.core.model.config

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Values that steer the app, sent by the server (`GET /app-config`) so they change without an app
 * update. The defaults here are the server's defaults, used until its answer arrives or when it fails.
 */
data class AppConfig(
    /** How old a map square's places may get before they're reloaded in the background. */
    val placesRefresh: Duration = 300.seconds,
    /** Longest edge, in pixels, a photo is shrunk to before it's uploaded. */
    val photoMaxEdgePx: Int = 2560,
    /** JPEG quality photos are encoded with before they're uploaded. */
    val photoJpegQuality: Int = 85,
    /** Photos uploaded at the same time. */
    val photoParallelUploads: Int = 2,
    /** A place's prominence from which its map pin is drawn largest. */
    val pinLargeMinProminence: Int = 95,
    /** A place's prominence from which its map pin is drawn middle-sized; below it, smallest. */
    val pinMediumMinProminence: Int = 60,
    /** Most place pins drawn on the map at once: liked places first, then the best known. */
    val mapMaxPins: Int = 150,
    /** A place's pin shows its photo when its prominence is at least this, or its hot count reaches [photoPinMinHotCount]. */
    val photoPinMinProminence: Int = 90,
    /** A place's pin shows its photo when it has at least this many likes, or [photoPinMinProminence]. */
    val photoPinMinHotCount: Int = 3,
    /** Most pins drawn with a photo at once, in pin order; the rest keep their icon. */
    val mapMaxPhotoPins: Int = 100,
    /** Below this camera zoom, place pins are drawn as small dots; they grow back to pins when zoomed in. */
    val pinDotBelowZoom: Float = 14f,
)
