package com.falcon.tripingly.core.network

import kotlinx.serialization.json.Json

/** JSON settings shared by the client and tests: tolerant of new fields, compact on the wire. */
val NetworkJson: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    coerceInputValues = true
}
