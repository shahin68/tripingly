package com.falcon.tripingly.core.designsystem.component

import android.content.Context
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * Compose's own [LocalHapticFeedback], except for a drag's fine steps and its end: through the view those felt the
 * same on devices (Shahin, 2026-11-17), so they play the vibrator's own light tick and its strongest click, which
 * phones keep clearly apart.
 */
@Composable
internal actual fun platformHapticFeedback(): HapticFeedback {
    val view = LocalHapticFeedback.current
    val context = LocalContext.current
    return remember(view, context) { DragFeedback(view, context.vibrator()) }
}

private class DragFeedback(private val view: HapticFeedback, private val vibrator: Vibrator?) : HapticFeedback {
    override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
        when {
            vibrator == null || !vibrator.hasVibrator() -> view.performHapticFeedback(hapticFeedbackType)
            hapticFeedbackType == HapticFeedbackType.SegmentFrequentTick -> vibrate(fineTick())
            hapticFeedbackType == HapticFeedbackType.GestureEnd -> vibrate(end())
            else -> view.performHapticFeedback(hapticFeedbackType)
        }
    }

    private fun fineTick(): VibrationEffect = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && vibrator!!.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_TICK) ->
            VibrationEffect.startComposition().addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, TICK_SCALE).compose()
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
        else -> VibrationEffect.createOneShot(TICK_MILLIS, TICK_AMPLITUDE)
    }

    private fun end(): VibrationEffect = when {
        // A thud, where the phone has one, with a full click on it and a second full click off the wall: like hitting
        // a wall (Shahin, 2026-11-17, "even a bit harder").
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && vibrator!!.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_THUD, VibrationEffect.Composition.PRIMITIVE_CLICK) ->
            VibrationEffect.startComposition()
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 1f)
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 1f)
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 1f, END_ECHO_DELAY_MILLIS)
                .compose()
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && vibrator!!.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_CLICK) ->
            VibrationEffect.startComposition()
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 1f)
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 1f, END_ECHO_DELAY_MILLIS)
                .compose()
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)
        else -> VibrationEffect.createOneShot(END_MILLIS, VibrationEffect.DEFAULT_AMPLITUDE)
    }

    // As touch feedback, so the phone's touch feedback setting still applies.
    private fun vibrate(effect: VibrationEffect) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            vibrator!!.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_TOUCH))
        } else {
            vibrator!!.vibrate(effect)
        }
    }
}

private fun Context.vibrator(): Vibrator? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

/** A fine step is a light tap, well under the end's full click. */
private const val TICK_SCALE = 0.7f
private const val TICK_MILLIS = 8L
private const val TICK_AMPLITUDE = 90
private const val END_MILLIS = 30L
private const val END_ECHO_DELAY_MILLIS = 25
