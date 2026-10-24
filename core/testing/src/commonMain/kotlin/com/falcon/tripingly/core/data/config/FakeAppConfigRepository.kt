package com.falcon.tripingly.core.data.config

import com.falcon.tripingly.core.model.config.AppConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** The defaults, for tests without the server. */
class FakeAppConfigRepository : AppConfigRepository {
    override val config: StateFlow<AppConfig> = MutableStateFlow(AppConfig())
}
