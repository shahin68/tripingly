package com.falcon.tripingly.core.designsystem.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import platform.UIKit.UIImpactFeedbackGenerator
import platform.UIKit.UIImpactFeedbackStyle
import platform.UIKit.UINotificationFeedbackGenerator
import platform.UIKit.UINotificationFeedbackType

@Composable
internal actual fun platformHapticFeedback(): HapticFeedback = remember { TapticFeedback() }

/** The Taptic Engine at the strength Android's clicks have: rigid taps for steps, firmer ones for toggles and presses. */
private class TapticFeedback : HapticFeedback {
    private val rigid = UIImpactFeedbackGenerator(UIImpactFeedbackStyle.UIImpactFeedbackStyleRigid)
    private val medium = UIImpactFeedbackGenerator(UIImpactFeedbackStyle.UIImpactFeedbackStyleMedium)
    private val heavy = UIImpactFeedbackGenerator(UIImpactFeedbackStyle.UIImpactFeedbackStyleHeavy)
    private val notification = UINotificationFeedbackGenerator()

    override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
        when (hapticFeedbackType) {
            HapticFeedbackType.SegmentTick, HapticFeedbackType.GestureThresholdActivate -> rigid.impactOccurredWithIntensity(1.0)
            HapticFeedbackType.ToggleOn, HapticFeedbackType.ToggleOff -> medium.impactOccurredWithIntensity(1.0)
            HapticFeedbackType.LongPress -> heavy.impactOccurred()
            HapticFeedbackType.Confirm -> notification.notificationOccurred(UINotificationFeedbackType.UINotificationFeedbackTypeSuccess)
            HapticFeedbackType.Reject -> notification.notificationOccurred(UINotificationFeedbackType.UINotificationFeedbackTypeError)
            else -> rigid.impactOccurredWithIntensity(1.0)
        }
    }
}
