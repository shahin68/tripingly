package com.falcon.tripingly.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme
import com.falcon.tripingly.core.designsystem.theme.spacing

/** One tab of a [FloatingNavigationBar]. */
@Immutable
data class NavBarItem(val label: String, val icon: ImageVector)

/**
 * The app's bottom bar: a floating rounded glass bar whose selected tab sits in a filled accent pill. The
 * pill slides to the tab the user picks. Use it as the tab shell `Scaffold`'s `bottomBar`.
 */
@Composable
fun FloatingNavigationBar(
    items: List<NavBarItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
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
            val pillOffset by animateDpAsState(
                targetValue = itemWidth * selectedIndex,
                animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow),
            )
            Box(
                Modifier
                    .offset(x = pillOffset)
                    .width(itemWidth)
                    .fillMaxHeight()
                    .clip(NavBarDefaults.PillShape)
                    .background(MaterialTheme.colorScheme.primary),
            )
            Row(Modifier.fillMaxSize().selectableGroup()) {
                items.forEachIndexed { index, item ->
                    NavBarTab(item, selected = index == selectedIndex, onClick = { onSelect(index) }, Modifier.weight(1f))
                }
            }
        }
    }
}

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
        FloatingNavigationBar(PreviewTabs, selectedIndex = 0, onSelect = {})
        FloatingNavigationBar(PreviewTabs, selectedIndex = 2, onSelect = {})
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
