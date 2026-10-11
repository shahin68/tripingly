package com.falcon.tripingly.feature.map.presentation.photos

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.falcon.tripingly.core.model.photo.PickedImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal actual fun rememberPhotoPicker(onPicked: (List<PickedImage>) -> Unit): () -> Unit {
    val resolver = LocalContext.current.applicationContext.contentResolver
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        // The picker lets the app read these until the process ends, which covers the upload queue.
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
    return { launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
}
