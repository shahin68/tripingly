package com.falcon.tripingly.feature.map.presentation.component

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import coil3.Image
import coil3.toBitmap

// Pins are drawn on a software canvas, which can't read hardware bitmaps.
internal actual fun Image.toPinBitmap(): ImageBitmap =
    toBitmap().let { if (it.config == Bitmap.Config.HARDWARE) it.copy(Bitmap.Config.ARGB_8888, false) else it }.asImageBitmap()
