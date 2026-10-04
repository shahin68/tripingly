package com.falcon.tripingly.core.designsystem.browser

import android.content.ActivityNotFoundException
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.UriHandler

@Composable
actual fun rememberInAppUriHandler(): UriHandler {
    val context = LocalContext.current
    return remember(context) {
        object : UriHandler {
            override fun openUri(uri: String) {
                try {
                    // Falls back to the default browser when none supports Custom Tabs.
                    CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(context, Uri.parse(uri))
                } catch (_: ActivityNotFoundException) {
                    // No browser installed: nothing can open the link.
                }
            }
        }
    }
}
