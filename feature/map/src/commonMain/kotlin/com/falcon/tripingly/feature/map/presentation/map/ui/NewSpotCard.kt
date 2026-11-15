package com.falcon.tripingly.feature.map.presentation.map.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.maxLength
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.AddAPhoto
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.falcon.tripingly.core.designsystem.component.AppTextField
import com.falcon.tripingly.core.designsystem.component.PillChip
import com.falcon.tripingly.core.designsystem.component.PrimaryButton
import com.falcon.tripingly.core.designsystem.component.horizontalFadingEdge
import com.falcon.tripingly.core.designsystem.component.icon
import com.falcon.tripingly.core.designsystem.component.rememberHaptics
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme
import com.falcon.tripingly.core.designsystem.theme.spacing
import com.falcon.tripingly.core.location.model.Coordinates
import com.falcon.tripingly.core.model.photo.PickedImage
import com.falcon.tripingly.core.model.place.PlaceCategory
import com.falcon.tripingly.core.ui.photos.rememberPhotoPicker
import com.falcon.tripingly.feature.map.generated.resources.Res
import com.falcon.tripingly.feature.map.generated.resources.map_new_spot_add_photo
import com.falcon.tripingly.feature.map.generated.resources.map_new_spot_create
import com.falcon.tripingly.feature.map.generated.resources.map_new_spot_name
import com.falcon.tripingly.feature.map.generated.resources.map_new_spot_remove_photo
import com.falcon.tripingly.feature.map.presentation.component.SelectedPinSize
import com.falcon.tripingly.feature.map.presentation.map.NewSpotCardState
import org.jetbrains.compose.resources.stringResource

/**
 * The card over a long-pressed point (design: layout C of the place categories canvas, Shahin 2026-11-14): the spot's
 * name with a photo button beside it, one sideways row of every kind of place (Spot first, picked until another is),
 * and Create, which makes the place and adds it as a stop on the open day. Rows are counted from the pin for
 * [PinCardScope.bloomRow]: the button is nearest.
 */
@Composable
internal fun PinCardScope.NewSpotCard(
    card: NewSpotCardState,
    onPickCategory: (PlaceCategory) -> Unit,
    onPhoto: (PickedImage?) -> Unit,
    onCreate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberHaptics()
    // The long press that dropped the pin is felt as its card opens.
    LaunchedEffect(card.location) { haptics.longPress() }
    val pickPhoto = rememberPhotoPicker(single = true) { images -> images.firstOrNull()?.let(onPhoto) }
    val canCreate = card.name.text.isNotBlank()
    val create = {
        if (canCreate) {
            haptics.success()
            onCreate()
        }
    }
    Column(
        modifier.widthIn(max = MapCardMaxWidth).padding(vertical = MaterialTheme.spacing.small),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
    ) {
        Row(
            Modifier.padding(horizontal = MaterialTheme.spacing.small).bloomRow(fromPin = 2),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppTextField(
                state = card.name,
                placeholder = stringResource(Res.string.map_new_spot_name),
                modifier = Modifier.weight(1f),
                onImeAction = create,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                inputTransformation = InputTransformation.maxLength(MAX_SPOT_NAME_LENGTH),
            )
            SpotPhotoButton(card.photo, onAdd = pickPhoto, onRemove = { onPhoto(null) })
        }
        CategoryRow(
            selected = card.category,
            onPick = { category ->
                haptics.tick()
                onPickCategory(category)
            },
            modifier = Modifier.bloomRow(fromPin = 1),
        )
        PrimaryButton(
            text = stringResource(Res.string.map_new_spot_create),
            onClick = create,
            enabled = canCreate,
            modifier = Modifier.fillMaxWidth().padding(horizontal = MaterialTheme.spacing.small).bloomRow(fromPin = 0),
        )
    }
}

/** Every kind of place in one sideways row; it scrolls out to the card's edges and starts in line with the rows above. */
@Composable
private fun CategoryRow(selected: PlaceCategory, onPick: (PlaceCategory) -> Unit, modifier: Modifier = Modifier) {
    val state = rememberLazyListState()
    LazyRow(
        state = state,
        modifier = modifier.fillMaxWidth().horizontalFadingEdge(state),
        contentPadding = PaddingValues(horizontal = MaterialTheme.spacing.small),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
    ) {
        items(SpotCategories, key = { it.name }) { category ->
            PillChip(
                label = stringResource(category.label()),
                onClick = { onPick(category) },
                selected = category == selected,
                leading = { Icon(category.icon, contentDescription = null, modifier = Modifier.size(ChipIconSize)) },
                modifier = Modifier.animateItem(),
            )
        }
    }
}

