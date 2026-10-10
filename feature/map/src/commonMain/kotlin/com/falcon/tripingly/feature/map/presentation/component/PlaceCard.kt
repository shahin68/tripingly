package com.falcon.tripingly.feature.map.presentation.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
 * A place tapped on the map, in a card that floats above its pin: what the pin knows at once, opening
 * hours and website once loaded, and Add to the open day for owners and editors ([dayNumber] is null
 * for viewers).
 */
@Composable
fun PlaceCard(
    place: MapPlace,
    details: PlaceDetails?,
    /** For an address from search: shown instead of the category. */
    address: String?,
    dayNumber: Int?,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.widthIn(max = 320.dp),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Column(
            modifier = Modifier.padding(MaterialTheme.spacing.medium),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    place.category.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(24.dp),
                )
                Text(
                    text = place.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = MaterialTheme.spacing.small),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = address ?: stringResource(place.category.label()),
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
