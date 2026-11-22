package com.falcon.tripingly.core.data.config

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.falcon.tripingly.core.common.result.onSuccess
import com.falcon.tripingly.core.model.config.AppConfig
import com.falcon.tripingly.core.model.config.TransitArea
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

/**
 * The server's tuning values. The ones it sent last time come first (DataStore, as its JSON), so a start offline or
 * before it answers already has them (Shahin, 2026-11-22); then the fresh ones, saved for the next start. Defaults only
 * before the first answer ever.
 */
internal class DefaultAppConfigRepository(
    api: AppConfigApi,
    store: DataStore<Preferences>,
    scope: CoroutineScope,
) : AppConfigRepository {

    private val _config = MutableStateFlow(AppConfig())
    override val config: StateFlow<AppConfig> = _config.asStateFlow()

    init {
        scope.launch {
            store.data.catch { emit(emptyPreferences()) }.first()[SAVED]?.let(::decode)?.let { _config.value = it.toAppConfig() }
            api.appConfig().onSuccess { dto ->
                _config.value = dto.toAppConfig()
                try {
                    store.edit { it[SAVED] = NetworkJson.encodeToString(AppConfigDto.serializer(), dto) }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // Not saved (disk full): the next start asks again.
                }
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
    )

    private companion object {
        val SAVED = stringPreferencesKey("appConfig")
    }
}
