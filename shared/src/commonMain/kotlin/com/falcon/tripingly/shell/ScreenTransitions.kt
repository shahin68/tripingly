package com.falcon.tripingly.shell

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith

/**
 * How screens come and go, on both platforms (Shahin, 2026-11-08): a new screen slides up from the bottom while the one
 * under it shrinks back a little, as iOS shows a sheet; back slides it down again while the one under it grows back
 * (the trip map leaves to the right). A back swipe runs the back transition with the finger.
 */
internal object ScreenTransitions {

    fun <S> push(): AnimatedContentTransitionScope<S>.() -> ContentTransform = {
        slideInVertically(tween(DURATION_MILLIS, easing = Ease)) { it } togetherWith
            scaleOut(tween(DURATION_MILLIS, easing = Ease), targetScale = BEHIND_SCALE)
    }

    fun <S> pop(): AnimatedContentTransitionScope<S>.() -> ContentTransform = { back() }

    /** A back swipe runs the same transition with the finger; letting go (or a back press) finishes it as fast. */
    fun <S> predictivePop(): AnimatedContentTransitionScope<S>.(Int) -> ContentTransform = { back() }

    /**
     * The trip map leaves to the right, the way iOS's back swipe moves from the left edge, so a back swipe doesn't
     * drag the map around under the finger (Shahin, 2026-11-08).
     */
    fun <S> mapPop(): AnimatedContentTransitionScope<S>.() -> ContentTransform = { mapBack() }

    fun <S> mapPredictivePop(): AnimatedContentTransitionScope<S>.(Int) -> ContentTransform = { mapBack() }

    private fun back(): ContentTransform =
        scaleIn(tween(BACK_MILLIS, easing = Ease), initialScale = BEHIND_SCALE) togetherWith
            slideOutVertically(tween(BACK_MILLIS, easing = Ease)) { it }

    private fun mapBack(): ContentTransform =
        scaleIn(tween(BACK_MILLIS, easing = Ease), initialScale = BEHIND_SCALE) togetherWith
            slideOutHorizontally(tween(BACK_MILLIS, easing = Ease)) { it }

    private const val DURATION_MILLIS = 450
    /** Back is quicker than coming in: a back press or a let-go swipe closes the screen briskly (Shahin, 2026-11-08). */
    private const val BACK_MILLIS = 280
    /** How far the screen underneath shrinks back. */
    private const val BEHIND_SCALE = 0.92f
    /**
     * Material's emphasized curve: a gentle start and a soft landing. iOS's own (0.28, 0.99, 0.32, 0.99) covered
     * most of the way in the first frames, so screens seemed to flash in (Shahin, 2026-11-08).
     */
    private val Ease = CubicBezierEasing(0.2f, 0f, 0f, 1f)
}
