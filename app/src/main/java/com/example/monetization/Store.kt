package com.example.monetization

import android.app.Activity
import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Where the Remove ads purchase stands, for the Settings screen's one line about it. */
enum class PurchaseState {
    /** Nothing under way, nothing to say. */
    IDLE,

    /** Waiting for the store: the purchase sheet is open, or purchases are being looked up. */
    BUSY,

    /** The purchase is made and acknowledged; [Store.adsRemoved] is true. */
    OWNED,

    /** Restore found no Remove ads purchase on the account. */
    NOTHING_TO_RESTORE,

    /** The store took the order but waits for the payment (cash at a shop, a slow bank); the ads go once it clears. */
    PENDING,

    /** The store cannot be reached, or the product is not for sale here. */
    UNAVAILABLE,

    /** The purchase did not go through; nothing was charged. */
    FAILED
}

/**
 * The one-time Remove ads purchase, as the app sees the store. The app uses [PlayStore] (Google Play Billing);
 * tests use a fake, and a device without Google Play gets [UNAVAILABLE].
 */
interface Store {
    /** Whether Remove ads is owned, once the store has answered; null until then, and the cached setting applies. */
    val adsRemoved: StateFlow<Boolean?>

    /** The price in the player's currency, as the store formats it ("$3.99"); null until known. */
    val removeAdsPrice: StateFlow<String?>

    /** The purchase under way or just finished; see [PurchaseState]. */
    val purchase: StateFlow<PurchaseState>

    /** True while the store is connected and sells Remove ads here: the Buy and Restore buttons are enabled. */
    val available: StateFlow<Boolean>

    /** Connects to the store and looks up the price and the purchases already made. A second call does nothing. */
    fun connect(context: Context)

    /** Opens the store's purchase sheet for Remove ads over [activity]. */
    fun buyRemoveAds(activity: Activity)

    /** Looks the purchases up again: for a player on a new phone, or after a refund. */
    fun restore()

    /** Back to [PurchaseState.IDLE] once the message was read. */
    fun clearPurchaseState()

    /** No store: nothing is for sale, nothing is owned. */
    object UNAVAILABLE : Store {
        override val adsRemoved: StateFlow<Boolean?> = MutableStateFlow(null)
        override val removeAdsPrice: StateFlow<String?> = MutableStateFlow(null)
        override val purchase: StateFlow<PurchaseState> = MutableStateFlow(PurchaseState.IDLE)
        override val available: StateFlow<Boolean> = MutableStateFlow(false)
        override fun connect(context: Context) = Unit
        override fun buyRemoveAds(activity: Activity) = Unit
        override fun restore() = Unit
        override fun clearPurchaseState() = Unit
    }

    companion object {
        /** The product id of the one-time purchase in the Play Console. */
        const val REMOVE_ADS = "remove_ads"
    }
}
