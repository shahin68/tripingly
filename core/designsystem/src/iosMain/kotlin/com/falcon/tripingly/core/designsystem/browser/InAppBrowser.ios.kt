package com.falcon.tripingly.core.designsystem.browser

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.uikit.LocalUIViewController
import platform.Foundation.NSURL
import platform.SafariServices.SFSafariViewController
import platform.UIKit.UIApplication

@Composable
actual fun rememberInAppUriHandler(): UriHandler {
    val controller = LocalUIViewController.current
    return remember(controller) {
        object : UriHandler {
            override fun openUri(uri: String) {
                val url = NSURL.URLWithString(uri) ?: return
                if (url.scheme == "https" || url.scheme == "http") {
                    controller.presentViewController(SFSafariViewController(uRL = url), animated = true, completion = null)
                } else {
                    // Safari's view only shows web pages; mailto: and the like go to the system.
                    UIApplication.sharedApplication.openURL(url, options = emptyMap<Any?, Any>(), completionHandler = null)
                }
            }
        }
    }
}
