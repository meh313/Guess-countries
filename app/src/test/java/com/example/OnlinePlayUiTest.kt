package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.ViewModelProvider
import com.example.support.FreshDatabaseRule
import com.example.support.SettledTodayRule
import com.example.support.TestWorkManagerRule
import com.example.support.openTab
import com.example.ui.viewmodel.CountryViewModel
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The screens of online play as the app's own factory builds them, in a build without Firebase configuration (the
 * tests never carry google-services.json): the Home card leads to Friends and to the leaderboard, both say online
 * play is not available, Settings shows the same and About the online paragraph. The flows with a server are in
 * CountryViewModelOnlineTest.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w360dp-h800dp-xxhdpi")
class OnlinePlayUiTest {

    private val composeRule = createAndroidComposeRule<MainActivity>()

    @get:Rule val chain: RuleChain =
        RuleChain.outerRule(FreshDatabaseRule()).around(TestWorkManagerRule()).around(SettledTodayRule()).around(composeRule)

    private val rule get() = composeRule
    private val viewModel get() = ViewModelProvider(composeRule.activity)[CountryViewModel::class.java]

    @Test
    fun theHomeCard_opensFriends_whichSaysOnlinePlayIsNotInThisBuild_andBackReturnsHome() {
        rule.waitForIdle()
        assertFalse(viewModel.online.value.configured)
        rule.onNodeWithTag("home_friends_card").performScrollTo().assertIsDisplayed()

        rule.onNodeWithTag("home_friends_btn").performScrollTo().performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("friends_unavailable").assertIsDisplayed()
        rule.onNodeWithTag("friends_unavailable").assertTextContains("Online play is not available in this build of the app.")
        rule.onNodeWithTag("nav_tab_home").assertIsSelected()

        rule.onNodeWithTag("friends_back_btn").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("home_play_btn").assertIsDisplayed()
    }

    @Test
    fun theLeaderboard_opensFromHome_andSaysTheSame() {
        rule.waitForIdle()
        rule.onNodeWithTag("home_leaderboard_btn").performScrollTo().performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("leaderboard_unavailable").assertIsDisplayed()
        rule.onNodeWithTag("nav_tab_home").assertIsSelected()
        rule.onNodeWithTag("leaderboard_back_btn").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("home_daily_card").assertIsDisplayed()
    }

    @Test
    fun settingsAndAbout_tellAboutOnlinePlay() {
        rule.openTab("stats")
        rule.onNodeWithTag("stats_settings_btn").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("settings_online_unavailable").performScrollTo().assertTextEquals("Online play is not available in this build of the app.")

        rule.onNodeWithTag("settings_about_btn").performScrollTo().performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("about_privacy_online").performScrollTo().assertTextContains("Online play is optional", substring = true)
    }
}
