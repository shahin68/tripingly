package com.falcon.tripingly.shell

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith

/**
 * How screens come and go, on both platforms (Shahin, 2026-11-08): a new screen slides up from the bottom while the one
 * under it shrinks back a little, as iOS shows a sheet; back slides it down again while the one under it grows back.
 * A back swipe runs the back transition with the finger.
 */
internal object ScreenTransitions {

    fun <S> push(): AnimatedContentTransitionScope<S>.() -> ContentTransform = {
        slideInVertically(tween(DURATION_MILLIS, easing = Ease)) { it } togetherWith
            scaleOut(tween(DURATION_MILLIS, easing = Ease), targetScale = BEHIND_SCALE)
    }

    fun <S> pop(): AnimatedContentTransitionScope<S>.() -> ContentTransform = { back(Ease) }

    /** Follows the finger, so it moves evenly. */
    fun <S> predictivePop(): AnimatedContentTransitionScope<S>.(Int) -> ContentTransform = { back(LinearEasing) }

    private fun back(easing: Easing): ContentTransform =
        scaleIn(tween(DURATION_MILLIS, easing = easing), initialScale = BEHIND_SCALE) togetherWith
            slideOutVertically(tween(DURATION_MILLIS, easing = easing)) { it }

    private const val DURATION_MILLIS = 450
    /** How far the screen underneath shrinks back. */
    private const val BEHIND_SCALE = 0.92f
    /**
     * Material's emphasized curve: a gentle start and a soft landing. iOS's own (0.28, 0.99, 0.32, 0.99) covered
     * most of the way in the first frames, so screens seemed to flash in (Shahin, 2026-11-08).
     */
    private val Ease = CubicBezierEasing(0.2f, 0f, 0f, 1f)
}
