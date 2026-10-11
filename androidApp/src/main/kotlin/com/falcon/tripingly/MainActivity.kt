package com.falcon.tripingly

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.falcon.tripingly.deeplink.DeepLink
import com.falcon.tripingly.deeplink.DeepLinks
import com.falcon.tripingly.push.TripinglyMessagingService
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {
    private val deepLinks: DeepLinks by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Not again after a rotation or process restore.
        if (savedInstanceState == null) openDeepLink(intent)

        setContent {
            App()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        openDeepLink(intent)
    }

    /** A tapped push carries its data as extras, whether the app or the system showed it. */
    private fun openDeepLink(intent: Intent) {
        val link = intent.getStringExtra(TripinglyMessagingService.DEEP_LINK) ?: return
        deepLinks.open(DeepLink.parse(link, intent.getStringExtra(TripinglyMessagingService.NOTIFICATION_ID)))
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}
