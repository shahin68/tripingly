package com.falcon.tripingly.feature.map.presentation.component

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import coil3.Image
import coil3.toBitmap

internal actual fun Image.toPinBitmap(): ImageBitmap = toBitmap().asComposeImageBitmap()
