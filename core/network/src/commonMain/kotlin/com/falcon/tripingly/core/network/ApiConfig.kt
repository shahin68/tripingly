package com.falcon.tripingly.core.network

/** Where and how the app talks to the Tripinly API. */
data class ApiConfig(
    /** Base URL including `/v1`, without a trailing slash. */
    val baseUrl: String,
    val environment: String,
    /** Sent as `X-Client`, e.g. `android/1.0`. */
    val client: String,
    /** How much of each call is logged; secrets and personal data are always hidden. */
    val httpLogLevel: HttpLogLevel,
    /** Repositories bind their fakes instead of the API (never in production). */
    val useFakeApi: Boolean = false,
    /** The backend's developer sign-in (`POST /auth/dev`); never in production. */
    val developerSignIn: Boolean = false,
    /** Staging's `X-Dev-Auth-Secret`, from a local Gradle property; never committed. */
    val devAuthSecret: String? = null,
    /** The backend's Google Web client ID, the audience of Google ID tokens; null until it exists. */
    val googleWebClientId: String? = null,
) {
    init {
        require(baseUrl.isNotBlank()) {
            "No API base URL for the $environment environment (see core/network/build.gradle.kts)"
        }
    }

    companion object {
        fun fromBuildConfig(platform: String): ApiConfig = ApiConfig(
            baseUrl = NetworkBuildConfig.BASE_URL.trimEnd('/'),
            environment = NetworkBuildConfig.ENVIRONMENT,
            client = "$platform/${NetworkBuildConfig.APP_VERSION}",
            httpLogLevel = HttpLogLevel.valueOf(NetworkBuildConfig.HTTP_LOG_LEVEL),
            useFakeApi = NetworkBuildConfig.USE_FAKE_API,
            developerSignIn = NetworkBuildConfig.DEVELOPER_SIGN_IN,
            devAuthSecret = NetworkBuildConfig.DEV_AUTH_SECRET.ifBlank { null },
            googleWebClientId = NetworkBuildConfig.GOOGLE_WEB_CLIENT_ID.ifBlank { null },
        )
    }
}
