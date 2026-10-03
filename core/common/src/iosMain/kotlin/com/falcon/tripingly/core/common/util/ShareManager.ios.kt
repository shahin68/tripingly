package com.falcon.tripingly.core.common.util

import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication

class IosShareManager : ShareManager {
    override fun shareTrip(name: String, dates: String) {
        val text = "Check out my trip: $name ($dates)!"
        val activityViewController = UIActivityViewController(
            activityItems = listOf(text),
            applicationActivities = null
        )

        val window = UIApplication.sharedApplication.keyWindow
        window?.rootViewController?.presentViewController(
            activityViewController,
            animated = true,
            completion = null
        )
    }
}
