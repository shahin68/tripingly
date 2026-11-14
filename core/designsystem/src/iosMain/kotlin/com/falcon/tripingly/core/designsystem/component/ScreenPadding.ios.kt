package com.falcon.tripingly.core.designsystem.component

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalWindowInfo
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIApplication
import platform.UIKit.UIDevice
import platform.UIKit.UIDeviceOrientationDidChangeNotification
import platform.UIKit.UIInterfaceOrientationLandscapeLeft
import platform.UIKit.UIInterfaceOrientationLandscapeRight

actual val WindowInsets.Companion.bottomSystemBar: WindowInsets
    @Composable get() = WindowInsets(0, 0, 0, 0)

/**
 * iOS gives both sides the notch's padding in landscape: only the side the notch (or Dynamic Island) is on keeps it.
 * Turned with the home indicator on the right the notch is on the left, and the other way round. Asked again when the
 * window's size changes and when the device turns (a turn from one landscape to the other keeps the size).
 */
actual val WindowInsets.Companion.safeSides: WindowInsets
    @Composable get() {
        val size = LocalWindowInfo.current.containerSize
        var turns by remember { mutableStateOf(0) }
        DisposableEffect(Unit) {
            UIDevice.currentDevice.beginGeneratingDeviceOrientationNotifications()
            val observer = NSNotificationCenter.defaultCenter.addObserverForName(
                name = UIDeviceOrientationDidChangeNotification,
                `object` = null,
                queue = NSOperationQueue.mainQueue,
                // The window turns after the device does: ask once it has.
            ) { _ -> NSOperationQueue.mainQueue.addOperationWithBlock { turns++ } }
            onDispose {
                NSNotificationCenter.defaultCenter.removeObserver(observer)
                UIDevice.currentDevice.endGeneratingDeviceOrientationNotifications()
            }
        }
        val notchSide = remember(size, turns) {
            when (UIApplication.sharedApplication.keyWindow?.windowScene?.interfaceOrientation) {
                UIInterfaceOrientationLandscapeRight -> WindowInsetsSides.Left
                UIInterfaceOrientationLandscapeLeft -> WindowInsetsSides.Right
                else -> WindowInsetsSides.Horizontal
            }
        }
        return safeDrawing.only(notchSide)
    }
