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
)
