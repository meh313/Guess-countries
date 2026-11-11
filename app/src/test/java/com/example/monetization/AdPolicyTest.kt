package com.example.monetization

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdPolicyTest {
    private val firstRun = 1_700_000_000_000L
    private val later = firstRun + AdPolicy.GRACE_MS + 1

    @Test
    fun anInterstitialIsDue_afterThreeQuizzes_onceTheGracePeriodIsOver() {
        assertFalse(AdPolicy.interstitialDue(quizzesSinceAd = 2, firstRunAt = firstRun, now = later, adsRemoved = false))
        assertTrue(AdPolicy.interstitialDue(quizzesSinceAd = 3, firstRunAt = firstRun, now = later, adsRemoved = false))
        // Offline for a while: the count keeps growing, and the ad is still just one.
        assertTrue(AdPolicy.interstitialDue(quizzesSinceAd = 9, firstRunAt = firstRun, now = later, adsRemoved = false))
    }

    @Test
    fun noInterstitial_inTheFirstTwoDays() {
        assertFalse(AdPolicy.interstitialDue(3, firstRun, firstRun, false))
        assertFalse(AdPolicy.interstitialDue(3, firstRun, firstRun + AdPolicy.GRACE_MS - 1, false))
        assertTrue(AdPolicy.interstitialDue(3, firstRun, firstRun + AdPolicy.GRACE_MS, false))
    }

    @Test
    fun noInterstitial_whileTheFirstRunIsUnknown_orTheClockWentBack() {
        assertFalse(AdPolicy.interstitialDue(3, firstRunAt = 0L, now = later, adsRemoved = false))
        assertFalse(AdPolicy.interstitialDue(3, firstRunAt = firstRun, now = firstRun - 1, adsRemoved = false))
    }

    @Test
    fun noInterstitial_onceAdsAreBoughtAway() {
        assertFalse(AdPolicy.interstitialDue(30, firstRun, later, adsRemoved = true))
    }
}
