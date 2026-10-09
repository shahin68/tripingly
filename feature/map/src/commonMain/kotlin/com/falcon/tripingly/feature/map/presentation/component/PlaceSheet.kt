package com.falcon.tripingly.feature.map.presentation.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.falcon.tripingly.core.designsystem.component.OsmAttribution
import com.falcon.tripingly.core.designsystem.theme.spacing
import com.falcon.tripingly.core.model.place.MapPlace
import com.falcon.tripingly.core.model.place.PlaceCategory
import com.falcon.tripingly.core.model.place.PlaceDetails
import com.falcon.tripingly.feature.map.generated.resources.Res
import com.falcon.tripingly.feature.map.generated.resources.*
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * A place tapped on the map: what the pin knows at once, opening hours and website once loaded, and
 * Add to the open day for owners and editors ([dayNumber] is null for viewers).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaceSheet(
    place: MapPlace,
    details: PlaceDetails?,
    dayNumber: Int?,
    onAdd: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MaterialTheme.spacing.large)
                .padding(bottom = MaterialTheme.spacing.large),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
        ) {
            Text(text = place.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(place.category.label()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                val likeCount = details?.likeCount ?: place.likeCount
                if (likeCount > 0) {
                    Icon(
                        Icons.Default.Favorite,
                        contentDescription = stringResource(Res.string.map_place_likes),
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = likeCount.toString(),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
            }
            details?.openingHours?.let { hours ->
                Column {
                    Text(stringResource(Res.string.map_place_opening_hours), style = MaterialTheme.typography.labelMedium)
                    Text(hours, style = MaterialTheme.typography.bodyMedium)
                }
            }
            details?.website?.let { website ->
                val uriHandler = LocalUriHandler.current
                TextButton(
                    onClick = {
                        val url = if ("://" in website) website else "https://$website"
                        // OpenStreetMap's value may not be a link a browser takes.
                        runCatching { uriHandler.openUri(url) }
                    },
                ) {
                    Text(stringResource(Res.string.map_place_website))
                }
            }
            if (dayNumber != null) {
                Button(onClick = onAdd, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(Res.string.map_place_add_to_day, dayNumber))
                }
            }
            OsmAttribution()
        }
    }
}

private fun PlaceCategory.label(): StringResource = when (this) {
    PlaceCategory.Cafe -> Res.string.map_place_category_cafe
    PlaceCategory.Restaurant -> Res.string.map_place_category_restaurant
    PlaceCategory.Bar -> Res.string.map_place_category_bar
    PlaceCategory.Attraction -> Res.string.map_place_category_attraction
    PlaceCategory.Museum -> Res.string.map_place_category_museum
    PlaceCategory.Historic -> Res.string.map_place_category_historic
    PlaceCategory.Park -> Res.string.map_place_category_park
    PlaceCategory.Nature -> Res.string.map_place_category_nature
    PlaceCategory.Landmark -> Res.string.map_place_category_landmark
    PlaceCategory.Other -> Res.string.map_place_category_other
}
