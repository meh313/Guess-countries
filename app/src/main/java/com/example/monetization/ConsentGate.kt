package com.example.monetization

import android.app.Activity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The consent the law asks for before ads are requested (the GDPR in the EEA and the UK, state laws in the US): a
 * form on the first start where one is required, and a way back to it from Settings. The app uses [UmpConsent],
 * Google's User Messaging Platform; where no ads will ever show, [NONE].
 */
interface ConsentGate {
    /** True where the player is entitled to change their choice later: Settings then shows "Ad privacy options". */
    val privacyOptionsRequired: StateFlow<Boolean>

    /**
     * Asks for consent where it is required, showing the form over [activity], and runs [onDone] once with whether ads
     * may be requested. Runs [onDone] at once when consent was given on an earlier start, so the ads do not wait for
     * the network.
     */
    fun gather(activity: Activity, onDone: (canRequestAds: Boolean) -> Unit)

    /** Shows the privacy options form over [activity]; [onDone] gets whether ads may still be requested. */
    fun showPrivacyOptions(activity: Activity, onDone: (canRequestAds: Boolean) -> Unit)

    /** No consent needed: nothing to ask, and ads may be requested (of which there are none). */
    object NONE : ConsentGate {
        override val privacyOptionsRequired: StateFlow<Boolean> = MutableStateFlow(false)
        override fun gather(activity: Activity, onDone: (canRequestAds: Boolean) -> Unit) = onDone(true)
        override fun showPrivacyOptions(activity: Activity, onDone: (canRequestAds: Boolean) -> Unit) = onDone(true)
    }
}
