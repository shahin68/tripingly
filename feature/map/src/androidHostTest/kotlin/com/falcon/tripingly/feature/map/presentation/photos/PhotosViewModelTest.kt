package com.falcon.tripingly.feature.map.presentation.photos

import com.falcon.tripingly.core.data.account.FakeAccountBackend
import com.falcon.tripingly.core.data.photos.FakePhotoRepository
import com.falcon.tripingly.core.model.photo.PhotoOwner
import com.falcon.tripingly.core.model.photo.PickedImage
import com.falcon.tripingly.feature.map.presentation.photos.PhotosViewModel.Action
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PhotosViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var photos: FakePhotoRepository

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        photos = FakePhotoRepository()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.viewModel(owner: PhotoOwner = PhotoOwner.Stop(MARKER_ID)) =
        PhotosViewModel(owner, photos, FakeAccountBackend(today = { LocalDate(2026, 10, 16) }))
            .also { viewModel -> backgroundScope.launch { viewModel.uiState.collect {} } }

    private fun images(count: Int) = List(count) { i -> PickedImage { byteArrayOf(i.toByte()) } }

    @Test
    fun `opening a stop without photos loads its empty gallery`() = runTest(testDispatcher) {
        val viewModel = viewModel()
        assertTrue(viewModel.uiState.value.isLoading)

        testScheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
        assertTrue(viewModel.uiState.value.photos.isEmpty())
    }

    @Test
    fun `added photos show in the gallery and open in the viewer`() = runTest(testDispatcher) {
        val viewModel = viewModel()
        testScheduler.advanceUntilIdle()

        viewModel.onAction(Action.OnAddPhotos(images(3)))
        testScheduler.advanceUntilIdle()
        val gallery = viewModel.uiState.value.photos
        assertEquals(3, gallery.size)

        viewModel.onAction(Action.OnOpenPhoto(gallery[1].id))
        testScheduler.advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.openPhotoIndex)

        viewModel.onAction(Action.OnClosePhoto)
        testScheduler.advanceUntilIdle()
        assertNull(viewModel.uiState.value.openPhotoIndex)
    }

    @Test
    fun `a photo taken with the camera goes up and the camera closes`() = runTest(testDispatcher) {
        val viewModel = viewModel()
        testScheduler.advanceUntilIdle()

        viewModel.onAction(Action.OnOpenCamera)
        testScheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isCameraOpen)

        viewModel.onAction(Action.OnAddPhotos(images(1)))
        testScheduler.advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isCameraOpen)
        assertEquals(1, viewModel.uiState.value.photos.size)
    }

    @Test
    fun `deleting asks first, and the viewer moves on to the next photo`() = runTest(testDispatcher) {
        val viewModel = viewModel()
        testScheduler.advanceUntilIdle()
        viewModel.onAction(Action.OnAddPhotos(images(3)))
        testScheduler.advanceUntilIdle()
        val gallery = viewModel.uiState.value.photos
        viewModel.onAction(Action.OnOpenPhoto(gallery[1].id))

        viewModel.onAction(Action.OnDeletePhoto(gallery[1]))
        testScheduler.advanceUntilIdle()
        assertEquals(gallery[1], viewModel.uiState.value.photoToDelete)
        assertEquals(3, viewModel.uiState.value.photos.size)

        viewModel.onAction(Action.OnConfirmDelete)
        testScheduler.advanceUntilIdle()
        val state = viewModel.uiState.value
        assertNull(state.photoToDelete)
        assertEquals(listOf(gallery[0].id, gallery[2].id), state.photos.map { it.id })
        assertEquals(gallery[2].id, state.viewable[state.openPhotoIndex!!].id)
    }

    @Test
    fun `deleting the only photo closes the viewer`() = runTest(testDispatcher) {
        val viewModel = viewModel()
        testScheduler.advanceUntilIdle()
        viewModel.onAction(Action.OnAddPhotos(images(1)))
        testScheduler.advanceUntilIdle()
        val photo = viewModel.uiState.value.photos.single()
        viewModel.onAction(Action.OnOpenPhoto(photo.id))

        viewModel.onAction(Action.OnDeletePhoto(photo))
        viewModel.onAction(Action.OnConfirmDelete)
        testScheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.photos.isEmpty())
        assertNull(viewModel.uiState.value.openPhotoIndex)
    }

    @Test
    fun `anyone adds photos to a place and likes them`() = runTest(testDispatcher) {
        val viewModel = viewModel(PhotoOwner.Place(PLACE_ID))
        testScheduler.advanceUntilIdle()

        viewModel.onAction(Action.OnAddPhotos(images(1)))
        testScheduler.advanceUntilIdle()
        val photo = viewModel.uiState.value.photos.single()
        assertEquals(PhotoOwner.Place(PLACE_ID), photo.owner)

        viewModel.onAction(Action.OnToggleLike(photo))
        testScheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value.photos.single().likedByMe)
        assertEquals(1, viewModel.uiState.value.photos.single().likeCount)

        viewModel.onAction(Action.OnToggleLike(photo))
        testScheduler.advanceUntilIdle()
        assertFalse(viewModel.uiState.value.photos.single().likedByMe)
        assertEquals(0, viewModel.uiState.value.photos.single().likeCount)
    }

    private companion object {
        const val MARKER_ID = "marker-1"
        const val PLACE_ID = "place-1"
    }
}
