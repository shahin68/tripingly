package com.falcon.tripingly.core.designsystem.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

/**
 * The app's haptics: the phone's own short, sharp clicks (Android's system feedback, iOS's Taptic Engine) through
 * Compose's [HapticFeedback], so they follow the user's touch-feedback setting and need no permission. Every place
 * uses these few, so the whole app feels the same and can be retuned here. A haptic answers the user's own touch or
 * its result, never something that happens on its own (live arrivals, pushes, search results, scrolling).
 */
@Stable
class Haptics(private val feedback: HapticFeedback) {
    private var lastTick: TimeSource.Monotonic.ValueTimeMark? = null

    /** A selection moving one step: a day, a tab, a segment, a sheet settling. Never more than ten a second. */
    fun tick() {
        val now = TimeSource.Monotonic.markNow()
        if (lastTick?.let { now - it < TickGap } == true) return
        lastTick = now
        feedback.performHapticFeedback(HapticFeedbackType.SegmentTick)
    }

    /** A gesture passing the point where letting go does something (swipe to delete). */
    fun threshold() = feedback.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)

    /** A like, flame, save, follow or switch turned [on] or off; they feel different. */
    fun toggle(on: Boolean) = feedback.performHapticFeedback(if (on) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)

    /** Something the user did worked: a trip created, a stop added, a photo uploaded. */
    fun success() = feedback.performHapticFeedback(HapticFeedbackType.Confirm)

    /** Something the user did failed or switched back, or is locked. */
    fun error() = feedback.performHapticFeedback(HapticFeedbackType.Reject)

    /** A long press opening a menu or picking something up. */
    fun longPress() = feedback.performHapticFeedback(HapticFeedbackType.LongPress)
}

@Composable
fun rememberHaptics(): Haptics {
    val feedback = LocalHapticFeedback.current
    return remember(feedback) { Haptics(feedback) }
}

private val TickGap = 100.milliseconds
