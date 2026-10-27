package com.falcon.tripingly.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Luggage
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme
import com.falcon.tripingly.core.designsystem.theme.spacing
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/** One tab of a [FloatingNavigationBar]. */
@Immutable
data class NavBarItem(val label: String, val icon: ImageVector)

/**
 * The app's bottom bar: a floating rounded glass bar whose selected tab sits in a filled accent pill. The bar
 * follows [pagerState]: while the pages are swiped the pill slides along with them, and the pill can be dragged
 * like on iOS, moving the pages with it and settling on the nearest tab when let go. A tap calls [onSelect].
 */
@Composable
fun FloatingNavigationBar(
    items: List<NavBarItem>,
    pagerState: PagerState,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var isDragging by remember { mutableStateOf(false) }
    val lift by animateFloatAsState(if (isDragging) NavBarDefaults.DragScale else 1f)
    val position = { pagerState.currentPage + pagerState.currentPageOffsetFraction }
    GlassSurface(
        modifier = modifier
            .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.displayCutout).only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
            .padding(horizontal = MaterialTheme.spacing.medium, vertical = MaterialTheme.spacing.small)
            // In landscape the bar stays a comfortable width instead of stretching across the screen.
            .widthIn(max = NavBarDefaults.MaxWidth)
            .fillMaxWidth()
            .height(NavBarDefaults.Height),
        shape = NavBarDefaults.Shape,
    ) {
        BoxWithConstraints(Modifier.fillMaxSize().padding(NavBarDefaults.InnerPadding)) {
            val itemWidth = maxWidth / items.size.coerceAtLeast(1)
            val itemPx = with(LocalDensity.current) { itemWidth.toPx() }
            // Read while drawing, so the pill follows every frame of a swipe without recomposing the bar.
            Box(
                Modifier
                    .width(itemWidth)
                    .fillMaxHeight()
                    .graphicsLayer {
                        translationX = itemPx * position()
                        scaleX = lift
                        scaleY = lift
                    }
                    .clip(NavBarDefaults.PillShape)
                    .background(MaterialTheme.colorScheme.primary),
            )
            val drag = rememberDraggableState { delta ->
                // The pill moves one tab while the pages move one page.
                val pagePx = pagerState.layoutInfo.pageSize + pagerState.layoutInfo.pageSpacing
                pagerState.dispatchRawDelta(delta * pagePx / itemPx)
            }
            Row(
                Modifier
                    .fillMaxSize()
                    .selectableGroup()
                    .draggable(
                        state = drag,
                        orientation = Orientation.Horizontal,
                        onDragStarted = { isDragging = true },
                        onDragStopped = { velocity ->
                            isDragging = false
                            val target = settledTab(position(), velocity, items.lastIndex)
                            scope.launch { pagerState.animateScrollToPage(target) }
                        },
                    ),
            ) {
                val selected = pagerState.currentPage
                items.forEachIndexed { index, item ->
                    NavBarTab(item, selected = index == selected, onClick = { onSelect(index) }, Modifier.weight(1f))
                }
            }
        }
    }
}

/** Where a let-go pill settles: the nearest tab, or the next one in the direction of a quick flick. */
internal fun settledTab(position: Float, velocity: Float, lastIndex: Int): Int {
    val tab = when {
        velocity > FLICK_VELOCITY -> ceil(position)
        velocity < -FLICK_VELOCITY -> floor(position)
        else -> position.roundToInt().toFloat()
    }
    return tab.toInt().coerceIn(0, lastIndex)
}

/** Pixels a second above which a let-go pill counts as flicked. */
private const val FLICK_VELOCITY = 600f

@Composable
private fun NavBarTab(item: NavBarItem, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val color by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Column(
        modifier = modifier
            .fillMaxHeight()
            .clip(NavBarDefaults.PillShape)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall, Alignment.CenterVertically),
    ) {
        Icon(item.icon, contentDescription = null, tint = color, modifier = Modifier.size(NavBarDefaults.IconSize))
        Text(
            text = item.label,
            color = color,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private object NavBarDefaults {
    val Height = 80.dp
    val MaxWidth = 560.dp
    val InnerPadding = 8.dp
    val IconSize = 22.dp
    val Shape = RoundedCornerShape(32.dp)
    val PillShape = RoundedCornerShape(24.dp)
    const val DragScale = 1.08f
}

private val PreviewTabs = listOf(
    NavBarItem("Trips", Icons.Default.Luggage),
    NavBarItem("Explore", Icons.Default.Explore),
    NavBarItem("Nearby", Icons.Default.NearMe),
    NavBarItem("Profile", Icons.Default.Person),
)

@Composable
private fun FloatingNavigationBarPreviewContent() {
    Column(Modifier.background(MaterialTheme.colorScheme.background)) {
        FloatingNavigationBar(PreviewTabs, rememberPagerState { PreviewTabs.size }, onSelect = {})
        FloatingNavigationBar(PreviewTabs, rememberPagerState(initialPage = 2) { PreviewTabs.size }, onSelect = {})
    }
}

@Preview
@Composable
private fun FloatingNavigationBarPreview() {
    TripinglyTheme { FloatingNavigationBarPreviewContent() }
}

@Preview
@Composable
private fun FloatingNavigationBarDarkPreview() {
    TripinglyTheme(darkTheme = true) { FloatingNavigationBarPreviewContent() }
}
