package com.falcon.tripingly.core.designsystem.component

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.foundation.clickable
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource
import kotlinx.coroutines.launch

/**
 * The app's bottom sheet: it slides up when [value] arrives and slides down before it leaves, however it closes —
 * a swipe, a tap outside, back, the `close` handed to [content] (for [SheetHeader]'s button), or the view model
 * clearing [value] (e.g. once a trip is created). [onDismiss] tells the view model the user closed it, as the sheet
 * starts sliding down; a swipe down tells [onSwipeDismiss] instead, where a sheet opened from another closes them all
 * at once (Shahin, 2026-11-13: ✕ and back go back one sheet, a swipe closes the whole chain); while
 * [dismissEnabled] is false (saving) the user can't. It hides while its screen isn't on top (see [LocalScreenOnTop]) and
 * comes back when it is, still open in the view model. [halfHigh]: it opens half high first (Material's peek), and a
 * drag or a tap on its handle takes it to its full height and back. It slides up with Material's fast spatial motion,
 * not the default one, so one sheet handing over to another (the map's details and its photos or comments) is quick
 * (Shahin, 2026-11-12); what's inside the sheet keeps the app's own motion.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T : Any> AppBottomSheet(
    value: T?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onSwipeDismiss: () -> Unit = onDismiss,
    dismissEnabled: Boolean = true,
    halfHigh: Boolean = false,
    content: @Composable (value: T, close: () -> Unit) -> Unit,
) {
    val canDismiss by rememberUpdatedState(dismissEnabled)
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    val currentOnSwipeDismiss by rememberUpdatedState(onSwipeDismiss)
    val touch = remember { SheetTouch() }
    // A sheet is a window over everything: shown under another screen (e.g. the screen a back swipe goes back to), it
    // would cover that screen and take its back gesture.
    val visible = value.takeIf { LocalScreenOnTop.current }
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = !halfHigh,
        confirmValueChange = { it != SheetValue.Hidden || canDismiss },
    )
    // The last value stays on screen while the sheet slides away.
    var shown by remember { mutableStateOf(visible) }
    // The value whose close the view model was already told of (see below).
    var dismissed by remember { mutableStateOf<T?>(null) }
    LaunchedEffect(visible) {
        if (visible != null) {
            shown = visible
            dismissed = null
            if (sheetState.targetValue == SheetValue.Hidden && sheetState.isVisible) sheetState.show()
        } else if (shown != null) {
            sheetState.hide()
            shown = null
        }
    }
    // The view model hears of a close once per value, as soon as the sheet heads down (a swipe, back, a tap outside,
    // `close`), not after it's gone: a sheet waiting behind it (the map's details under its comments) comes up while
    // this one slides away (Shahin, 2026-11-12). A close the view model asked for itself isn't told back.
    val latestVisible by rememberUpdatedState(visible)
    val dismiss: () -> Unit = {
        val closing = latestVisible
        if (closing != null && dismissed !== closing) {
            dismissed = closing
            if (touch.wasSwipe()) currentOnSwipeDismiss() else currentOnDismiss()
        }
    }
    LaunchedEffect(sheetState) {
        // Animating, not dragged: a finger still on the sheet may pull it back up.
        snapshotFlow { sheetState.targetValue == SheetValue.Hidden && sheetState.isAnimationRunning }
            .collect { isClosing -> if (isClosing) dismiss() }
    }
    val current = visible ?: shown ?: return
    val scope = rememberCoroutineScope()
    val close: () -> Unit = {
        touch.isButtonClose = true
        scope.launch { sheetState.hide() }
    }
    val motion = MaterialTheme.motionScheme
    val sheetMotion = remember(motion) { FastSheetMotion(motion) }
    // Only the sheet's own slide is faster; its content gets the app's motion back.
    MaterialTheme(colorScheme = MaterialTheme.colorScheme, motionScheme = sheetMotion, shapes = MaterialTheme.shapes, typography = MaterialTheme.typography) {
        ModalBottomSheet(
            onDismissRequest = dismiss,
            sheetState = sheetState,
            // Watches fingers on the sheet without taking them, to tell a swipe from ✕ or back.
            modifier = modifier.pointerInput(touch) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    touch.isButtonClose = false
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                    } while (event.changes.any { it.pressed })
                    touch.releasedAt = TimeSource.Monotonic.markNow()
                }
            },
            dragHandle = {
                BottomSheetDefaults.DragHandle(
                    modifier = if (halfHigh) {
                        Modifier.clickable(interactionSource = null, indication = null) {
                            scope.launch { if (sheetState.currentValue == SheetValue.Expanded) sheetState.partialExpand() else sheetState.expand() }
                        }
                    } else {
                        Modifier
                    },
                )
            },
        ) {
            MaterialTheme(colorScheme = MaterialTheme.colorScheme, motionScheme = motion, shapes = MaterialTheme.shapes, typography = MaterialTheme.typography) {
                content(current, close)
            }
        }
    }
}

/**
 * How the user is closing a sheet: a swipe if it heads down right as a finger leaves it, unless that finger tapped the
 * `close` button; ✕, back and a tap outside aren't swipes.
 */
private class SheetTouch {
    var releasedAt: TimeMark? = null
    var isButtonClose = false

    fun wasSwipe(): Boolean {
        val released = releasedAt
        val swiped = !isButtonClose && released != null && released.elapsedNow() < SwipeReleaseWindow
        releasedAt = null
        isButtonClose = false
        return swiped
    }
}

/** A swipe that ends a sheet starts it sliding down within a frame or two of the finger leaving. */
private val SwipeReleaseWindow = 250.milliseconds

/** [base], but the sheet slides in (and settles after a drag) with the fast spatial motion. */
private class FastSheetMotion(private val base: MotionScheme) : MotionScheme by base {
    override fun <T> defaultSpatialSpec(): FiniteAnimationSpec<T> = base.fastSpatialSpec()
}

/** False while this screen is under another one on the back stack, so its [AppBottomSheet]s stay hidden. */
val LocalScreenOnTop = compositionLocalOf { true }

/**
 * False while this screen's opening transition still runs, then true for as long as it stays. Heavy content (the
 * native map) waits for it, so building it doesn't skip frames of the slide in.
 */
val LocalScreenArrived = compositionLocalOf { true }
