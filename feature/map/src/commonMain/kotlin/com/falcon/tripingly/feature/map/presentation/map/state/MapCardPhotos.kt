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
 * The open card's photos, which its photo swipes through (Shahin, 2026-11-14): a stop's photo grid (its trip's photos,
 * its place's popular photos and Commons images) or a place's gallery, ranked by the server, the same photos as its
 * Photos sheet. A place's pin cover still needs enough likes; only the card shows them all. Asked for once per stop or
 * place, the first time its card opens (an address from search has none); the photos are the repository's, so
 * uploads, deletes and likes in the sheet show on the card too.
 */
internal class MapCardPhotos(private val context: MapContext, private val photoRepository: PhotoRepository) {
    private val asked = mutableSetOf<PhotoOwner>()

    fun start() {
        context.scope.launch {
            context.model.map { it.selection?.photoOwner() }
                .distinctUntilChanged()
                .collectLatest { owner ->
                    if (owner == null) return@collectLatest
                    if (asked.add(owner)) context.scope.launch { photoRepository.loadPhotos(owner).onError { asked -= owner } }
                    photoRepository.photos(owner).filterNotNull().collect { photos ->
                        context.update { it.copy(cardPhotos = it.cardPhotos + (owner to photos)) }
                    }
                }
        }
    }

    private fun Selection.photoOwner(): PhotoOwner? = when {
        !isCardShown -> null
        this is Selection.Stop -> PhotoOwner.Stop(markerId)
        this is Selection.Place && address == null -> PhotoOwner.Place(place.id)
        else -> null
    }
}
