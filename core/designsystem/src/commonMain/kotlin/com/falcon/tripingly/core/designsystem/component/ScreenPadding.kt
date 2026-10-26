package com.falcon.tripingly.core.designsystem.component

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.falcon.tripingly.core.designsystem.theme.spacing

/**
 * The content padding of a screen under floating bars: below the [topBar] (its `Scaffold`'s padding), above the
 * [bottomBar] (the tab shell's padding; none on screens without it) plus [extraBottom] for a floating button,
 * with the standard side gutter. Content scrolls behind the glass bars and comes to rest clear of them.
 */
@Composable
fun screenContentPadding(
    topBar: PaddingValues,
    bottomBar: PaddingValues = PaddingValues(),
    extraBottom: Dp = 0.dp,
): PaddingValues {
    val spacing = MaterialTheme.spacing
    return PaddingValues(
        start = spacing.medium,
        end = spacing.medium,
        top = topBar.calculateTopPadding() + spacing.small,
        bottom = maxOf(bottomBar.calculateBottomPadding(), topBar.calculateBottomPadding()) + spacing.medium + extraBottom,
    )
}
