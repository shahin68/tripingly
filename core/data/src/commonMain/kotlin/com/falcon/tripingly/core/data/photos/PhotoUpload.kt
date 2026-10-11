package com.falcon.tripingly.core.data.photos

import com.falcon.tripingly.core.common.error.DataError
import com.falcon.tripingly.core.model.photo.Photo

/** A photo picked on this device, until the server has it (then it's a [Photo] in the gallery, processing). */
data class PhotoUpload(
    /** Local only. */
    val key: String,
    val markerId: String,
    val state: State,
) {
    sealed interface State {
        data object Queued : State

        /** 0 to 1. */
        data class Sending(val progress: Float) : State

        /** [error] is null when the picked file isn't an image the phone can read. */
        data class Failed(val error: DataError.Network?) : State
    }
}
