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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import kotlinx.coroutines.launch

/**
 * The app's bottom sheet: it slides up when [value] arrives and slides down before it leaves, however it closes —
 * a swipe, a tap outside, back, the `close` handed to [content] (for [SheetHeader]'s button), or the view model
 * clearing [value] (e.g. once a trip is created). [onDismiss] tells the view model the user closed it, as the sheet
 * starts sliding down. [onBack]: the sheet was opened from another sheet; its header shows a back arrow instead of ✕
 * ([LocalSheetBack], read by [SheetHeader]), and that arrow or the system back tells [onBack], which goes back to the
 * sheet before. ✕, a swipe down and a tap outside always tell [onDismiss], which closes every sheet in the chain
 * (Shahin, 2026-11-13); while
 * [dismissEnabled] is false (saving) the user can't. It hides while its screen isn't on top (see [LocalScreenOnTop]) and
 * comes back when it is, still open in the view model. [halfHigh]: it opens half high first (Material's peek), and a
 * drag or a tap on its handle takes it to its full height and back. It slides up with Material's fast spatial motion,
 * not the default one, so one sheet handing over to another (the map's details and its photos or comments) is quick
 * (Shahin, 2026-11-12); what's inside the sheet keeps the app's own motion.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun <T : Any> AppBottomSheet(
    value: T?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    dismissEnabled: Boolean = true,
    halfHigh: Boolean = false,
    content: @Composable (value: T, close: () -> Unit) -> Unit,
) {
    val canDismiss by rememberUpdatedState(dismissEnabled)
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    val currentOnBack by rememberUpdatedState(onBack)
    // The arrow or the system back started this close, not ✕, a swipe or a tap outside.
    val goingBack = remember { BooleanArray(1) }
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
            val back = currentOnBack.takeIf { goingBack[0] }
            goingBack[0] = false
            if (back != null) back() else currentOnDismiss()
        }
    }
    LaunchedEffect(sheetState) {
        // Animating, not dragged: a finger still on the sheet may pull it back up.
        snapshotFlow { sheetState.targetValue == SheetValue.Hidden && sheetState.isAnimationRunning }
            .collect { isClosing -> if (isClosing) dismiss() }
    }
    val current = visible ?: shown ?: return
    val scope = rememberCoroutineScope()
    val close: () -> Unit = { scope.launch { sheetState.hide() } }
    val goBack: (() -> Unit)? = if (onBack == null) null else {
        {
            goingBack[0] = true
            scope.launch { sheetState.hide() }
        }
    }
    val motion = MaterialTheme.motionScheme
    val sheetMotion = remember(motion) { FastSheetMotion(motion) }
    // Only the sheet's own slide is faster; its content gets the app's motion back.
    MaterialTheme(colorScheme = MaterialTheme.colorScheme, motionScheme = sheetMotion, shapes = MaterialTheme.shapes, typography = MaterialTheme.typography) {
        ModalBottomSheet(
            onDismissRequest = dismiss,
            sheetState = sheetState,
            modifier = modifier,
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
                // Back goes to the sheet before, not out of the chain.
                if (goBack != null) BackHandler(onBack = goBack)
                CompositionLocalProvider(LocalSheetBack provides goBack) { content(current, close) }
            }
        }
    }
}

/** [base], but the sheet slides in (and settles after a drag) with the fast spatial motion. */
private class FastSheetMotion(private val base: MotionScheme) : MotionScheme by base {
    override fun <T> defaultSpatialSpec(): FiniteAnimationSpec<T> = base.fastSpatialSpec()
}

/** The open sheet's way back to the sheet it was opened from, if it was; [SheetHeader] then shows a back arrow for it. */
val LocalSheetBack = compositionLocalOf<(() -> Unit)?> { null }

/** False while this screen is under another one on the back stack, so its [AppBottomSheet]s stay hidden. */
val LocalScreenOnTop = compositionLocalOf { true }

/**
 * False while this screen's opening transition still runs, then true for as long as it stays. Heavy content (the
 * native map) waits for it, so building it doesn't skip frames of the slide in.
 */
val LocalScreenArrived = compositionLocalOf { true }
