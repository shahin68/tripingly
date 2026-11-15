package com.falcon.tripingly.core.data.config

import com.falcon.tripingly.core.model.config.AppConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** The defaults, or [initial], for tests without the server. */
class FakeAppConfigRepository(initial: AppConfig = AppConfig()) : AppConfigRepository {
    override val config: StateFlow<AppConfig> = MutableStateFlow(initial)
}
