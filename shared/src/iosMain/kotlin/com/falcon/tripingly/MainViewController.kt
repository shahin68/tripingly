package com.falcon.tripingly

import androidx.compose.ui.window.ComposeUIViewController
import com.falcon.tripingly.di.initKoin
import platform.UIKit.UIViewController

private var isKoinStarted = false

fun MainViewController(): UIViewController {
    if (!isKoinStarted) {
        initKoin()
        isKoinStarted = true
    }
    return ComposeUIViewController { App() }
}
