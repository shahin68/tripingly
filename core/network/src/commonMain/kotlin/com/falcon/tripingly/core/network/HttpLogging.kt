package com.falcon.tripingly.core.network

import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.logging.LoggingFormat
import io.ktor.http.HttpHeaders

/**
 * How much of each API call is logged, like OkHttp's logging levels. Set
 * `tripinly.httpLogLevel` in `local.properties`; production builds never log.
 * Every level hides secrets and personal data, see [redactForLog].
 */
enum class HttpLogLevel {
    NONE,

    /** Method, URL, status and duration. */
    BASIC,

    /** [BASIC] plus request and response headers. */
    HEADERS,

    /** [HEADERS] plus request and response bodies. */
    BODY,
}

internal fun HttpClientConfig<*>.installLogging(level: HttpLogLevel, log: (String) -> Unit) {
    if (level == HttpLogLevel.NONE) return
    install(Logging) {
        format = LoggingFormat.OkHttp
        this.level = when (level) {
            HttpLogLevel.NONE -> LogLevel.NONE
            HttpLogLevel.BASIC -> LogLevel.INFO
            HttpLogLevel.HEADERS -> LogLevel.HEADERS
            HttpLogLevel.BODY -> LogLevel.BODY
        }
        logger = object : Logger {
            override fun log(message: String) = log(redactForLog(message))
        }
        sanitizeHeader { name -> SECRET_HEADERS.any { it.equals(name, ignoreCase = true) } }
    }
}

private val SECRET_HEADERS = listOf(
    HttpHeaders.Authorization,
    HttpHeaders.Cookie,
    HttpHeaders.SetCookie,
    "X-Dev-Auth-Secret",
)

internal const val REDACTED = "██"

/** JSON fields holding tokens, secrets, personal data, locations or signed URLs. */
private val secretJsonField = Regex(
    """"((?:access|refresh|id|identity)Token|token|secret|authorizationCode|email|birthDate|lat|lng|latitude|longitude|url|[A-Za-z]+Url)"\s*:\s*("(?:[^"\\]|\\.)*"|-?[0-9][0-9.eE+-]*)""",
)

/** Query parameters that carry a location or a map area. */
private val locationQueryParam = Regex("""([?&](?:lat|lng|lon|latitude|longitude|bbox|(?:min|max)(?:Lat|Lng|Lon)[A-Za-z]*)=)[^&\s]*""")

/** An invite's token in its URL lets anyone join the trip. */
private val inviteToken = Regex("""(/invites/)[^/\s?#]+""")

/**
 * Hides what the app must never log (client rule 5): tokens and secrets,
 * emails, birth dates, precise locations and signed URLs.
 */
fun redactForLog(text: String): String = text
    .replace(secretJsonField) { """"${it.groupValues[1]}":"$REDACTED"""" }
    .replace(locationQueryParam) { it.groupValues[1] + REDACTED }
    .replace(inviteToken) { it.groupValues[1] + REDACTED }
