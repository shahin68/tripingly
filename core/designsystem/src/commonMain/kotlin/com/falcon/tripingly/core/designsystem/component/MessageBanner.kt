package com.falcon.tripingly.core.designsystem.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animate
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.falcon.tripingly.core.designsystem.generated.resources.Res
import com.falcon.tripingly.core.designsystem.generated.resources.common_dismiss
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme
import com.falcon.tripingly.core.designsystem.theme.spacing
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.compose.resources.stringResource

/**
 * The app's one floating message. Screens hand it messages with [ShowMessage]; it shows one at a time,
 * hides each after [VISIBLE] (with an action, [VISIBLE_WITH_ACTION]), and a newer message replaces the one
 * on show once that has been up for [MIN_VISIBLE]. A message already on show or waiting isn't repeated.
 */
@Stable
class MessageBanner(private val scope: CoroutineScope) {
    class Message internal constructor(
        val id: Long,
        val text: String,
        val isError: Boolean,
        val actionLabel: String?,
        val onAction: (() -> Unit)?,
    )

    var current: Message? by mutableStateOf(null)
        private set

    /** Hosts on screen, oldest first; only the newest draws (see [MessageBannerHost]). */
    internal val hosts = mutableStateListOf<Any>()

    private val pending = ArrayDeque<Message>()
    private val changed = Channel<Unit>(Channel.CONFLATED)
    private var nextId = 0L
    private var showing: Job? = null

    fun show(text: String, isError: Boolean = true, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
        if (current?.text == text || pending.any { it.text == text }) return
        pending.addLast(Message(nextId++, text, isError, actionLabel?.takeIf { onAction != null }, onAction))
        changed.trySend(Unit)
        if (showing?.isActive != true) showing = scope.launch { showAll() }
    }

    fun dismiss(message: Message) {
        if (current?.id != message.id) return
        current = null
        changed.trySend(Unit)
    }

    private suspend fun showAll() {
        while (true) {
            val message = pending.removeFirstOrNull() ?: break
            current = message
            val isGone = { current?.id != message.id }
            withTimeoutOrNull(MIN_VISIBLE) { awaitUntil(isGone) }
            if (!isGone() && pending.isEmpty()) {
                val visible = if (message.onAction != null) VISIBLE_WITH_ACTION else VISIBLE
                withTimeoutOrNull(visible - MIN_VISIBLE) { awaitUntil { isGone() || pending.isNotEmpty() } }
            }
        }
        current = null
    }

    private suspend fun awaitUntil(condition: () -> Boolean) {
        while (!condition()) changed.receive()
    }

    companion object {
        val VISIBLE: Duration = 4.seconds
        val VISIBLE_WITH_ACTION: Duration = 8.seconds
        val MIN_VISIBLE: Duration = 1500.milliseconds
    }
}

/** Provided by [com.falcon.tripingly.core.designsystem.theme.TripinglyTheme]. */
val LocalMessageBanner = staticCompositionLocalOf<MessageBanner> { error("No MessageBanner provided") }

@Composable
fun rememberMessageBanner(): MessageBanner {
    val scope = rememberCoroutineScope()
    return remember(scope) { MessageBanner(scope) }
}

/**
 * Hands [message] to the app's banner once, then calls [onShown] so the screen's state can forget it.
 * A confirmation (not [isError]) shows in neutral colors. [onAction] (e.g. Retry) shows as [actionLabel].
 */
@Composable
fun ShowMessage(
    message: String?,
    onShown: () -> Unit,
    isError: Boolean = true,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val banner = LocalMessageBanner.current
    val currentOnShown by rememberUpdatedState(onShown)
    LaunchedEffect(message) {
        if (message != null) {
            banner.show(message, isError, actionLabel, onAction)
            currentOnShown()
        }
    }
}

/**
 * Draws the banner's message, floating at the top of where it's placed. The app has one at its root; every
 * modal sheet has its own, since a sheet is drawn above the app with a scrim. Only the newest host draws.
 */
@Composable
fun MessageBannerHost(modifier: Modifier = Modifier) {
    val banner = LocalMessageBanner.current
    val key = remember { Any() }
    DisposableEffect(banner, key) {
        banner.hosts.add(key)
        onDispose { banner.hosts.remove(key) }
    }
    val message = banner.current.takeIf { banner.hosts.lastOrNull() === key }
    AnimatedContent(
        targetState = message,
        modifier = modifier,
        transitionSpec = { (fadeIn() + slideInVertically { -it }) togetherWith (fadeOut() + slideOutVertically { -it }) },
        contentKey = { it?.id },
        label = "MessageBanner",
    ) { shown ->
        if (shown != null) {
            FloatingBanner(
                text = shown.text,
                onDismiss = { banner.dismiss(shown) },
                isError = shown.isError,
                actionLabel = shown.actionLabel,
                onAction = shown.onAction?.let { action -> { banner.dismiss(shown); action() } },
            )
        }
    }
}

/** A card that floats over the screen (error colors unless not [isError]); swipe it sideways or tap the cross to hide it. */
@Composable
fun FloatingBanner(
    text: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    isError: Boolean = true,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    var offsetX by remember { mutableFloatStateOf(0f) }
    var width by remember { mutableIntStateOf(0) }
    Surface(
        modifier = modifier
            .padding(horizontal = MaterialTheme.spacing.medium, vertical = MaterialTheme.spacing.small)
            .widthIn(max = 560.dp)
            .fillMaxWidth()
            .onSizeChanged { width = it.width }
            .offset { IntOffset(offsetX.roundToInt(), 0) }
            .draggable(
                orientation = Orientation.Horizontal,
                state = rememberDraggableState { offsetX += it },
                onDragStopped = { velocity ->
                    if (abs(offsetX) > width / 3f || abs(velocity) > 1000f) {
                        onDismiss()
                    } else {
                        animate(offsetX, 0f) { value, _ -> offsetX = value }
                    }
                },
            )
            .semantics { liveRegion = LiveRegionMode.Polite },
        shape = RoundedCornerShape(12.dp),
        color = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.inverseSurface,
        contentColor = if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.inverseOnSurface,
        shadowElevation = 6.dp,
    ) {
        Row(
            modifier = Modifier.padding(start = MaterialTheme.spacing.medium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f).padding(vertical = 12.dp),
            )
            if (actionLabel != null && onAction != null) {
                TextButton(onClick = onAction) {
                    Text(text = actionLabel, color = LocalContentColor.current)
                }
            }
            IconButton(onClick = onDismiss) {
                Icon(imageVector = Icons.Filled.Close, contentDescription = stringResource(Res.string.common_dismiss))
            }
        }
    }
}

@Preview
@Composable
private fun FloatingBannerNotePreview() {
    TripinglyTheme {
        FloatingBanner(text = "Saved to My Trips.", onDismiss = {}, isError = false)
    }
}

@Preview
@Composable
private fun FloatingBannerPreview() {
    TripinglyTheme {
        FloatingBanner(
            text = "Couldn't load your trips. No internet connection. Check your connection and try again.",
            onDismiss = {},
            actionLabel = "Retry",
            onAction = {},
        )
    }
}
