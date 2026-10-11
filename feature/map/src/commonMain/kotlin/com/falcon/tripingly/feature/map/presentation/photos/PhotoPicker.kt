package com.falcon.tripingly.feature.map.presentation.photos

import androidx.compose.runtime.Composable
import com.falcon.tripingly.core.model.photo.PickedImage

/**
 * The system's photo picker (Android Photo Picker, iOS PHPicker): no storage permission, several photos at
 * once. Returns the function that opens it; [onPicked] gets the chosen photos, not yet read.
 */
@Composable
internal expect fun rememberPhotoPicker(onPicked: (List<PickedImage>) -> Unit): () -> Unit
