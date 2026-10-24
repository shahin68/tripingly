package com.falcon.tripingly.core.designsystem.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import com.falcon.tripingly.core.designsystem.generated.resources.Res
import com.falcon.tripingly.core.designsystem.generated.resources.osm_attribution
import androidx.compose.foundation.background
import androidx.compose.ui.tooling.preview.Preview
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme

/**
 * "© OpenStreetMap contributors": required wherever our places, search results or routes show.
 * Opens OpenStreetMap's copyright page.
 */
@Composable
fun OsmAttribution(modifier: Modifier = Modifier) {
    val uriHandler = LocalUriHandler.current
    Text(
        text = stringResource(Res.string.osm_attribution),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .clickable { runCatching { uriHandler.openUri(COPYRIGHT_URL) } }
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

private const val COPYRIGHT_URL = "https://www.openstreetmap.org/copyright"

@Preview
@Composable
private fun OsmAttributionPreview() {
    TripinglyTheme { OsmAttribution(Modifier.background(MaterialTheme.colorScheme.surface)) }
}

@Preview
@Composable
private fun OsmAttributionDarkPreview() {
    TripinglyTheme(darkTheme = true) { OsmAttribution(Modifier.background(MaterialTheme.colorScheme.surface)) }
}
