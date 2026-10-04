package com.falcon.tripingly.core.designsystem.browser

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.UriHandler

/**
 * Opens web links inside the app: Custom Tabs on Android, SFSafariViewController on iOS.
 * Provided as `LocalUriHandler` at the app root, so `LocalUriHandler.current.openUri` uses it.
 */
@Composable
expect fun rememberInAppUriHandler(): UriHandler
