package com.example

import androidx.activity.compose.setContent
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.data.local.AppDatabase
import com.example.data.model.CountryRepository
import com.example.data.settings.InMemoryUserSettings
import com.example.monetization.AdPolicy
import com.example.monetization.HintOffer
import com.example.quiz.QuizEngine
import com.example.quiz.QuizMode
import com.example.support.FakeAdNetwork
import com.example.support.FakeConsent
import com.example.support.FakeSpeech
import com.example.support.FakeStore
import com.example.support.FreshDatabaseRule
import com.example.support.closeWhenIdle
import com.example.support.eventually
import com.example.support.inMemoryDatabase
import com.example.support.stateDescriptionIs
import com.example.ui.screens.QuizScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.viewmodel.CountryViewModel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The ads and the Remove ads purchase on the screens, with a fake ad network and store: the Settings section, the
 * 50/50 hint in the quiz and the interstitial on leaving the score screen. The real activity under Robolectric has
 * no Google Play services, so these screens are set with a view model built here.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w360dp-h640dp-xxhdpi")
class MonetizationUiTest {

    private val composeRule = createAndroidComposeRule<MainActivity>()

    @get:Rule val chain: RuleChain = RuleChain.outerRule(FreshDatabaseRule()).around(composeRule)

    private val rule get() = composeRule

    private lateinit var db: AppDatabase
    private val ads = FakeAdNetwork()
    private val store = FakeStore()
    private val consent = FakeConsent(privacyOptions = true)
    private val firstRun = 1_700_000_000_000L
    private val settings = InMemoryUserSettings(firstRunAt = firstRun)
    private lateinit var viewModel: CountryViewModel

    @Before
    fun setUp() {
        db = inMemoryDatabase()
        viewModel = CountryViewModel(CountryRepository(db.userProgressDao()), FakeSpeech(), settings = settings, ads = ads, store = store, consent = consent)
    }

    @After
    fun tearDown() {
        db.closeWhenIdle(viewModel)
    }

    private fun showSettings() {
        rule.activity.setContent { SettingsScreen(viewModel = viewModel, onBack = {}, onAbout = {}) }
        rule.waitForIdle()
    }

    private fun showQuiz() {
        rule.activity.setContent { QuizScreen(viewModel = viewModel) }
        rule.waitForIdle()
    }

    @Test
    fun settings_offerRemoveAdsAtTheStoresPrice_andSayThankYouOnceBought() {
        showSettings()
        rule.onNodeWithTag("settings_remove_ads_btn").performScrollTo().assertIsDisplayed().assertIsEnabled().assert(hasText("Buy · $3.99"))
        rule.onNodeWithText("Remove ads").assertExists()
        rule.onNodeWithText("The free app shows one short ad after every third quiz", substring = true).assertExists()
        rule.onNodeWithTag("settings_restore_purchase_btn").assertIsEnabled()
        rule.onNodeWithTag("settings_ad_privacy_btn").assertExists()
        rule.onNodeWithTag("settings_ads_removed").assertDoesNotExist()
        rule.onNodeWithTag("settings_store_unavailable").assertDoesNotExist()

        rule.onNodeWithTag("settings_remove_ads_btn").performClick()
        rule.waitForIdle()
        assertEquals(1, store.buyRequests)
        rule.onNodeWithTag("settings_purchase_busy").assertExists()
        rule.onNodeWithTag("settings_remove_ads_btn").assertIsNotEnabled()

        store.completePurchase(bought = true)
        rule.eventually("the thank-you line") { rule.onNodeWithTag("settings_ads_removed").assertExists() }
        rule.onNodeWithText("Ads removed: thank you for supporting World Flags. The 50/50 quiz hint is free.").assertExists()
        rule.onNodeWithTag("settings_remove_ads_btn").assertDoesNotExist()
        rule.onNodeWithTag("settings_restore_purchase_btn").assertDoesNotExist()
        assertTrue(settings.adsRemoved)
        assertEquals(HintOffer.FREE, viewModel.hintOffer.value)
    }

    @Test
    fun settings_reportAFailedPurchase_andARestoreThatFindsNothing() {
        showSettings()
        rule.onNodeWithTag("settings_remove_ads_btn").performScrollTo().performClick()
        store.completePurchase(bought = false)
        rule.eventually("the failure line") {
            rule.onNodeWithTag("settings_purchase_message").assert(hasText("The purchase did not go through and nothing was charged. Try again later."))
        }
        assertFalse(settings.adsRemoved)

        rule.onNodeWithTag("settings_restore_purchase_btn").performScrollTo().performClick()
        rule.eventually("the nothing-to-restore line") {
            rule.onNodeWithTag("settings_purchase_message").assert(hasText("No Remove ads purchase was found on this Google account."))
        }
        assertEquals(1, store.restores)
    }