/**
 * The photo beside the name, as high as the field: a camera to pick one, or the picked photo, which a tap takes off
 * again. One glides into the other.
 */
@Composable
private fun SpotPhotoButton(photo: PickedImage?, onAdd: () -> Unit, onRemove: () -> Unit) {
    AnimatedContent(
        targetState = photo,
        transitionSpec = { (fadeIn() + scaleIn(initialScale = 0.8f)) togetherWith (fadeOut() + scaleOut(targetScale = 0.8f)) },
        contentAlignment = Alignment.Center,
    ) { shown ->
        if (shown == null) {
            Surface(onClick = onAdd, shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.size(PhotoButtonSize)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.AddAPhoto, contentDescription = stringResource(Res.string.map_new_spot_add_photo))
                }
            }
        } else {
            // Read once from the phone; Coil scales it down to the button.
            val bytes by produceState<ByteArray?>(null, shown) { value = shown.bytes() }
            Box(
                Modifier
                    .size(PhotoButtonSize)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .clickable(onClick = onRemove),
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(model = bytes, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                Box(Modifier.size(RemoveBadgeSize).clip(CircleShape).background(PhotoScrim), contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = stringResource(Res.string.map_new_spot_remove_photo),
                        tint = Color.White,
                        modifier = Modifier.size(ChipIconSize),
                    )
                }
            }
        }
    }
}

/**
 * Spot first, the default, then our own Photo spot and Romantic spot; then food and drink, sights, nature, things to do,
 * shopping, staying and getting around, and the practical ones last.
 */
private val SpotCategories = listOf(
    PlaceCategory.Other,
    PlaceCategory.PhotoSpot,
    PlaceCategory.Romantic,
    PlaceCategory.Cafe,
    PlaceCategory.Restaurant,
    PlaceCategory.Bar,
    PlaceCategory.Bakery,
    PlaceCategory.FastFood,
    PlaceCategory.Nightlife,
    PlaceCategory.Winery,
    PlaceCategory.Attraction,
    PlaceCategory.Viewpoint,
    PlaceCategory.Museum,
    PlaceCategory.Historic,
    PlaceCategory.Landmark,
    PlaceCategory.Zoo,
    PlaceCategory.Park,
    PlaceCategory.Nature,
    PlaceCategory.Beach,
    PlaceCategory.Entertainment,
    PlaceCategory.Cinema,
    PlaceCategory.Sports,
    PlaceCategory.Spa,
    PlaceCategory.Kids,
    PlaceCategory.Library,
    PlaceCategory.Shopping,
    PlaceCategory.Market,
    PlaceCategory.Groceries,
    PlaceCategory.Stay,
    PlaceCategory.Camping,
    PlaceCategory.Transport,
    PlaceCategory.Rental,
    PlaceCategory.Fuel,
    PlaceCategory.Parking,
    PlaceCategory.Money,
    PlaceCategory.Health,
    PlaceCategory.Pharmacy,
    PlaceCategory.Toilets,
)

/** The server's limit for a stop's name. */
private const val MAX_SPOT_NAME_LENGTH = 120
private val PhotoButtonSize = 56.dp
private val RemoveBadgeSize = 28.dp
private val ChipIconSize = 18.dp
private val PhotoScrim = Color.Black.copy(alpha = 0.45f)

@Preview(widthDp = 360, heightDp = 640)
@Composable
private fun NewSpotCardPreview() {
    TripinglyTheme { NewSpotCardPreviewContent() }
}

@Preview(widthDp = 360, heightDp = 640)
@Composable
private fun NewSpotCardDarkPreview() {
    TripinglyTheme(darkTheme = true) { NewSpotCardPreviewContent() }
}

@Preview(widthDp = 800, heightDp = 360)
@Composable
private fun NewSpotCardLandscapePreview() {
    TripinglyTheme(darkTheme = true) { NewSpotCardPreviewContent() }
}

@Composable
private fun NewSpotCardPreviewContent() {
    val card = NewSpotCardState(Coordinates(48.1915, 16.3809), rememberTextFieldState("Rooftop view"), PlaceCategory.Viewpoint, photo = null)
    PinCard(card = card, key = { it.location }, pinClearance = SelectedPinSize / 2) { shown ->
        NewSpotCard(shown, onPickCategory = {}, onPhoto = {}, onCreate = {})
    }
}
