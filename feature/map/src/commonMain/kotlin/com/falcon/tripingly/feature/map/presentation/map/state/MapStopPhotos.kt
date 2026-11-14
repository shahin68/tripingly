package com.falcon.tripingly.feature.map.presentation.map.state

import com.falcon.tripingly.core.common.result.onError
import com.falcon.tripingly.core.data.photos.PhotoRepository
import com.falcon.tripingly.core.model.photo.PhotoOwner
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * The open stop's photo grid, which its card's photo swipes through (Shahin, 2026-11-14): its trip's photos, its
 * place's popular photos and Commons images, ranked by the server, the same grid as its Photos sheet and details.
 * Asked for once per stop, the first time its card opens; the grid is the repository's, so uploads, deletes and likes
 * in the sheet show on the card too.
 */
internal class MapStopPhotos(private val context: MapContext, private val photoRepository: PhotoRepository) {
    private val asked = mutableSetOf<String>()

    fun start() {
        context.scope.launch {
            context.model.map { (it.selection as? Selection.Stop)?.takeIf { stop -> stop.isCardShown }?.markerId }
                .distinctUntilChanged()
                .collectLatest { markerId ->
                    if (markerId == null) return@collectLatest
                    val owner = PhotoOwner.Stop(markerId)
                    if (asked.add(markerId)) context.scope.launch { photoRepository.loadPhotos(owner).onError { asked -= markerId } }
                    photoRepository.photos(owner).filterNotNull().collect { photos ->
                        context.update { it.copy(stopPhotos = it.stopPhotos + (markerId to photos)) }
                    }
                }
        }
    }
}
