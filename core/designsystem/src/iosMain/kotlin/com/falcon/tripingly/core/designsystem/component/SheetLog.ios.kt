package com.falcon.tripingly.core.designsystem.component

import kotlin.experimental.ExperimentalNativeApi

@OptIn(ExperimentalNativeApi::class)
internal actual fun sheetLog(message: String) {
    if (Platform.isDebugBinary) println("Sheet: $message")
}
