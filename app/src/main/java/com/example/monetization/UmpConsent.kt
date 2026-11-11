package com.example.monetization

import android.app.Activity
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Google's User Messaging Platform: the consent form the GDPR asks for in the EEA and the UK (and the state-law
 * notices in the US), configured in the AdMob console under Privacy & messaging, see docs/monetization.md. Where
 * no form is required, [gather] answers at once; where one is, the player is asked on the first start and can come
 * back to it from Settings.
 */
class UmpConsent : ConsentGate {
    private val _privacyOptionsRequired = MutableStateFlow(false)
    override val privacyOptionsRequired: StateFlow<Boolean> = _privacyOptionsRequired

    override fun gather(activity: Activity, onDone: (canRequestAds: Boolean) -> Unit) {
        val info = UserMessagingPlatform.getConsentInformation(activity)
        var answered = false
        fun answer() {
            _privacyOptionsRequired.value =
                info.privacyOptionsRequirementStatus == ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
            if (!answered) {
                answered = true
                onDone(info.canRequestAds())
            }
        }
        val params = ConsentRequestParameters.Builder()
            .setTagForUnderAgeOfConsent(false)
            .build()
        info.requestConsentInfoUpdate(
            activity,
            params,
            { UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { answer() } },
            { answer() }
        )
        // Consent given on an earlier start (or none needed): the ads start now rather than after the network call.
        if (info.canRequestAds()) answer()
    }

    override fun showPrivacyOptions(activity: Activity, onDone: (canRequestAds: Boolean) -> Unit) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) {
            onDone(UserMessagingPlatform.getConsentInformation(activity).canRequestAds())
        }
    }
}
