package com.thinkofme.app.ads

import android.app.Activity
import com.google.android.gms.ads.MobileAds
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Gathers ad consent with Google's User Messaging Platform (GDPR / US state
 * privacy) and only then initializes the Mobile Ads SDK. If the user declines
 * personalization, the SDK serves non-personalized ads automatically.
 */
class AdsConsent(private val activity: Activity) {
    private val consent: ConsentInformation = UserMessagingPlatform.getConsentInformation(activity)
    private val initialized = AtomicBoolean(false)

    private val _canShowAds = MutableStateFlow(false)
    val canShowAds: StateFlow<Boolean> = _canShowAds.asStateFlow()

    private val _privacyOptionsRequired = MutableStateFlow(false)
    val privacyOptionsRequired: StateFlow<Boolean> = _privacyOptionsRequired.asStateFlow()

    fun gather() {
        val params = ConsentRequestParameters.Builder().build()
        consent.requestConsentInfoUpdate(
            activity,
            params,
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) {
                    refresh()
                }
            },
            { refresh() },
        )
        // Consent from a previous session lets ads start immediately.
        refresh()
    }

    fun showPrivacyOptions() {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { refresh() }
    }

    private fun refresh() {
        _privacyOptionsRequired.value = consent.privacyOptionsRequirementStatus == ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
        if (consent.canRequestAds() && initialized.compareAndSet(false, true)) {
            MobileAds.initialize(activity) { _canShowAds.value = true }
        }
    }
}
