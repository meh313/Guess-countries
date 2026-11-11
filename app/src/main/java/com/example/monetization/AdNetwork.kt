package com.example.monetization

import android.app.Activity
import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Which full-screen ads are loaded and ready to show. */
data class AdInventory(val interstitial: Boolean = false, val rewarded: Boolean = false)

/**
 * What the app needs from an ad network: one interstitial and one rewarded ad kept loaded, and a way to show each.
 * The app uses [AdMobNetwork]; tests use a fake, and a device without Google Play services gets [NONE].
 */
interface AdNetwork {
    /** The ads loaded right now; the screens read it to decide what to offer. */
    val inventory: StateFlow<AdInventory>

    /** Starts the SDK and loads the first ads. Called once consent allows ad requests; a second call does nothing. */
    fun start(context: Context)

    /**
     * Shows the loaded interstitial over [activity] and runs [onClosed] once it is dismissed (or failed to show).
     * False, with nothing shown and [onClosed] not run, when none is loaded.
     */
    fun showInterstitial(activity: Activity, onClosed: () -> Unit): Boolean

    /**
     * Shows the loaded rewarded ad over [activity]: [onEarned] runs when the player has watched enough to earn the
     * reward, [onClosed] once the ad is dismissed (or failed to show), after [onEarned] when both happen. False, with
     * nothing shown and nothing run, when none is loaded.
     */
    fun showRewarded(activity: Activity, onEarned: () -> Unit, onClosed: () -> Unit): Boolean

    /** No ads at all: nothing is ever loaded. */
    object NONE : AdNetwork {
        override val inventory: StateFlow<AdInventory> = MutableStateFlow(AdInventory())
        override fun start(context: Context) = Unit
        override fun showInterstitial(activity: Activity, onClosed: () -> Unit): Boolean = false
        override fun showRewarded(activity: Activity, onEarned: () -> Unit, onClosed: () -> Unit): Boolean = false
    }
}
