package com.falcon.tripingly.core.network

import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.logging.LoggingFormat

/** Logs every call in full, OkHttp-style. Debug builds only: it prints tokens and personal data. */
internal fun HttpClientConfig<*>.installDebugLogging(log: (String) -> Unit) {
    install(Logging) {
        format = LoggingFormat.OkHttp
        level = LogLevel.ALL
        logger = object : Logger {
            override fun log(message: String) = log(message)
        }
    }
}
