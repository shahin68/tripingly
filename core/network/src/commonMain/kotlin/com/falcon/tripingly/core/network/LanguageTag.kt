package com.falcon.tripingly.core.network

/**
 * Language, script and region of a BCP 47 tag, without extensions or private use:
 * Android adds regional preferences such as `en-US-u-mu-celsius`, which the API's
 * locale validation rejects.
 */
fun String.baseLanguageTag(): String {
    val subtags = replace('_', '-').split('-')
    val base = subtags.takeWhile { it.length > 1 }
    return base.joinToString("-").ifEmpty { "en" }
}
