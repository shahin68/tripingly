package com.falcon.tripingly.core.designsystem.component

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.runtime.Composable

actual val WindowInsets.Companion.bottomSystemBar: WindowInsets
    @Composable get() = navigationBars.only(WindowInsetsSides.Bottom)
