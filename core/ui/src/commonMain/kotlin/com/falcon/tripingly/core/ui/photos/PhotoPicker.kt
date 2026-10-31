package com.falcon.tripingly.core.ui.photos

import androidx.compose.runtime.Composable
import com.falcon.tripingly.core.model.photo.PickedImage

/**
 * The system's photo picker (Android Photo Picker, iOS PHPicker): no storage permission, several photos at
 * once, or one when [single] (a profile photo). Returns the function that opens it; [onPicked] gets the chosen
 * photos, not yet read.
 */
@Composable
expect fun rememberPhotoPicker(single: Boolean = false, onPicked: (List<PickedImage>) -> Unit): () -> Unit
