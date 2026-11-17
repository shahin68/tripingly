package com.falcon.tripingly.core.designsystem.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import platform.UIKit.UIImpactFeedbackGenerator
import platform.darwin.DISPATCH_TIME_NOW
import platform.darwin.dispatch_after
import platform.darwin.dispatch_get_main_queue
import platform.darwin.dispatch_time
import platform.UIKit.UIImpactFeedbackStyle
import platform.UIKit.UINotificationFeedbackGenerator
import platform.UIKit.UINotificationFeedbackType
import platform.UIKit.UISelectionFeedbackGenerator

@Composable
internal actual fun platformHapticFeedback(): HapticFeedback = remember { TapticFeedback() }

/** The Taptic Engine at the strength Android's clicks have: rigid taps for steps, firmer ones for toggles and presses. */
private class TapticFeedback : HapticFeedback {
    private val rigid = UIImpactFeedbackGenerator(UIImpactFeedbackStyle.UIImpactFeedbackStyleRigid)
    private val medium = UIImpactFeedbackGenerator(UIImpactFeedbackStyle.UIImpactFeedbackStyleMedium)
    private val heavy = UIImpactFeedbackGenerator(UIImpactFeedbackStyle.UIImpactFeedbackStyleHeavy)
    private val notification = UINotificationFeedbackGenerator()
    private val selection = UISelectionFeedbackGenerator()

    override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
        when (hapticFeedbackType) {
            HapticFeedbackType.SegmentTick, HapticFeedbackType.GestureThresholdActivate -> rigid.impactOccurredWithIntensity(1.0)
            // A drag's fine steps: the picker's own ratchet; its end: the heaviest tap.
            HapticFeedbackType.SegmentFrequentTick -> selection.selectionChanged()
            // A heavy tap and a quick rigid one off the wall: sharper than any single tap (Shahin, 2026-11-17).
            HapticFeedbackType.GestureEnd -> {
                heavy.impactOccurredWithIntensity(1.0)
                dispatch_after(dispatch_time(DISPATCH_TIME_NOW, END_ECHO_DELAY_NANOS), dispatch_get_main_queue()) { rigid.impactOccurredWithIntensity(1.0) }
            }
            HapticFeedbackType.ToggleOn, HapticFeedbackType.ToggleOff -> medium.impactOccurredWithIntensity(1.0)
            HapticFeedbackType.LongPress -> heavy.impactOccurred()
            HapticFeedbackType.Confirm -> notification.notificationOccurred(UINotificationFeedbackType.UINotificationFeedbackTypeSuccess)
            HapticFeedbackType.Reject -> notification.notificationOccurred(UINotificationFeedbackType.UINotificationFeedbackTypeError)
            else -> rigid.impactOccurredWithIntensity(1.0)
        }
    }
}

/** The wall's second tap comes this soon after the first. */
private const val END_ECHO_DELAY_NANOS = 30_000_000L
