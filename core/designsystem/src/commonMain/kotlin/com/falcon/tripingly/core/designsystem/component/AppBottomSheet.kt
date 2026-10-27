package com.falcon.tripingly.core.designsystem.component

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch

/**
 * The app's bottom sheet: it slides up when [value] arrives and slides down before it leaves, however it closes —
 * a swipe, a tap outside, back, the `close` handed to [content] (for [SheetHeader]'s button), or the view model
 * clearing [value] (e.g. once a trip is created). [onDismiss] tells the view model the user closed it; while
 * [dismissEnabled] is false (saving) the user can't.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T : Any> AppBottomSheet(
    value: T?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    dismissEnabled: Boolean = true,
    content: @Composable (value: T, close: () -> Unit) -> Unit,
) {
    val canDismiss by rememberUpdatedState(dismissEnabled)
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { it != SheetValue.Hidden || canDismiss },
    )
    // The last value stays on screen while the sheet slides away.
    var shown by remember { mutableStateOf(value) }
    LaunchedEffect(value) {
        if (value != null) {
            shown = value
            if (sheetState.targetValue == SheetValue.Hidden && sheetState.isVisible) sheetState.show()
        } else if (shown != null) {
            sheetState.hide()
            shown = null
        }
    }
    val current = value ?: shown ?: return
    val scope = rememberCoroutineScope()
    val close: () -> Unit = { scope.launch { sheetState.hide() }.invokeOnCompletion { currentOnDismiss() } }
    ModalBottomSheet(onDismissRequest = { currentOnDismiss() }, sheetState = sheetState, modifier = modifier) {
        content(current, close)
    }
}
