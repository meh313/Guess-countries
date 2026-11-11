package com.example.monetization

import android.app.Activity
import com.example.data.settings.InMemoryUserSettings
import com.example.support.FakeAdNetwork
import com.example.support.FakeConsent
import com.example.support.FakeStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MonetizationTest {
    private val firstRun = 1_700_000_000_000L
    private var now = firstRun + AdPolicy.GRACE_MS + 60_000L
    private val settings = InMemoryUserSettings(firstRunAt = firstRun)
    private val ads = FakeAdNetwork()
    private val store = FakeStore()
    private val consent = FakeConsent()
    // Unconfined: the store's answer reaches the setting as soon as it is given, as it does on the main thread.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val activity: Activity = Robolectric.buildActivity(Activity::class.java).setup().get()

    private fun monetization(settings: InMemoryUserSettings = this.settings) =
        Monetization(settings, ads, store, consent, scope, now = { now })

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun theFirstRunIsStamped_once() {
        val fresh = InMemoryUserSettings()
        monetization(fresh)
        assertEquals(now, fresh.firstRunAt)
        now += 1
        monetization(fresh)
        assertEquals("a later instance keeps the first stamp", now - 1, fresh.firstRunAt)
    }

    @Test
    fun start_connectsTheStore_gathersConsent_andStartsTheAdsOnce() {
        val m = monetization()
        m.start(activity)
        m.start(activity) // the activity was created again (a rotation)
        assertEquals(1, store.connected)
        assertEquals(1, consent.gathered)
        assertEquals(1, ads.started)
    }

    @Test
    fun start_doesNotStartTheAds_whenConsentIsRefused_orAdsAreBoughtAway() {
        val refused = Monetization(InMemoryUserSettings(firstRunAt = firstRun), ads, store, FakeConsent(canRequestAds = false), scope, now = { now })
        refused.start(activity)
        assertEquals(0, ads.started)

        val silentStore = FakeStore(owned = null) // Play has not answered yet: the setting's copy applies
        val bought = Monetization(InMemoryUserSettings(firstRunAt = firstRun, adsRemoved = true), ads, silentStore, consent, scope, now = { now })
        bought.start(activity)
        assertEquals("the store is still connected, for a refund to show", 1, silentStore.connected)
        assertEquals("no consent form for a player who sees no ads", 0, consent.gathered)
        assertEquals(0, ads.started)
    }

    @Test
    fun theInterstitialShows_onLeavingTheThirdQuizsScore_andTheCountStartsAgain() {
        val m = monetization()
        ads.stock(interstitial = true)
        var left = 0
        repeat(2) {
            m.onQuizFinished()
            m.showInterstitialIfDue(activity) { left++ }
        }
        assertEquals("no ad before the third quiz", 0, ads.interstitialsShown)
        assertEquals(2, left)

        m.onQuizFinished()
        assertTrue(m.interstitialDue())
        m.showInterstitialIfDue(activity) { left++ }
        assertEquals(1, ads.interstitialsShown)
        assertEquals("the way on waits for the ad to close", 2, left)
        ads.closeInterstitial()
        assertEquals(3, left)
        assertEquals(0, settings.quizzesSinceAd)
        assertFalse(m.interstitialDue())
    }

    @Test
    fun noAdLoaded_theWayOnIsTakenAtOnce_andTheCountKeepsGrowing() {
        val m = monetization()
        repeat(4) { m.onQuizFinished() }
        var left = 0
        m.showInterstitialIfDue(activity) { left++ }
        assertEquals(1, left)
        assertEquals(0, ads.interstitialsShown)
        assertEquals("the ad is owed until one loads", 4, settings.quizzesSinceAd)
        assertFalse(m.interstitialDue())
        ads.stock(interstitial = true)
        assertTrue(m.interstitialDue())
    }

    @Test
    fun noInterstitial_inTheGracePeriod_orOnceAdsAreBought() {
        now = firstRun + 60_000L
        val m = monetization()
        ads.stock(interstitial = true)
        repeat(3) { m.onQuizFinished() }
        assertFalse("the first two days", m.interstitialDue())

        now = firstRun + AdPolicy.GRACE_MS
        assertTrue(m.interstitialDue())
        store.adsRemoved.value = true
        assertTrue(m.adsRemoved.value)
        assertTrue(settings.adsRemoved)
        assertFalse(m.interstitialDue())
        var left = 0
        m.showInterstitialIfDue(activity) { left++ }
        assertEquals(1, left)
        assertEquals(0, ads.interstitialsShown)
    }

    @Test
    fun theStoresAnswerIsFinal_aRefundBringsTheAdsBack() {
        val bought = InMemoryUserSettings(firstRunAt = firstRun, adsRemoved = true)
        val store = FakeStore(owned = null)
        val m = Monetization(bought, ads, store, consent, scope, now = { now })
        assertTrue("the setting's copy applies until the store answers", m.adsRemoved.value)
        assertEquals(HintOffer.FREE, m.hintOffer.value)

        store.adsRemoved.value = false
        assertFalse(m.adsRemoved.value)
        assertFalse(bought.adsRemoved)
        assertEquals(HintOffer.NONE, m.hintOffer.value)
    }

    @Test
    fun theHint_isFreeForABuyer_forAnAdWhileOneIsLoaded_andNotOfferedOtherwise() {
        val m = monetization()
        assertEquals(HintOffer.NONE, m.hintOffer.value)
        var granted = 0
        m.requestHint(activity) { granted++ }
        assertEquals(0, granted)

        ads.stock(rewarded = true)
        assertEquals(HintOffer.WATCH_AD, m.hintOffer.value)
        m.requestHint(activity) { granted++ }
        assertEquals(1, ads.rewardedShown)
        assertEquals("the hint waits for the reward", 0, granted)
        ads.finishRewarded()
        assertEquals(1, granted)
        assertEquals("the ad is spent until the next loads", HintOffer.NONE, m.hintOffer.value)

        ads.stock(rewarded = true)
        m.requestHint(activity) { granted++ }
        ads.abandonRewarded()
        assertEquals("closed early: no hint", 1, granted)

        store.adsRemoved.value = true
        assertEquals(HintOffer.FREE, m.hintOffer.value)
        m.requestHint(activity) { granted++ }
        assertEquals(2, granted)
        assertEquals("a buyer watches no ad", 2, ads.rewardedShown)
    }

    @Test
    fun theHintForAnAd_needsAnActivityToShowOver() {
        val m = monetization()
        ads.stock(rewarded = true)
        var granted = 0
        m.requestHint(null) { granted++ }
        assertEquals(0, ads.rewardedShown)
        assertEquals(0, granted)
    }

    @Test
    fun buyAndRestore_goToTheStore_andThePriceComesFromIt() {
        val m = monetization()
        assertEquals("$3.99", m.removeAdsPrice.value)
        assertTrue(m.storeAvailable.value)
        m.buyRemoveAds(activity)
        assertEquals(1, store.buyRequests)
        assertEquals(PurchaseState.BUSY, m.purchase.value)
        store.completePurchase(bought = true)
        assertEquals(PurchaseState.OWNED, m.purchase.value)
        assertTrue(m.adsRemoved.value)
        m.restorePurchases()
        assertEquals(1, store.restores)
        m.clearPurchaseState()
        assertEquals(PurchaseState.IDLE, m.purchase.value)
    }

    @Test
    fun withoutGooglePlay_nothingIsForSale_andNoAdShows() {
        val m = Monetization(settings, AdNetwork.NONE, Store.UNAVAILABLE, ConsentGate.NONE, scope, now = { now })
        m.start(activity)
        repeat(5) { m.onQuizFinished() }
        var left = 0
        m.showInterstitialIfDue(activity) { left++ }
        assertEquals(1, left)
        assertFalse(m.storeAvailable.value)
        assertEquals(HintOffer.NONE, m.hintOffer.value)
        assertFalse(m.privacyOptionsRequired.value)
    }
}
