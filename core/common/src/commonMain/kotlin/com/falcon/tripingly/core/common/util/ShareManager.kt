package com.falcon.tripingly.core.common.util

/** Opens the system share sheet. Callers pass text that is already localized. */
interface ShareManager {
    fun share(text: String)
}
