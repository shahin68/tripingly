package com.falcon.tripingly.core.data.account

import kotlin.coroutines.cancellation.CancellationException

/**
 * Wipes one kind of data the app keeps for the signed-in user. Every
 * implementation bound in Koin runs whenever the session ends (sign-out,
 * expired or refused session, deleted account), so nothing of one account
 * survives into the next. Tokens are cleared by the session itself.
 */
fun interface LocalDataCleaner {
    suspend fun clear()
}

/** Runs every cleaner; one failing must not keep the others from running. */
internal suspend fun List<LocalDataCleaner>.clearAll() {
    forEach { cleaner ->
        try {
            cleaner.clear()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Best effort per store; the session ends either way.
        }
    }
}
