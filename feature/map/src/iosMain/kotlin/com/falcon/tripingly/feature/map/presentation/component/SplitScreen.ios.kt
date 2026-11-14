package com.falcon.tripingly.feature.map.presentation.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalWindowInfo
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.UIKit.UIApplication

/** The app's window is narrower than the screen: an iPad's Split View, Slide Over or Stage Manager window. */
@OptIn(ExperimentalForeignApi::class)
@Composable
internal actual fun isSplitScreen(): Boolean {
    // The window's size changes with every rotation or resize, which asks again.
    val size = LocalWindowInfo.current.containerSize
    return remember(size) {
        val window = UIApplication.sharedApplication.keyWindow ?: return@remember false
        window.bounds.useContents { this.size.width } < window.screen.bounds.useContents { this.size.width } - 1
    }
}
