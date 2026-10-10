package com.falcon.tripingly.core.data.config

import com.falcon.tripingly.core.common.result.onSuccess
import com.falcon.tripingly.core.model.config.AppConfig
import com.falcon.tripingly.core.network.config.AppConfigApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

internal class DefaultAppConfigRepository(
    api: AppConfigApi,
    scope: CoroutineScope,
) : AppConfigRepository {

    private val _config = MutableStateFlow(AppConfig())
    override val config: StateFlow<AppConfig> = _config.asStateFlow()

    init {
        scope.launch {
            api.appConfig().onSuccess { _config.value = AppConfig(placesRefresh = it.placesRefreshSeconds.seconds) }
        }
    }
}

/** The defaults, for building and demoing without the server. */
class FakeAppConfigRepository : AppConfigRepository {
    override val config: StateFlow<AppConfig> = MutableStateFlow(AppConfig())
}
