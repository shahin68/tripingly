package com.falcon.tripingly.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme
import com.falcon.tripingly.core.designsystem.theme.spacing
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** One button behind a swiped row; a [isDestructive] one is red. */
@Immutable
data class SwipeAction(val label: String, val icon: ImageVector, val isDestructive: Boolean = false, val onClick: () -> Unit)

private enum class SwipeValue { Closed, Open }

/**
 * A row ([content], on a card with [MaterialTheme.shapes.large] corners) that slides to the start to show [actions]
 * behind its end, like a mail or social app. A tap on an action closes it again and runs it.
 */
@Composable
fun SwipeActions(actions: List<SwipeAction>, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val state = remember { AnchoredDraggableState(SwipeValue.Closed) }
    val scope = rememberCoroutineScope()
    val open = with(LocalDensity.current) { (SwipeActionWidth * actions.size).toPx() }
    LaunchedEffect(open) {
        state.updateAnchors(
            DraggableAnchors {
                SwipeValue.Closed at 0f
                SwipeValue.Open at -open
            },
        )
    }
    Box(modifier.fillMaxWidth().clip(MaterialTheme.shapes.large)) {
        Row(Modifier.matchParentSize(), horizontalArrangement = Arrangement.End) {
            actions.forEach { action ->
                val container = if (action.isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.surfaceContainerHigh
                val tint = if (action.isDestructive) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onSurface
                Column(
                    modifier = Modifier
                        .width(SwipeActionWidth)
                        .fillMaxHeight()
                        .background(container)
                        .clickable {
                            scope.launch { state.animateTo(SwipeValue.Closed) }
                            action.onClick()
                        }
                        .padding(MaterialTheme.spacing.extraSmall),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(action.icon, contentDescription = null, tint = tint)
                    Text(action.label, style = MaterialTheme.typography.labelSmall, color = tint, textAlign = TextAlign.Center, maxLines = 2)
                }
            }
        }
        Box(
            Modifier
                .offset { IntOffset(state.offset.takeUnless { it.isNaN() }?.roundToInt() ?: 0, 0) }
                .anchoredDraggable(state, Orientation.Horizontal),
        ) { content() }
    }
}

private val SwipeActionWidth = 80.dp

@Preview
@Composable
private fun SwipeActionsPreview() {
    TripinglyTheme {
        SwipeActions(
            actions = listOf(
                SwipeAction("Block", Icons.Outlined.Block) {},
                SwipeAction("Delete", Icons.Outlined.Delete, isDestructive = true) {},
            ),
        ) {
            Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                Text("Anna and Jonas commented on Café Central", Modifier.padding(MaterialTheme.spacing.medium))
            }
        }
    }
}
