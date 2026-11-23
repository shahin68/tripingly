package com.falcon.tripingly.core.data.config

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.falcon.tripingly.core.common.result.onSuccess
import com.falcon.tripingly.core.model.config.AppConfig
import com.falcon.tripingly.core.model.config.ConfigItem
import com.falcon.tripingly.core.model.config.TransitArea
import com.falcon.tripingly.core.model.place.PlaceCategory
import com.falcon.tripingly.core.model.place.PlaceKind
import com.falcon.tripingly.core.model.place.PlaceKinds
import com.falcon.tripingly.core.network.AppLanguage
import com.falcon.tripingly.core.network.NetworkJson
import com.falcon.tripingly.core.network.config.AppConfigApi
import com.falcon.tripingly.core.network.model.AppConfigDto
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The server's tuning values and lists. The ones it sent last time come first (DataStore, as its JSON), so a start
 * offline or before it answers already has them (Shahin, 2026-11-22); then the fresh ones, saved for the next start.
 * Saved per language, since the lists' labels are in it: switching back to a language shows its labels at once, a new
 * one keeps the old labels until the server answers. Defaults only before the first answer ever.
 */
internal class DefaultAppConfigRepository(
    private val api: AppConfigApi,
    private val store: DataStore<Preferences>,
    private val language: AppLanguage,
    private val scope: CoroutineScope,
) : AppConfigRepository {

    private val _config = MutableStateFlow(AppConfig())
    override val config: StateFlow<AppConfig> = _config.asStateFlow()

    private val lock = Mutex()

    /** The language the server last answered in; null until it answers, so a failed read is tried again. */
    private var answeredIn: String? = null

    init {
        scope.launch { load(language.tag()) }
    }

    override fun refreshIfLanguageChanged() {
        val tag = language.tag()
        if (tag != answeredIn) scope.launch { load(tag) }
    }

    private suspend fun load(tag: String) = lock.withLock {
        if (tag == answeredIn) return@withLock
        val saved = savedKey(tag)
        store.data.catch { emit(emptyPreferences()) }.first()[saved]?.let(::decode)?.let { _config.value = it.toAppConfig() }
        api.appConfig().onSuccess { dto ->
            answeredIn = tag
            _config.value = dto.toAppConfig()
            try {
                store.edit { it[saved] = NetworkJson.encodeToString(AppConfigDto.serializer(), dto) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Not saved (disk full): the next start asks again.
            }
        }
    }

    // A SerializationException is an IllegalArgumentException: a config saved by an older app is skipped.
    private fun decode(json: String): AppConfigDto? = try {
        NetworkJson.decodeFromString(AppConfigDto.serializer(), json)
    } catch (_: IllegalArgumentException) {
        null
    }

    private fun AppConfigDto.toAppConfig() = AppConfig(
        placesRefresh = placesRefreshSeconds.seconds,
        photoMaxEdgePx = photoMaxEdgePx.toInt(),
        photoJpegQuality = photoJpegQuality.toInt(),
        photoParallelUploads = photoParallelUploads.toInt(),
        pinLargeMinProminence = pinLargeMinProminence.toInt(),
        pinMediumMinProminence = pinMediumMinProminence.toInt(),
        mapMaxPins = mapMaxPins.toInt(),
        photoPinMinProminence = photoPinMinProminence.toInt(),
        photoPinMinHotCount = photoPinMinHotCount.toInt(),
        mapMaxPhotoPins = mapMaxPhotoPins.toInt(),
        pinDotBelowZoom = pinDotBelowZoom.toFloat(),
        // Optional, so a server from before the switch still sends a config the app reads.
        osmPinsEnabled = osmPinsEnabled ?: true,
        transitAreas = transitAreas.map { area -> TransitArea(area.id, area.name, area.south, area.west, area.north, area.east) },
        placeKinds = PlaceKinds(placeKinds.map { PlaceKind(PlaceCategory(it.key), it.label, it.icon, it.forSpots, it.forFilters) }),
        explorePeriods = explorePeriods.map { ConfigItem(it.key, it.label, it.icon, it.isDefault) },
        reportReasons = reportReasons.map { ConfigItem(it.key, it.label) },
    )

    private companion object {
        fun savedKey(language: String) = stringPreferencesKey("appConfig.$language")
    }
}
