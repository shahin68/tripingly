package com.falcon.tripingly.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.falcon.tripingly.core.designsystem.generated.resources.Res
import com.falcon.tripingly.core.designsystem.generated.resources.common_back
import com.falcon.tripingly.core.designsystem.generated.resources.common_settings
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme
import com.falcon.tripingly.core.designsystem.theme.spacing
import org.jetbrains.compose.resources.stringResource

/**
 * The floating glass header of a screen: a large [title] with [actions] on the right (usually [SettingsAction]),
 * an optional [navigationIcon] (usually [BackAction]) and an optional [search] field below. Use it as a
 * `Scaffold`'s `topBar` and draw the content behind it with the scaffold's padding as content padding.
 */
@Composable
fun FloatingTopBar(
    title: String,
    modifier: Modifier = Modifier,
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    search: (@Composable () -> Unit)? = null,
) {
    GlassSurface(
        modifier = modifier
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            .padding(horizontal = MaterialTheme.spacing.medium, vertical = MaterialTheme.spacing.small)
            .fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(
            modifier = Modifier.padding(TopBarDefaults.InnerPadding),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
        ) {
            Row(
                modifier = Modifier.heightIn(min = TopBarDefaults.RowHeight),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (navigationIcon != null) navigationIcon() else Spacer(Modifier.width(MaterialTheme.spacing.small))
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                actions()
            }
            search?.invoke()
        }
    }
}

/** The top bar's Settings button: a gear that opens Settings straight away. */
@Composable
fun SettingsAction(onClick: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(onClick = onClick, modifier = modifier) {
        Icon(Icons.Outlined.Settings, contentDescription = stringResource(Res.string.common_settings))
    }
}

/** The top bar's back button. */
@Composable
fun BackAction(onClick: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(onClick = onClick, modifier = modifier) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.common_back))
    }
}

private object TopBarDefaults {
    val InnerPadding = 8.dp
    val RowHeight = 48.dp
}

@Composable
private fun FloatingTopBarPreviewContent() {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column {
            FloatingTopBar(title = "My Trips", actions = { SettingsAction({}) }, search = {
                TopBarSearchField(query = "", onQueryChange = {})
            })
            FloatingTopBar(title = "Nearby", actions = { SettingsAction({}) })
            FloatingTopBar(title = "@anna", navigationIcon = { BackAction({}) })
            Spacer(Modifier.height(MaterialTheme.spacing.medium))
        }
    }
}

@Preview
@Composable
private fun FloatingTopBarPreview() {
    TripinglyTheme { FloatingTopBarPreviewContent() }
}

@Preview
@Composable
private fun FloatingTopBarDarkPreview() {
    TripinglyTheme(darkTheme = true) { FloatingTopBarPreviewContent() }
}
