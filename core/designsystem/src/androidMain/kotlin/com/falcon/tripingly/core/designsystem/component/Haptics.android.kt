package com.falcon.tripingly.core.designsystem.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.platform.LocalHapticFeedback

@Composable
internal actual fun platformHapticFeedback(): HapticFeedback = LocalHapticFeedback.current
