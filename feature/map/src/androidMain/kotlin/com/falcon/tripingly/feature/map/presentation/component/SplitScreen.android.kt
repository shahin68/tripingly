package com.falcon.tripingly.feature.map.presentation.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.falcon.tripingly.core.location.permission.findActivity

@Composable
internal actual fun isSplitScreen(): Boolean {
    // A new configuration comes with every change of the window, entering or leaving a split screen too.
    val configuration = LocalConfiguration.current
    val activity = LocalContext.current.findActivity()
    return remember(configuration, activity) { activity.isInMultiWindowMode }
}
