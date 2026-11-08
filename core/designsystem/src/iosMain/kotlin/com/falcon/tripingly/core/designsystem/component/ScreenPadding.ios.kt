package com.falcon.tripingly.core.designsystem.component

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.Composable

actual val WindowInsets.Companion.bottomSystemBar: WindowInsets
    @Composable get() = WindowInsets(0, 0, 0, 0)
