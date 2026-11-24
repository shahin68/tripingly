package com.falcon.tripingly.core.data.tips

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** A tip card the app shows until it is dismissed, once per account on this device. */
enum class Tip {
    /** The Search tab's "Travel with friends" card: people can be searched there too (Shahin, 2026-11-24). */
    SearchPeople,
}

/** Which tips were dismissed, on the device. */
interface TipRepository {
    fun isDismissed(tip: Tip): Flow<Boolean>
    suspend fun dismiss(tip: Tip)
}

/**
 * One value per tip in the UI state DataStore, under `tip:<name>:dismissed`. The store is wiped as a whole when the
 * session ends ([com.falcon.tripingly.core.data.trips.TripMapViewRepository.clear]), so the next account sees the tips again.
 */
internal class DataStoreTipRepository(private val store: DataStore<Preferences>) : TipRepository {

    override fun isDismissed(tip: Tip): Flow<Boolean> = store.data.map { it[key(tip)] ?: false }.distinctUntilChanged()

    override suspend fun dismiss(tip: Tip) {
        store.edit { it[key(tip)] = true }
    }

    private fun key(tip: Tip) = booleanPreferencesKey("tip:${tip.name}:dismissed")
}
