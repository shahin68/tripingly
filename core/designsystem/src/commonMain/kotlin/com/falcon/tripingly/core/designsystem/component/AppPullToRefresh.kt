package com.falcon.tripingly.core.designsystem.component

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter

/**
 * Pull to refresh for a screen under the floating top bar, Compose's own [PullToRefreshBox] and indicator. Wrap the
 * whole [LoadStateContent] in it, so its error and empty screens can be pulled too and a pull does what Try again does
 * (Shahin, 2026-11-23); nothing pulls while the skeleton shows (it doesn't scroll).
 *
 * The indicator is laid out below the floating bar (the top of [contentPadding] is the bar's bottom), so it comes out
 * from beneath the bar and settles under it; Compose's own pull distance, clip and limit stay as they are. A click when
 * the pull is far enough to let go ([Haptics.threshold]), a strong one when it's let go ([Haptics.refresh]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppPullToRefresh(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val state = rememberPullToRefreshState()
    val haptics = rememberHaptics()
    // Only the finger's pull clicks: while refreshing, the indicator also slides to the line on its own.
    val refreshing by rememberUpdatedState(isRefreshing)
    LaunchedEffect(state, haptics) {
        snapshotFlow { state.distanceFraction >= 1f }.distinctUntilChanged().drop(1).filter { it && !refreshing }.collect { haptics.threshold() }
    }
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = {
            haptics.refresh()
            onRefresh()
        },
        modifier = modifier.fillMaxSize(),
        state = state,
        indicator = {
            PullToRefreshDefaults.Indicator(
                state = state,
                isRefreshing = isRefreshing,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = contentPadding.calculateTopPadding()),
            )
        },
        content = content,
    )
}
