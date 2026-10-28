package com.falcon.tripingly.core.designsystem.component

import android.os.Build

// Compose's blur needs RenderEffect, which came with Android 12.
internal actual val isBlurSupported: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
