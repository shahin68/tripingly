package com.falcon.tripingly.core.designsystem.component

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import kotlinx.coroutines.flow.first
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
 * (Shahin, 2026-11-12); what's inside the sheet keeps the app's own motion. [followsKeyboard] false: a sheet with no
 * field to type in doesn't rise over the keyboard, so one coming back from a sheet that typed (the members after Add
 * people) doesn't open high over the keyboard going away and then drop (Shahin, 2026-11-19).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T : Any> AppBottomSheet(
    value: T?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    dismissEnabled: Boolean = true,
    halfHigh: Boolean = false,
    followsKeyboard: Boolean = true,
    content: @Composable (value: T, close: () -> Unit) -> Unit,
) {
    val canDismiss by rememberUpdatedState(dismissEnabled)
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    // While it slides away, the sheet keeps the way back it had when it was shown: the next sheet's state has none, and
    // the header's back arrow flipped to ✕ on the way down.
    val backWhileShown = remember { arrayOfNulls<() -> Unit>(1) }
    if (value != null) backWhileShown[0] = onBack
    val sheetBack = if (value != null) onBack else backWhileShown[0]
    val currentOnBack by rememberUpdatedState(sheetBack)
    // The arrow or the system back started this close, not ✕, a swipe or a tap outside.
    val goingBack = remember { BooleanArray(1) }
    // A sheet is a window over everything: shown under another screen (e.g. the screen a back swipe goes back to), it
    // would cover that screen and take its back gesture.
    val visible = value.takeIf { LocalScreenOnTop.current }
    // A new sheet window, once, when the one opening never got its size (see below).
    var window by remember { mutableIntStateOf(0) }
    var reopened by remember { mutableStateOf(false) }
    val sheetState = key(window) {
        rememberModalBottomSheetState(
            skipPartiallyExpanded = !halfHigh,
            confirmValueChange = { it != SheetValue.Hidden || canDismiss },
        )
    }
    // iOS (Compose 1.12 and 1.13): the first sheet opened after a rotation, once a sheet had been opened and closed
    // before, never gets its window's size. Compose takes the holder of the sheet windows off the screen while none is
    // open, so it misses the rotation; the next sheet's window is then told to wait for a size animation that never
    // runs, lays out with no height limit and opens about two billion pixels below the screen (Shahin's logs,
    // 2026-11-22). A second window opens normally, so the sheet opens once more in a new one. Android never gets here.
    val windowHeightPx = LocalWindowInfo.current.containerSize.height
    LaunchedEffect(sheetState) {
        snapshotFlow {
            sheetState.targetValue != SheetValue.Hidden &&
                (runCatching { sheetState.requireOffset() }.getOrNull() ?: 0f) > windowHeightPx * 2f
        }.first { it }
        if (!reopened) {
            reopened = true
            window++
        }
    }
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
            // Already sliding down (back, ✕, a swipe): let that slide finish. Starting it again from where it is restarted
            // the motion from a standstill, and the sheet hung for a few frames while the next one came up (Shahin, 2026-11-19).
            if (sheetState.targetValue == SheetValue.Hidden && sheetState.isAnimationRunning) {
                snapshotFlow { sheetState.isAnimationRunning }.first { !it }
            }
            // Caught and held up on the way, it still goes: the view model closed it.
            if (sheetState.isVisible) sheetState.hide()
            shown = null
            reopened = false
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
    val goBack: (() -> Unit)? = if (sheetBack == null) null else {
        {
            goingBack[0] = true
            scope.launch { sheetState.hide() }
        }
    }
    val motion = MaterialTheme.motionScheme
    val sheetMotion = remember(motion) { FastSheetMotion(motion) }
    // Only the sheet's own slide is faster; its content gets the app's motion back.
    MaterialTheme(colorScheme = MaterialTheme.colorScheme, motionScheme = sheetMotion, shapes = MaterialTheme.shapes, typography = MaterialTheme.typography) {
        // A new window when the opening one never got its size (see the sheet state above).
        key(window) {
            ModalBottomSheet(
                onDismissRequest = dismiss,
                sheetState = sheetState,
                // A tall sheet stops under the status bar instead of padding its content under it. Material pads the content
                // by the status bar minus how far the sheet has moved down, so a sheet near full height changed height on every
                // frame of a drag: its anchors moved, the drag snapped back up, and ✕'s slide restarted for seconds
                // (Shahin, 2026-11-19). Padded outside the sheet, the status bar is already taken and the height stays put.
                modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top)).then(modifier),
                contentWindowInsets = {
                    if (followsKeyboard) BottomSheetDefaults.windowInsets else BottomSheetDefaults.windowInsets.exclude(WindowInsets.ime)
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
                    // Back goes to the sheet before, not out of the chain.
                    if (goBack != null) NavigationBackHandler(rememberNavigationEventState(NavigationEventInfo.None), onBackCompleted = goBack)
                    // No overscroll inside a sheet: pulled down at the top, a list moves the sheet itself. iOS's bounce kept
                    // the pull in the list, so a full-height sheet sprang back up however hard it was pulled (Shahin, 2026-11-19).
                    // Opened, the sheet says so once its slide has ended, until it goes ([LocalSheetArrived]).
                    var arrived by remember { mutableStateOf(false) }
                    LaunchedEffect(Unit) {
                        snapshotFlow { sheetState.isVisible && !sheetState.isAnimationRunning }.first { it }
                        arrived = true
                    }
                    // Never taller than the window: the iOS sheet window that missed a rotation (see the sheet state above)
                    // lays out with no height limit until it opens again, and a scroll inside would throw ("measured with an
                    // infinity maximum height"; Shahin's Xcode log, 2026-11-22). A normal measure already passes the window's
                    // height or less, so this changes nothing else.
                    // A Column, so content with several parts stacks as it did in the sheet's own column.
                    val windowHeight = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() }
                    Column(Modifier.heightIn(max = windowHeight)) {
                        CompositionLocalProvider(LocalSheetBack provides goBack, LocalOverscrollFactory provides null, LocalSheetArrived provides arrived) {
                            content(current, close)
                        }
                    }
                }
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

/**
 * False while the sheet's opening slide still runs, then true for as long as it stays open. Heavy parts of a sheet
 * (the details' photo strip) wait for it, so building them doesn't skip frames of the slide, above all while the
 * sheet before slides away (Shahin, 2026-11-19).
 */
val LocalSheetArrived = compositionLocalOf { true }

/** False while this screen is under another one on the back stack, so its [AppBottomSheet]s stay hidden. */
val LocalScreenOnTop = compositionLocalOf { true }

/**
 * False while this screen's opening transition still runs, then true for as long as it stays. Heavy content (the
 * native map) waits for it, so building it doesn't skip frames of the slide in.
 */
val LocalScreenArrived = compositionLocalOf { true }
