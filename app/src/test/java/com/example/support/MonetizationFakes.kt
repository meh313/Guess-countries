package com.example.support

import android.app.Activity
import android.content.Context
import com.example.monetization.AdInventory
import com.example.monetization.AdNetwork
import com.example.monetization.ConsentGate
import com.example.monetization.PurchaseState
import com.example.monetization.Store
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * An ad network the test stocks by hand: [stock] what is loaded, and each show records itself and hands the test the
 * callbacks to run as the ad "closes" ([closeInterstitial], [finishRewarded], [abandonRewarded]).
 */
class FakeAdNetwork : AdNetwork {
    override val inventory = MutableStateFlow(AdInventory())
    var started = 0
    var interstitialsShown = 0
    var rewardedShown = 0
    private var onInterstitialClosed: (() -> Unit)? = null
    private var onEarned: (() -> Unit)? = null
    private var onRewardedClosed: (() -> Unit)? = null

    fun stock(interstitial: Boolean = inventory.value.interstitial, rewarded: Boolean = inventory.value.rewarded) {
        inventory.value = AdInventory(interstitial, rewarded)
    }

    override fun start(context: Context) {
        started++
    }

    override fun showInterstitial(activity: Activity, onClosed: () -> Unit): Boolean {
        if (!inventory.value.interstitial) return false
        interstitialsShown++
        inventory.value = inventory.value.copy(interstitial = false)
        onInterstitialClosed = onClosed
        return true
    }

    override fun showRewarded(activity: Activity, onEarned: () -> Unit, onClosed: () -> Unit): Boolean {
        if (!inventory.value.rewarded) return false
        rewardedShown++
        inventory.value = inventory.value.copy(rewarded = false)
        this.onEarned = onEarned
        onRewardedClosed = onClosed
        return true
    }

    /** The interstitial on screen is dismissed. */
    fun closeInterstitial() {
        onInterstitialClosed?.invoke()
        onInterstitialClosed = null
    }

    /** The rewarded ad on screen is watched to its reward and then dismissed. */
    fun finishRewarded() {
        onEarned?.invoke()
        onRewardedClosed?.invoke()
        onEarned = null
        onRewardedClosed = null
    }

    /** The rewarded ad on screen is dismissed before the reward. */
    fun abandonRewarded() {
        onRewardedClosed?.invoke()
        onEarned = null
        onRewardedClosed = null
    }
}

/** A store the test controls: [price] and [owned] stand for the Play Console's product and the account's purchases. */
class FakeStore(price: String? = "$3.99", owned: Boolean? = false, available: Boolean = true) : Store {
    override val adsRemoved = MutableStateFlow(owned)
    override val removeAdsPrice = MutableStateFlow(price)
    override val purchase = MutableStateFlow(PurchaseState.IDLE)
    override val available = MutableStateFlow(available)
    var connected = 0
    var buyRequests = 0
    var restores = 0

    override fun connect(context: Context) {
        connected++
    }

    override fun buyRemoveAds(activity: Activity) {
        buyRequests++
        purchase.value = PurchaseState.BUSY
    }

    /** The purchase sheet ends: bought, or not. */
    fun completePurchase(bought: Boolean) {
        if (bought) {
            adsRemoved.value = true
            purchase.value = PurchaseState.OWNED
        } else {
            purchase.value = PurchaseState.FAILED
        }
    }

    override fun restore() {
        restores++
        purchase.value = if (adsRemoved.value == true) PurchaseState.OWNED else PurchaseState.NOTHING_TO_RESTORE
    }

    override fun clearPurchaseState() {
        purchase.value = PurchaseState.IDLE
    }
}

/** A consent gate whose answer the test sets; [gathered] counts the asks. */
class FakeConsent(private val canRequestAds: Boolean = true, privacyOptions: Boolean = false) : ConsentGate {
    override val privacyOptionsRequired: StateFlow<Boolean> = MutableStateFlow(privacyOptions)
    var gathered = 0
    var privacyFormsShown = 0

    override fun gather(activity: Activity, onDone: (canRequestAds: Boolean) -> Unit) {
        gathered++
        onDone(canRequestAds)
    }

    override fun showPrivacyOptions(activity: Activity, onDone: (canRequestAds: Boolean) -> Unit) {
        privacyFormsShown++
        onDone(canRequestAds)
    }
}
