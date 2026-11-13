package com.falcon.tripingly.feature.map.presentation.map.ui

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned

/**
 * Brings item [index] to the row's start when it isn't wholly in view (a pin tapped on the map shows its chip in the
 * plan): gliding there while the row is on screen ([onScreen]), at once otherwise, e.g. under a collapsed sheet, where
 * a glide only costs frames (Shahin, 2026-11-13). Nothing when it already is in view, or for -1 (nothing picked).
 */
internal suspend fun LazyListState.reveal(index: Int, onScreen: OnScreen? = null) {
    if (index < 0) return
    val info = layoutInfo
    val item = info.visibleItemsInfo.firstOrNull { it.index == index }
    val isWhollyShown = item != null && item.offset >= info.viewportStartOffset && item.offset + item.size <= info.viewportEndOffset
    when {
        isWhollyShown -> Unit
        onScreen == null || onScreen.isShown -> animateScrollToItem(index)
        else -> scrollToItem(index)
    }
}

/** Whether a row is on screen, kept up by [trackOnScreen] as it moves and read only when revealing, so it costs no recomposition. */
internal class OnScreen {
    var isShown = false
        internal set
}

/** Keeps [onScreen] up: on screen while at least half of the row's height shows in the window. */
internal fun Modifier.trackOnScreen(onScreen: OnScreen): Modifier = onGloballyPositioned {
    onScreen.isShown = it.size.height > 0 && it.boundsInWindow().height >= it.size.height / 2f
}
