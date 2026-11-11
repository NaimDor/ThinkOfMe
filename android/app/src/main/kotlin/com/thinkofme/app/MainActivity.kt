package com.thinkofme.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.thinkofme.app.ads.AdsConsent
import com.thinkofme.core.designsystem.theme.ThinkOfMeTheme
import com.thinkofme.core.ui.ads.AdsConfig
import com.thinkofme.core.ui.ads.LocalAdsConfig
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val incoming = Channel<IncomingIntent>(Channel.BUFFERED)
    private lateinit var adsConsent: AdsConsent

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        adsConsent = AdsConsent(this).also { it.gather() }
        // The Google web client id is generated from google-services.json; absent in demo builds.
        val webClientId = resources.getIdentifier("default_web_client_id", "string", packageName)
            .takeIf { it != 0 }?.let(::getString)

        if (savedInstanceState == null) handle(intent)

        setContent {
            val canShowAds by adsConsent.canShowAds.collectAsStateWithLifecycle()
            val privacyRequired by adsConsent.privacyOptionsRequired.collectAsStateWithLifecycle()
            val ads = AdsConfig(
                enabled = canShowAds,
                nativeUnitId = BuildConfig.AD_UNIT_NATIVE,
                bannerUnitId = BuildConfig.AD_UNIT_BANNER,
            )
            ThinkOfMeTheme {
                CompositionLocalProvider(LocalAdsConfig provides ads) {
                    ThinkOfMeApp(
                        incoming = incoming.receiveAsFlow(),
                        onIncomingHandled = { },
                        webClientId = webClientId,
                        onPrivacyOptions = if (privacyRequired) adsConsent::showPrivacyOptions else null,
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)?.let { incoming.trySend(IncomingIntent.SharedText(it)) }
            Intent.ACTION_VIEW -> {
                val data = intent.data ?: return
                if (data.host == "thinkofme.app" && data.pathSegments.firstOrNull() == "i") {
                    data.pathSegments.getOrNull(1)?.let { incoming.trySend(IncomingIntent.Invite(it)) }
                }
            }
        }
    }
}