    @Test
    fun settings_withoutAStore_disableBuying_andSaySo() {
        store.available.value = false
        showSettings()
        rule.onNodeWithTag("settings_remove_ads_btn").performScrollTo().assertIsNotEnabled()
        rule.onNodeWithTag("settings_restore_purchase_btn").assertIsNotEnabled()
        rule.onNodeWithTag("settings_store_unavailable").assertExists()
    }

    @Test
    fun theHint_isOfferedForAnAd_takesTwoWrongOptionsAway_andHalvesThePoints() {
        showQuiz()
        viewModel.startQuiz(QuizMode.FLAG_NAME, QuizEngine.GLOBAL)
        rule.waitForIdle()
        rule.onNodeWithTag("quiz_hint_btn").assertDoesNotExist()

        ads.stock(rewarded = true)
        rule.waitForIdle()
        rule.onNodeWithTag("quiz_hint_btn").performScrollTo().assert(hasText("50/50 hint · watch an ad")).performClick()
        rule.waitForIdle()
        assertEquals(1, ads.rewardedShown)
        assertNotNull("no hint before the reward", viewModel.quizSession.value)
        assertTrue(viewModel.quizSession.value!!.eliminated.isEmpty())

        ads.finishRewarded()
        rule.waitForIdle()
        val session = viewModel.quizSession.value!!
        assertEquals(2, session.eliminated.size)
        rule.onNodeWithTag("quiz_hint_btn").assertDoesNotExist()
        rule.onNodeWithTag("quiz_hint_used").assertExists()
        for (index in session.eliminated) {
            rule.onNodeWithTag("quiz_option_$index").assert(stateDescriptionIs("Removed by the hint")).assertIsNotEnabled()
        }
        val right = session.current.correctAnswerIndex
        rule.onNodeWithTag("quiz_option_$right").assertIsEnabled().performClick()
        rule.waitForIdle()
        assertEquals(QuizEngine.pointsForCorrectAnswer(0) / 2, viewModel.quizSession.value!!.score)
        assertTrue(viewModel.quizSession.value!!.answers.single().hinted)
    }

    @Test
    fun theHint_isFreeForABuyer_andNotOfferedWithoutAnAdOrAPurchase() {
        store.adsRemoved.value = true
        showQuiz()
        viewModel.startQuiz(QuizMode.FLAG_NAME, QuizEngine.GLOBAL)
        rule.waitForIdle()
        rule.onNodeWithTag("quiz_hint_btn").performScrollTo().assert(hasText("50/50 hint")).performClick()
        rule.waitForIdle()
        assertEquals(0, ads.rewardedShown)
        assertEquals(2, viewModel.quizSession.value!!.eliminated.size)
    }

    @Test
    fun theInterstitial_showsOnLeavingTheThirdScoreScreen_andTheWayOnWaitsForIt() {
        settings.quizzesSinceAd = 2
        ads.stock(interstitial = true)
        showQuiz()
        viewModel.startQuiz(QuizMode.FLAG_NAME, QuizEngine.GLOBAL)
        rule.waitForIdle()
        // Play the quiz through, by the view model: the screen's own flow is covered elsewhere.
        while (viewModel.quizSession.value?.isFinished == false) {
            val s = viewModel.quizSession.value!!
            if (!s.hasAnswered) viewModel.answerQuiz(s.current.correctAnswerIndex) else viewModel.nextQuizQuestion()
        }
        rule.waitForIdle()
        rule.eventually("the quiz to be saved and counted") { assertEquals(3, settings.quizzesSinceAd) }
        rule.onNodeWithTag("quiz_finish_done_btn").performScrollTo().performClick()
        rule.waitForIdle()

        assertEquals(1, ads.interstitialsShown)
        assertNotNull("the score screen stays until the ad closes", viewModel.quizSession.value)
        ads.closeInterstitial()
        rule.waitForIdle()
        assertNull(viewModel.quizSession.value)
        assertEquals(0, settings.quizzesSinceAd)
    }

    @Test
    fun noInterstitial_inTheFirstTwoDays() {
        settings.firstRunAt = System.currentTimeMillis() - AdPolicy.GRACE_MS / 2
        settings.quizzesSinceAd = 5
        ads.stock(interstitial = true)
        showQuiz()
        viewModel.startQuiz(QuizMode.FLAG_NAME, QuizEngine.GLOBAL)
        rule.waitForIdle()
        while (viewModel.quizSession.value?.isFinished == false) {
            val s = viewModel.quizSession.value!!
            if (!s.hasAnswered) viewModel.answerQuiz(s.current.correctAnswerIndex) else viewModel.nextQuizQuestion()
        }
        rule.waitForIdle()
        rule.onNodeWithTag("quiz_finish_done_btn").performScrollTo().performClick()
        rule.waitForIdle()
        assertEquals(0, ads.interstitialsShown)
        assertNull(viewModel.quizSession.value)
    }
}
