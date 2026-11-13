package com.falcon.tripingly.feature.map.presentation.map.ui

import androidx.compose.foundation.lazy.LazyListState

/**
 * Scrolls item [index] to the row's start when it isn't wholly in view (a pin tapped on the map shows its chip in the
 * plan); nothing when it already is, or for -1 (nothing picked).
 */
internal suspend fun LazyListState.reveal(index: Int) {
    if (index < 0) return
    val info = layoutInfo
    val item = info.visibleItemsInfo.firstOrNull { it.index == index }
    val isWhollyShown = item != null && item.offset >= info.viewportStartOffset && item.offset + item.size <= info.viewportEndOffset
    if (!isWhollyShown) animateScrollToItem(index)
}
