package com.falcon.tripingly.core.storage

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import okio.Path.Companion.toPath

/**
 * Small, non-secret values that bring a screen back as the person left it (DataStore preferences), such as
 * where a trip's map was. Never tokens: those go in [SecureStore]. One instance per file, so it is a Koin single.
 */
internal fun createUiStateStore(path: String): DataStore<Preferences> =
    PreferenceDataStoreFactory.createWithPath(produceFile = { path.toPath() })

internal const val UI_STATE_FILE = "ui_state.preferences_pb"
