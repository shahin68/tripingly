package com.falcon.tripingly.core.data.config

import com.falcon.tripingly.core.model.config.AppConfig
import kotlinx.coroutines.flow.StateFlow

/**
 * Settings the server sends to steer the app (see [AppConfig]): read once per launch, the defaults
 * until they arrive and for the whole launch when they can't be read. A value that tunes the app's
 * behaviour (a cache or refresh time, a limit) belongs here, not in code.
 */
interface AppConfigRepository {
    val config: StateFlow<AppConfig>
}
