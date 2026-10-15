package com.example

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.data.local.AppDatabase
import com.example.data.local.SkillProgressEntity
import com.example.data.model.Country
import com.example.data.model.CountryCatalog
import com.example.data.model.CountryRepository
import com.example.progress.Evidence
import com.example.progress.Skill
import com.example.progress.SkillState
import com.example.data.settings.InMemoryUserSettings
import com.example.quiz.QuizDifficulty
import com.example.quiz.QuizEngine
import com.example.quiz.QuizMode
import com.example.quiz.sovereign
import com.example.support.setupNode
import com.example.support.FakeSpeech
import com.example.support.OwnDatabaseRule
import com.example.support.awaitQuizPlan
import com.example.support.awaitWeakSpots
import com.example.ui.screens.QuizScreen
import com.example.ui.theme.WorldFlagsTheme
import com.example.ui.viewmodel.CountryViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The quiz setup screen over a small, injected country list: the paths for a scope with too few countries
 * and for a scope with fewer than ten, which the full catalog no longer reaches.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w360dp-h640dp-xxhdpi")
class QuizSetupScreenTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    /** Closed once the activity is gone, not from @After under it, where the close once hit a write still in flight. */
    private val database = OwnDatabaseRule { created }

    @get:Rule val chain: RuleChain = RuleChain.outerRule(database).around(composeRule)

    private val rule get() = composeRule

    private val db: AppDatabase get() = database.db

    /** Three Oceania countries (too few) and six African ones (fewer than ten). */
    private val smallWorld =
        sovereign.filter { it.continent == "Oceania" }.take(3) + sovereign.filter { it.continent == "Africa" }.take(6)

    /** Clicks a setup control, scrolling the choices (the Start button below them is pinned and never scrolls). */
    private fun clickSetup(tag: String) {
        rule.setupNode(tag).performClick()
    }

    private val created = mutableListOf<CountryViewModel>()

    private fun newViewModel(settings: InMemoryUserSettings = InMemoryUserSettings(), countries: List<Country> = smallWorld) =
        CountryViewModel(CountryRepository(db.userProgressDao(), countries), FakeSpeech(), settings = settings).also { created += it }

    private fun show() {
        val vm = newViewModel()
        rule.setContent { WorldFlagsTheme { QuizScreen(vm) } }
        rule.waitForIdle()
    }

    @Test
    fun aScopeWithTooFewCountries_disablesStartAndExplainsWhy() {
        show()

        clickSetup("quiz_scope_oceania")

        rule.onNodeWithTag("start_quiz_btn").assertIsNotEnabled()
        rule.onNodeWithText("Needs at least ${QuizEngine.MIN_POOL} countries").assertIsDisplayed()
    }

    @Test
    fun aScopeWithFewerThanTenCountries_offersAShorterQuiz() {
        show()

        clickSetup("quiz_scope_africa")

        rule.onNodeWithTag("start_quiz_btn").assertIsEnabled()
        rule.onNodeWithText("Start 6-question quiz").assertIsDisplayed()
    }

    @Test
    fun theGlobalScope_countsEveryInjectedCountry() {
        show()

        rule.onNodeWithText("Start ${QuizEngine.questionCount(smallWorld.size)}-question quiz").assertIsDisplayed()
    }

    @Test
    fun theAnswersRow_choosesTheDifficultyForFlagQuestions() {
        show()

        rule.setupNode("quiz_difficulty_normal").assertIsSelected()
        rule.onNodeWithText("One look-alike flag among the answers").assertIsDisplayed()

        clickSetup("quiz_difficulty_hard")

        rule.onNodeWithTag("quiz_difficulty_hard").assertIsSelected()
        rule.onNodeWithText("Look-alike flags wherever they exist").assertIsDisplayed()
    }

    @Test
    fun theAnswersRow_isInactiveForQuestionsWithoutLookAlikeFlags() {
        show()

        clickSetup("quiz_mode_capital")

        rule.setupNode("quiz_difficulty_hard").assertIsNotEnabled()
        rule.onNodeWithText("Look-alike answers apply to flag questions").assertIsDisplayed()

        clickSetup("quiz_mode_pick_flag")

        rule.setupNode("quiz_difficulty_hard").assertIsEnabled()
    }

    @Test
    fun theBlitz_offersAOneMinuteQuizInsteadOfAQuestionCount() {
        show()

        clickSetup("quiz_mode_blitz")

        rule.onNodeWithText("As many flags as you can in one minute").assertIsDisplayed()
        rule.onNodeWithTag("start_quiz_btn").assertIsEnabled()
        rule.onNodeWithText("Start 60-second blitz").assertIsDisplayed()
        // It uses flags, so the Answers row still applies.
        rule.setupNode("quiz_difficulty_hard").assertIsEnabled()
    }

    @Test
    fun theTypedMode_isOfferedWithItsTitleAndHint_withoutTheAnswersRow() {
        val vm = newViewModel(countries = CountryCatalog.all)
        rule.setContent { WorldFlagsTheme { QuizScreen(vm) } }
        rule.waitForIdle()

        clickSetup("quiz_mode_type_name")

        rule.onNodeWithTag("quiz_mode_type_name").assertIsSelected()
        rule.onNodeWithText("Flag → Typed name").assertIsDisplayed()
        rule.onNodeWithText("No options: type the country's name").assertIsDisplayed()
        // No wrong answers to make harder.
        rule.setupNode("quiz_difficulty_hard").assertIsNotEnabled()
        rule.onNodeWithTag("start_quiz_btn").assertIsEnabled()
        rule.onNodeWithText("Start 10-question quiz").assertIsDisplayed()
    }

    @Test
    fun theMapMode_isOfferedWithItsTitleAndHint_andStartsATenQuestionQuizOverTheWorld() {
        val vm = newViewModel(countries = CountryCatalog.all)
        rule.setContent { WorldFlagsTheme { QuizScreen(vm) } }
        rule.waitForIdle()

        clickSetup("quiz_mode_locate")

        rule.onNodeWithTag("quiz_mode_locate").assertIsSelected()
        rule.onNodeWithText("Map → Country").assertIsDisplayed()
        rule.onNodeWithText("Where in the world is it?").assertIsDisplayed()
        // Not a flag question, so the Answers row does not apply; untimed and of a fixed length.
        rule.setupNode("quiz_difficulty_hard").assertIsNotEnabled()
        rule.onNodeWithTag("start_quiz_btn").assertIsEnabled()
        rule.onNodeWithText("Start 10-question quiz").assertIsDisplayed()

        rule.onNodeWithTag("start_quiz_btn").performClick()
        rule.waitForIdle()

        val session = vm.quizSession.value!!
        assertEquals(QuizMode.LOCATE, session.mode)
        assertEquals(QuizEngine.GLOBAL, session.scope)
        assertEquals(QuizEngine.MAX_QUESTIONS, session.questions.size)
        assertTrue(session.questions.all { q -> q.options.size == 4 && q.optionCodes.none { it == null } })
    }

    /** Gives four countries of the small world a wrong review each, so four weak spots exist before the screen opens. */
    private fun fourWeakSpots(): List<String> {
        val codes = smallWorld.take(4).map { it.code }
        runBlocking { codes.forEachIndexed { i, code -> db.userProgressDao().recordAnswer(code, Skill.FLAG, false, Evidence.FULL, 100L + i) } }
        return codes
    }

    private fun showWithWeakSpots(count: Int): CountryViewModel {
        val vm = newViewModel()
        rule.setContent { WorldFlagsTheme { QuizScreen(vm) } }
        rule.awaitWeakSpots(vm, count)
        rule.waitForIdle()
        return vm
    }

    @Test
    fun theWeakSpotsChip_isLockedAndExplainsItselfUntilFourCountriesAreWeak() {
        show()

        rule.setupNode("quiz_scope_weak_spots").assertIsNotEnabled()
        rule.setupNode("quiz_weak_spots_note")
        rule.onNodeWithText("Weak spots unlocks once 4 reviewed countries are below mastery (you have 0)").assertIsDisplayed()
    }

    @Test
    fun theWeakSpotsChip_countsTheCountriesItIsStillShortOf() {
        runBlocking { smallWorld.take(3).forEachIndexed { i, c -> db.userProgressDao().recordAnswer(c.code, Skill.FLAG, false, Evidence.FULL, 100L + i) } }

        showWithWeakSpots(3)

        rule.setupNode("quiz_scope_weak_spots").assertIsNotEnabled()
        rule.setupNode("quiz_weak_spots_note")
        rule.onNodeWithText("Weak spots unlocks once 4 reviewed countries are below mastery (you have 3)").assertIsDisplayed()
    }

    @Test
    fun theWeakSpotsChip_unlocksOnceFourCountriesAreWeakAndStartsAQuizAboutThem() {
        val weak = fourWeakSpots()
        val vm = showWithWeakSpots(4)

        clickSetup("quiz_scope_weak_spots")

        rule.onNodeWithTag("quiz_scope_weak_spots").assertIsSelected()
        rule.setupNode("quiz_weak_spots_note")
        rule.onNodeWithText("Your 4 weakest countries, with answers from the whole world").assertIsDisplayed()
        rule.onNodeWithTag("start_quiz_btn").assertIsEnabled()
        rule.onNodeWithText("Start 4-question quiz").assertIsDisplayed()

        rule.onNodeWithTag("start_quiz_btn").performClick()
        rule.waitForIdle()

        val session = vm.quizSession.value!!
        assertEquals(QuizEngine.WEAK_SPOTS, session.scope)
        assertEquals(weak.toSet(), session.questions.map { it.targetCountry.code }.toSet())
    }

    @Test
    fun theWeakSpotsChip_isInactiveInTheContinentQuizEvenWhenUnlocked() {
        fourWeakSpots()
        showWithWeakSpots(4)

        clickSetup("quiz_mode_continent")

        rule.setupNode("quiz_scope_weak_spots").assertIsNotEnabled()
        rule.onNodeWithText("The continent quiz always covers the whole world").assertIsDisplayed()
        rule.onNodeWithTag("quiz_weak_spots_note").assertDoesNotExist()
    }

    // --- Due today ----------------------------------------------------------------------------------------------------

    /** Makes the flags of [codes] due for review: reviewed before, due date long passed. */
    private fun dueFlags(codes: List<String>) = runBlocking {
        codes.forEach {
            db.userProgressDao().upsertSkill(SkillProgressEntity.of(it, Skill.FLAG, SkillState(score = 40, box = 2, dueAt = 0L, reviews = 2, correct = 1)))
        }
    }

    private fun showWithDue(count: Int): CountryViewModel {
        val vm = newViewModel()
        rule.setContent { WorldFlagsTheme { QuizScreen(vm) } }
        rule.awaitQuizPlan(vm) { it.due(Skill.FLAG).size == count }
        rule.waitForIdle()
        return vm
    }

    @Test
    fun theDueChip_isLockedAndSaysSoWhileNothingIsDue() {
        show()

        rule.setupNode("quiz_scope_due_today").assertIsNotEnabled()
        rule.setupNode("quiz_due_note")
        rule.onNodeWithText("Nothing is due for review today").assertIsDisplayed()
    }

    @Test
    fun theDueChip_countsTheDueCountriesAndStartsAQuizAboutThem() {
        val due = smallWorld.take(2).map { it.code }
        dueFlags(due)
        val vm = showWithDue(2)

        clickSetup("quiz_scope_due_today")

        rule.onNodeWithTag("quiz_scope_due_today").assertIsSelected()
        rule.onNodeWithText("Due today · 2").assertIsDisplayed()
        rule.setupNode("quiz_due_note")
        rule.onNodeWithText("The 2 countries due for review, with answers from the whole world").assertIsDisplayed()
        rule.onNodeWithTag("start_quiz_btn").assertIsEnabled()
        rule.onNodeWithText("Start 2-question quiz").assertIsDisplayed()

        rule.onNodeWithTag("start_quiz_btn").performClick()
        rule.waitForIdle()

        val session = vm.quizSession.value!!
        assertEquals(QuizEngine.DUE, session.scope)
        assertEquals(due.toSet(), session.questions.map { it.targetCountry.code }.toSet())
        assertTrue(session.questions.all { it.options.size == 4 })
    }

    @Test
    fun theDueChip_isInactiveInTheContinentQuiz() {
        dueFlags(smallWorld.take(2).map { it.code })
        showWithDue(2)

        clickSetup("quiz_mode_continent")

        rule.setupNode("quiz_scope_due_today").assertIsNotEnabled()
        rule.onNodeWithTag("quiz_due_note").assertDoesNotExist()
    }

    @Test
    fun theScopesThatAreNotPlaces_followTheSkillOfTheMode() {
        // Four wrong flag answers at the dawn of time: weak, and long due.
        fourWeakSpots()
        showWithWeakSpots(4)
        rule.setupNode("quiz_scope_weak_spots").assertIsEnabled()
        rule.setupNode("quiz_scope_due_today").assertIsEnabled()

        clickSetup("quiz_mode_capital")

        rule.setupNode("quiz_scope_weak_spots").assertIsNotEnabled()
        rule.setupNode("quiz_scope_due_today").assertIsNotEnabled()
        rule.setupNode("quiz_weak_spots_note")
        rule.onNodeWithText("Weak spots unlocks once 4 reviewed capitals are below mastery (you have 0)").assertIsDisplayed()
        rule.setupNode("quiz_due_note")
        rule.onNodeWithText("Nothing is due for review today").assertIsDisplayed()

        clickSetup("quiz_mode_flag_name")

        rule.setupNode("quiz_scope_weak_spots").assertIsEnabled()
        rule.setupNode("quiz_scope_due_today").assertIsEnabled()
    }

    @Test
    fun theAnswersRow_startsOnTheRememberedDifficultyAndRemembersTheNextChoice() {
        val settings = InMemoryUserSettings(QuizDifficulty.HARD)
        val vm = newViewModel(settings)
        rule.setContent { WorldFlagsTheme { QuizScreen(vm) } }
        rule.waitForIdle()

        rule.setupNode("quiz_difficulty_hard").assertIsSelected()

        clickSetup("quiz_difficulty_normal")

        rule.onNodeWithTag("quiz_difficulty_normal").assertIsSelected()
        assertEquals(QuizDifficulty.NORMAL, settings.quizDifficulty)
    }
}
