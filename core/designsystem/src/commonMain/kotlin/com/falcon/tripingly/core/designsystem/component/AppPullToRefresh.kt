package com.falcon.tripingly.core.designsystem.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.tooling.preview.Preview
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme
import com.falcon.tripingly.core.designsystem.theme.spacing

/**
 * Pull to refresh for a screen under the floating top bar, on Compose's own [PullToRefreshBox] and its state. Wrap
 * the whole [LoadStateContent] in it, so its error and empty screens can be pulled too and a pull does what Try again
 * does (Shahin, 2026-11-23); nothing pulls while the skeleton shows (it doesn't scroll).
 *
 * iOS-like (Shahin, 2026-11-23, the pull to refresh canvas and the refinements after it): the page follows the finger
 * down and stays down while refreshing. The spinner lies under the page and grows out of the page's top edge (the top
 * of [contentPadding]) into the gap the page opens: centred in the gap, never bigger than it, its ring drawing itself,
 * whole at the line with a quick bounce, spinning once let go. Compose's state keeps the pull distance, its limit and
 * the easing back. A click when the pull is far enough to let go ([Haptics.threshold]), a strong one when it's let go
 * ([Haptics.refresh]).
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
    val bounce = remember { Animatable(1f) }
    // Only the finger's pull clicks and bounces: while refreshing, the state also eases to the line on its own.
    val refreshing by rememberUpdatedState(isRefreshing)
    LaunchedEffect(state, haptics) {
        snapshotFlow { state.distanceFraction >= 1f }.distinctUntilChanged().drop(1).filter { it && !refreshing }.collectLatest {
            haptics.threshold()
            bounce.animateTo(BOUNCE_SCALE, tween(BOUNCE_UP_MILLIS))
            bounce.animateTo(1f, spring(dampingRatio = BOUNCE_DAMPING, stiffness = Spring.StiffnessMedium))
        }
    }
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = {
            haptics.refresh()
            onRefresh()
        },
        modifier = modifier.fillMaxSize(),
        state = state,
        indicator = {},
    ) {
        // Declared first, so the page draws over it.
        PullSpinner(
            state = state,
            isRefreshing = isRefreshing,
            bounce = { bounce.value },
            modifier = Modifier.align(Alignment.TopCenter).padding(top = contentPadding.calculateTopPadding()),
        )
        // The page follows the finger; read while drawing, so pulling never recomposes.
        Box(Modifier.fillMaxSize().graphicsLayer { translationY = state.distanceFraction * PullDistance.toPx() }) { content() }
    }
}

/**
 * Starts as a dot on the page's top edge and stays centred in the gap the page opens, as big as the gap allows (with
 * [SpinnerMargin] free above and below), so it never overlaps the page or goes under the bar. Past the line it stays.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PullSpinner(state: PullToRefreshState, isRefreshing: Boolean, bounce: () -> Float, modifier: Modifier = Modifier) {
    val pulled = { state.distanceFraction.coerceIn(0f, 1f) }
    Box(
        modifier.size(SpinnerSize).graphicsLayer {
            val gap = pulled() * PullDistance.toPx()
            val grown = ((gap - 2 * SpinnerMargin.toPx()) / size.height).coerceIn(0f, 1f)
            translationY = (gap - size.height) / 2f
            alpha = grown
            scaleX = grown * bounce()
            scaleY = scaleX
        },
    ) {
        if (isRefreshing) {
            CircularProgressIndicator(Modifier.fillMaxSize(), strokeWidth = SpinnerStroke)
        } else {
            CircularProgressIndicator(progress = pulled, modifier = Modifier.fillMaxSize(), strokeWidth = SpinnerStroke)
        }
    }
}

/** How far the page moves at the line: Compose's own pull threshold. */
private val PullDistance: Dp = PullToRefreshDefaults.PositionalThreshold
private val SpinnerSize = 28.dp
private val SpinnerStroke = 2.5.dp
private val SpinnerMargin = 8.dp
private const val BOUNCE_SCALE = 1.2f
private const val BOUNCE_UP_MILLIS = 90
private const val BOUNCE_DAMPING = 0.45f

/** A page of cards while it refreshes: pulled down, the spinner in the gap above it. */
@Composable
private fun AppPullToRefreshPreviewContent() {
    AppPullToRefresh(
        isRefreshing = true,
        onRefresh = {},
        contentPadding = PaddingValues(top = MaterialTheme.spacing.medium),
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(MaterialTheme.spacing.medium),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
        ) {
            repeat(6) { Box(Modifier.fillMaxWidth().height(72.dp).background(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.shapes.large)) }
        }
    }
}

@Preview(heightDp = 480)
@Composable
private fun AppPullToRefreshPreview() {
    TripinglyTheme { AppPullToRefreshPreviewContent() }
}

@Preview(heightDp = 480)
@Composable
private fun AppPullToRefreshDarkPreview() {
    TripinglyTheme(darkTheme = true) { AppPullToRefreshPreviewContent() }
}
