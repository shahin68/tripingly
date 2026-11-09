package com.falcon.tripingly.core.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay

/**
 * One line of text that changes by scrambling: its letters shuffle and lock into the new text from start to end,
 * like a departure board, with a haptic tick as letters lock. A change undone within [SCRAMBLE_DEBOUNCE] never shows,
 * a new change cancels the running one and starts from what is on screen, and it is done within [SCRAMBLE_MAX_TIME]
 * whatever the length. Screen readers only ever hear [text].
 */
@Composable
fun ScrambleText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
) {
    val haptics = rememberHaptics()
    var shown by remember { mutableStateOf(text) }
    LaunchedEffect(text) {
        scramble(from = shown, to = text, random = Random.Default, onFrame = { shown = it }, onLetterLocked = haptics::tick)
    }
    Box(modifier.clearAndSetSemantics { contentDescription = text }) {
        // The new text, unseen, keeps the width steady while the letters shuffle.
        Text(text, Modifier.alpha(0f), color = color, style = style, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(shown, color = color, style = style, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * Shows the frames from [from] to [to] through [onFrame], the last one always [to]. Waits [SCRAMBLE_DEBOUNCE] first,
 * then every [SCRAMBLE_FRAME] locks the next letters in order (all of them within [SCRAMBLE_MAX_FRAMES] frames) and
 * shows the rest as random letters of [to]; anything that isn't a letter (digits, spaces, "…", emoji) shows as it is.
 * Calls [onLetterLocked] once per frame that locks a letter. Cancelling it stops it between frames.
 */
internal suspend fun scramble(from: String, to: String, random: Random, onFrame: (String) -> Unit, onLetterLocked: () -> Unit) {
    if (from == to) return
    delay(SCRAMBLE_DEBOUNCE)
    val letters = to.indices.filter { to[it].isLetter() }
    if (letters.isEmpty()) return onFrame(to)
    val perFrame = (letters.size + SCRAMBLE_MAX_FRAMES - 1) / SCRAMBLE_MAX_FRAMES
    val chars = to.toCharArray()
    var locked = 0
    while (locked < letters.size) {
        for (i in locked until letters.size) chars[letters[i]] = to[letters[random.nextInt(letters.size)]]
        onFrame(chars.concatToString())
        delay(SCRAMBLE_FRAME)
        val next = minOf(locked + perFrame, letters.size)
        for (i in locked until next) chars[letters[i]] = to[letters[i]]
        locked = next
        onLetterLocked()
    }
    onFrame(to)
}

internal val SCRAMBLE_DEBOUNCE = 90.milliseconds
internal val SCRAMBLE_FRAME = 24.milliseconds
internal const val SCRAMBLE_MAX_FRAMES = 10

/** The longest a change takes on screen, debounce included. */
internal val SCRAMBLE_MAX_TIME = SCRAMBLE_DEBOUNCE + SCRAMBLE_FRAME * SCRAMBLE_MAX_FRAMES

@Preview
@Composable
private fun ScrambleTextPreview() {
    TripinglyTheme { ScrambleText("6 stops", style = MaterialTheme.typography.titleMedium) }
}
