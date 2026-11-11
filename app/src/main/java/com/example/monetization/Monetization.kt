package com.example.monetization

import android.app.Activity
import android.content.Context
import com.example.data.settings.UserSettings
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailabilityLight
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Whether the quiz offers its 50/50 hint, and at what price. */
enum class HintOffer {
    /** No hint: no rewarded ad is loaded (offline, or no ads on this device) and ads were not bought away. */
    NONE,

    /** The hint is free: Remove ads was bought. */
    FREE,

    /** The hint is given for watching a rewarded ad. */
    WATCH_AD
}

/**
 * The free app's ads and the purchase that removes them, as the screens see them: whether an interstitial is due
 * after a quiz ([AdPolicy]), the 50/50 hint and what it costs, and the Remove ads purchase. It joins the ad network,
 * the consent form and the store, each of which the app or a test supplies; [UserSettings] keeps the quiz count,
 * the first-run time and a copy of the purchase, so a player who paid sees no ad even before the store answers.
 */
class Monetization(
    private val settings: UserSettings,
    private val ads: AdNetwork = AdNetwork.NONE,
    private val store: Store = Store.UNAVAILABLE,
    private val consent: ConsentGate = ConsentGate.NONE,
    scope: CoroutineScope,
    /** Wall-clock milliseconds, for the grace period after the first run. */
    private val now: () -> Long = System::currentTimeMillis
) {
    private val _adsRemoved = MutableStateFlow(settings.adsRemoved)

    /** True once Remove ads is bought: no interstitials, and the hint is free. The setting's copy until the store answers. */
    val adsRemoved: StateFlow<Boolean> = _adsRemoved

    /** The price of Remove ads as the store formats it, null until known. */
    val removeAdsPrice: StateFlow<String?> get() = store.removeAdsPrice

    /** Where a purchase or a restore stands. */
    val purchase: StateFlow<PurchaseState> get() = store.purchase

    /** True while the store can sell Remove ads here. */
    val storeAvailable: StateFlow<Boolean> get() = store.available

    /** True where the player may change their ad consent later: Settings shows the way to the form. */
    val privacyOptionsRequired: StateFlow<Boolean> get() = consent.privacyOptionsRequired

    /** The hint on offer for a quiz question: free for a buyer, for an ad while one is loaded, else none. */
    val hintOffer: StateFlow<HintOffer> = combine(adsRemoved, ads.inventory) { removed, inventory ->
        when {
            removed -> HintOffer.FREE
            inventory.rewarded -> HintOffer.WATCH_AD
            else -> HintOffer.NONE
        }
    }.stateIn(scope, SharingStarted.Eagerly, if (settings.adsRemoved) HintOffer.FREE else HintOffer.NONE)

    private var started = false

    init {
        if (settings.firstRunAt == 0L) settings.firstRunAt = now()
        // The store's word is final: a purchase made on another phone appears, a refunded one goes.
        scope.launch {
            store.adsRemoved.collect { owned ->
                if (owned != null && owned != _adsRemoved.value) {
                    settings.adsRemoved = owned
                    _adsRemoved.value = owned
                }
            }
        }
    }

    /**
     * Connects the store and, unless ads were bought away, gathers consent and starts the ads. Once per instance:
     * the activity may be created again (a rotation) while this lives on in the view model.
     */
    fun start(activity: Activity) {
        if (started) return
        started = true
        store.connect(activity.applicationContext)
        if (adsRemoved.value) return
        val app = activity.applicationContext
        consent.gather(activity) { canRequestAds -> if (canRequestAds && !adsRemoved.value) ads.start(app) }
    }

    /** A quiz finished and was saved: one more towards the next interstitial. */
    fun onQuizFinished() {
        settings.quizzesSinceAd = settings.quizzesSinceAd + 1
    }

    /** True when the rules say an interstitial is due and one is loaded. */
    fun interstitialDue(): Boolean =
        AdPolicy.interstitialDue(settings.quizzesSinceAd, settings.firstRunAt, now(), adsRemoved.value) &&
            ads.inventory.value.interstitial

    /**
     * Leaving the score screen: shows the interstitial when one is due and loaded, then runs [then] once it is
     * dismissed; otherwise runs [then] at once. The count starts again only when an ad was shown, so a player who
     * was offline is not spared the ad for good, only until one loads.
     */
    fun showInterstitialIfDue(activity: Activity, then: () -> Unit) {
        if (!interstitialDue()) {
            then()
            return
        }
        if (ads.showInterstitial(activity, onClosed = then)) settings.quizzesSinceAd = 0 else then()
    }

    /**
     * The player asked for the 50/50 hint: [onGranted] runs at once for a buyer, after the rewarded ad was watched to
     * its reward otherwise (not when it was closed early, and not when none could be shown). Nothing happens without
     * an offer, or without an [activity] to show the ad over.
     */
    fun requestHint(activity: Activity?, onGranted: () -> Unit) {
        when (hintOffer.value) {
            HintOffer.FREE -> onGranted()
            HintOffer.WATCH_AD -> {
                if (activity == null) return
                var earned = false
                ads.showRewarded(activity, onEarned = { earned = true }, onClosed = { if (earned) onGranted() })
            }
            HintOffer.NONE -> Unit
        }
    }

    /** Opens the store's purchase sheet for Remove ads. */
    fun buyRemoveAds(activity: Activity) = store.buyRemoveAds(activity)

    /** Looks the purchases up again (a new phone, a refund). */
    fun restorePurchases() = store.restore()

    /** The purchase message was read. */
    fun clearPurchaseState() = store.clearPurchaseState()

    /** Shows the ad privacy options form; the ads start or stop with the new choice at the next start. */
    fun showPrivacyOptions(activity: Activity) = consent.showPrivacyOptions(activity) {}

    companion object {
        /**
         * Whether this device has Google Play services, which AdMob, the consent form and Play Billing all need. False
         * on a phone without Google and under Robolectric, where the app then has no ads and nothing for sale.
         */
        fun isSupported(context: Context): Boolean =
            GoogleApiAvailabilityLight.getInstance().isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS
    }
}
