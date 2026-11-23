package com.falcon.tripingly.core.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.falcon.tripingly.core.designsystem.theme.spacing
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter

/**
 * Pull to refresh for a screen under the floating top bar, on Compose's own [PullToRefreshBox] and its state. Wrap
 * the whole [LoadStateContent] in it, so its error and empty screens can be pulled too and a pull does what Try again
 * does (Shahin, 2026-11-23); nothing pulls while the skeleton shows (it doesn't scroll).
 *
 * iOS-like (Shahin, 2026-11-23, the pull to refresh canvas): the page follows the finger down and stays down while
 * refreshing, and a plain spinner under the bar (the top of [contentPadding]) fades and grows in as its ring draws
 * itself, whole at the line, spinning once let go. It never moves, so nothing slides out from behind a clip line.
 * Compose's state keeps the pull distance, its limit and the easing back. A click when the pull is far enough to let
 * go ([Haptics.threshold]), a strong one when it's let go ([Haptics.refresh]).
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
    // Only the finger's pull clicks: while refreshing, the state also eases to the line on its own.
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
            PullSpinner(
                state = state,
                isRefreshing = isRefreshing,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = contentPadding.calculateTopPadding() + MaterialTheme.spacing.medium),
            )
        },
    ) {
        // The page follows the finger; read while drawing, so pulling never recomposes.
        Box(Modifier.fillMaxSize().graphicsLayer { translationY = state.distanceFraction * PullDistance.toPx() }) { content() }
    }
}

/** Fixed in place: fades and grows in with the pull while its ring draws itself, then spins while refreshing. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PullSpinner(state: PullToRefreshState, isRefreshing: Boolean, modifier: Modifier = Modifier) {
    val shown = { state.distanceFraction.coerceIn(0f, 1f) }
    Box(
        modifier.size(SpinnerSize).graphicsLayer {
            alpha = shown()
            scaleX = MIN_SCALE + (1f - MIN_SCALE) * shown()
            scaleY = scaleX
        },
    ) {
        if (isRefreshing) {
            CircularProgressIndicator(Modifier.fillMaxSize(), strokeWidth = SpinnerStroke)
        } else {
            CircularProgressIndicator(progress = shown, modifier = Modifier.fillMaxSize(), strokeWidth = SpinnerStroke)
        }
    }
}

/** How far the page moves at the line: Compose's own pull threshold. */
private val PullDistance: Dp = PullToRefreshDefaults.PositionalThreshold
private val SpinnerSize = 28.dp
private val SpinnerStroke = 2.5.dp
private const val MIN_SCALE = 0.5f
