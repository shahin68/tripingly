package com.falcon.tripingly.core.ui.photos

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.falcon.tripingly.core.model.photo.PickedImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
actual fun rememberPhotoPicker(single: Boolean, onPicked: (List<PickedImage>) -> Unit): () -> Unit {
    val resolver = LocalContext.current.applicationContext.contentResolver
    // The picker lets the app read these until the process ends, which covers the upload queue.
    val picked = { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            onPicked(
                uris.map { uri ->
                    PickedImage {
                        withContext(Dispatchers.IO) {
                            runCatching { resolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
                        }
                    }
                },
            )
        }
    }
    val request = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
    // Both are registered (a launcher can't be added conditionally); only one is ever used per picker.
    val one = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { picked(listOfNotNull(it)) }
    val many = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(), picked)
    return { if (single) one.launch(request) else many.launch(request) }
}
