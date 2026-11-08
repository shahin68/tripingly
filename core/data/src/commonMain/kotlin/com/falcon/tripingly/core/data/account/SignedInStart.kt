package com.falcon.tripingly.core.data.account

/**
 * Work that starts once someone is signed in (push, purchases, the notification center, the map's warm-up). Every
 * implementation bound in Koin starts when the signed-in app first shows, so the shell needn't know what each part
 * of the app starts.
 */
fun interface SignedInStart {
    fun start()
}
