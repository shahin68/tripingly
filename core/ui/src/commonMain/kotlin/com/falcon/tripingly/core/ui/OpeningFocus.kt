package com.falcon.tripingly.core.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * The focus of a field that opens with the keyboard up (a search). A rotation builds the screen anew: the keyboard
 * comes back only if it was up, so one closed stays closed (Shahin, 2026-11-19). Only what the keyboard does while the
 * screen is in front counts, not it going as the screen is torn down. Put it on the field with `Modifier.focusRequester`.
 */
@Composable
fun rememberOpeningFocus(): FocusRequester {
    val focusRequester = remember { FocusRequester() }
    var isKeyboardWanted by rememberSaveable { mutableStateOf(true) }
    val ime = WindowInsets.ime
    val density = LocalDensity.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(Unit) {
        if (isKeyboardWanted) focusRequester.requestFocus()
        var wasShown = false
        snapshotFlow { ime.getBottom(density) > 0 }.collect { isShown ->
            if (isShown) wasShown = true
            if (wasShown && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) isKeyboardWanted = isShown
        }
    }
    return focusRequester
}
