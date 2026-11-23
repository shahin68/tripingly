package com.falcon.tripingly.core.data.config

import com.falcon.tripingly.core.model.config.AppConfig
import kotlinx.coroutines.flow.MutableStateFlow

/** The defaults, or [initial], for tests without the server; set [config] for settings arriving later. */
class FakeAppConfigRepository(initial: AppConfig = AppConfig()) : AppConfigRepository {
    override val config = MutableStateFlow(initial)

    override fun refreshIfLanguageChanged() = Unit
}
