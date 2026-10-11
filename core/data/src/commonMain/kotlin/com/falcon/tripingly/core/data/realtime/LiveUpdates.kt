package com.falcon.tripingly.core.data.realtime

/** Live updates from the server, while the app is signed in and in the foreground. */
interface LiveUpdates {
    fun start()

    fun stop()
}
