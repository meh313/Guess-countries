package com.example.monetization

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AgeRestrictedTreatment
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * Google AdMob: one interstitial and one rewarded ad kept loaded, each loaded again after it is shown or fails, with
 * a growing wait between failed loads (offline, no fill). Every ad request asks for the G content rating and no
 * age-restricted treatment (the audience is 13 and over, neither children nor under the age of consent); the categories the
 * owner blocks (gambling, alcohol, dating and the rest) are set in the AdMob console, see docs/monetization.md.
 * The ad unit ids come from the build (Google's test ids unless the release sets the real ones).
 */
class AdMobNetwork(
    private val interstitialUnitId: String,
    private val rewardedUnitId: String
) : AdNetwork {
    private val _inventory = MutableStateFlow(AdInventory())
    override val inventory: StateFlow<AdInventory> = _inventory

    private val main = Handler(Looper.getMainLooper())
    private var interstitial: InterstitialAd? = null
    private var rewarded: RewardedAd? = null
    private var interstitialRetryMs = FIRST_RETRY_MS
    private var rewardedRetryMs = FIRST_RETRY_MS

    @Volatile
    private var started = false

    override fun start(context: Context) {
        if (started) return
        started = true
        val app = context.applicationContext
        MobileAds.setRequestConfiguration(
            RequestConfiguration.Builder()
                .setMaxAdContentRating(RequestConfiguration.MAX_AD_CONTENT_RATING_G)
                // Neither child-directed nor for an audience under the age of consent: a general 13+ audience.
                .setAgeRestrictedTreatment(AgeRestrictedTreatment.UNSPECIFIED)
                .build()
        )
        // Initialising can take a while, and Google documents it as work for a background thread; the ads
        // themselves are loaded and shown on the main thread, as the SDK asks.
        Thread({
            MobileAds.initialize(app) {
                main.post {
                    loadInterstitial(app)
                    loadRewarded(app)
                }
            }
        }, "admob-init").start()
    }

    private fun loadInterstitial(context: Context) {
        InterstitialAd.load(context, interstitialUnitId, AdRequest.Builder().build(), object : InterstitialAdLoadCallback() {
            override fun onAdLoaded(ad: InterstitialAd) {
                interstitial = ad
                interstitialRetryMs = FIRST_RETRY_MS
                _inventory.update { it.copy(interstitial = true) }
            }

            override fun onAdFailedToLoad(error: LoadAdError) {
                interstitial = null
                _inventory.update { it.copy(interstitial = false) }
                main.postDelayed({ loadInterstitial(context) }, interstitialRetryMs)
                interstitialRetryMs = (interstitialRetryMs * 2).coerceAtMost(MAX_RETRY_MS)
            }
        })
    }

    private fun loadRewarded(context: Context) {
        RewardedAd.load(context, rewardedUnitId, AdRequest.Builder().build(), object : RewardedAdLoadCallback() {
            override fun onAdLoaded(ad: RewardedAd) {
                rewarded = ad
                rewardedRetryMs = FIRST_RETRY_MS
                _inventory.update { it.copy(rewarded = true) }
            }

            override fun onAdFailedToLoad(error: LoadAdError) {
                rewarded = null
                _inventory.update { it.copy(rewarded = false) }
                main.postDelayed({ loadRewarded(context) }, rewardedRetryMs)
                rewardedRetryMs = (rewardedRetryMs * 2).coerceAtMost(MAX_RETRY_MS)
            }
        })
    }

    override fun showInterstitial(activity: Activity, onClosed: () -> Unit): Boolean {
        val ad = interstitial ?: return false
        // An interstitial shows once: it is out of stock from here, and the next one loads once this one is gone.
        interstitial = null
        _inventory.update { it.copy(interstitial = false) }
        val app = activity.applicationContext
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                onClosed()
                loadInterstitial(app)
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                onClosed()
                loadInterstitial(app)
            }
        }
        ad.show(activity)
        return true
    }

    override fun showRewarded(activity: Activity, onEarned: () -> Unit, onClosed: () -> Unit): Boolean {
        val ad = rewarded ?: return false
        rewarded = null
        _inventory.update { it.copy(rewarded = false) }
        val app = activity.applicationContext
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                onClosed()
                loadRewarded(app)
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                onClosed()
                loadRewarded(app)
            }
        }
        ad.show(activity) { onEarned() }
        return true
    }

    companion object {
        private const val FIRST_RETRY_MS = 30_000L
        private const val MAX_RETRY_MS = 10 * 60_000L
    }
}
