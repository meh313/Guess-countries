package com.example

import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.ViewModelProvider
import com.example.support.FreshDatabaseRule
import com.example.support.SettledTodayRule
import com.example.support.TestWorkManagerRule
import com.example.support.drawWindow
import com.example.support.eventually
import com.example.support.writePng
import com.example.ui.viewmodel.CountryViewModel
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w360dp-h800dp-xxhdpi")
class HomeShotsTest {
    private val rule = createAndroidComposeRule<MainActivity>()
    @get:Rule val chain: RuleChain =
        RuleChain.outerRule(FreshDatabaseRule()).around(TestWorkManagerRule()).around(SettledTodayRule()).around(rule)
    private val vm get() = ViewModelProvider(rule.activity)[CountryViewModel::class.java]
    private fun save(n: String) = rule.drawWindow().writePng(File("build/screenshots", "$n.png"))

    @Test
    fun shoot() {
        rule.waitForIdle()
        save("home_new")
        rule.onNodeWithTag("home_badges_count").performScrollTo()
        rule.waitForIdle()
        save("home_new_badges")
        rule.onNodeWithTag("home_scroll").performScrollTo()
        rule.onNodeWithTag("nav_tab_home").performClick()
        rule.onNodeWithTag("home_daily_btn").performScrollTo().performClick()
        rule.waitForIdle()
        save("daily_question")
        rule.mainClock.autoAdvance = false
        var g = 0
        while (vm.quizSession.value?.isFinished == false) {
            rule.runOnUiThread {
                vm.answerQuiz(vm.quizSession.value!!.current.correctAnswerIndex)
                vm.nextQuizQuestion()
            }
            check(++g <= 20)
        }
        rule.mainClock.autoAdvance = true
        rule.eventually("rewards") { rule.onNodeWithTag("quiz_reward_xp").performScrollTo() }
        rule.mainClock.autoAdvance = false
        rule.mainClock.advanceTimeBy(900)
        save("score_confetti_rewards")
        rule.mainClock.autoAdvance = true
        rule.onNodeWithTag("quiz_finish_done_btn").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("nav_tab_home").performClick()
        rule.waitForIdle()
        save("home_after_daily")
        rule.onNodeWithTag("home_badges_count").performScrollTo()
        rule.waitForIdle()
        save("home_after_daily_badges")
    }
}
