package com.example

import android.os.Looper
import androidx.lifecycle.SavedStateHandle
import com.example.data.local.AppDatabase
import com.example.data.local.QuizScoreEntity
import com.example.data.model.CountryRepository
import com.example.data.settings.InMemoryUserSettings
import com.example.data.settings.ThemeMode
import com.example.data.transfer.ImportProblem
import com.example.data.transfer.ProgressTransfer
import com.example.flashcards.StudySession
import com.example.progress.DueForecast
import com.example.progress.Evidence
import com.example.progress.GoalHistory
import com.example.progress.Grade
import com.example.progress.Skill
import com.example.quiz.QuizDifficulty
import com.example.quiz.QuizMode
import com.example.reminder.ReminderScheduler
import com.example.support.FakeSpeech
import com.example.support.awaitPendingWrites
import com.example.support.closeWhenIdle
import com.example.support.inMemoryDatabase
import com.example.ui.viewmodel.CountryViewModel
import com.example.widget.WidgetRefresher
import java.util.TimeZone
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Import through the view model: what a good file does, and what a bad or refused one must not do. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CountryViewModelImportTest {

    private class RecordingScheduler : ReminderScheduler {
        val planned = mutableListOf<Int>()
        override fun schedule(minuteOfDay: Int) { planned += minuteOfDay }
        override fun cancel() = Unit
    }

    private lateinit var db: AppDatabase
    private val settings = InMemoryUserSettings()
    private val scheduler = RecordingScheduler()
    private val created = mutableListOf<CountryViewModel>()

    @Before
    fun setUp() {
        db = inMemoryDatabase()
    }

    @After
    fun tearDown() {
        if (db.isOpen) db.closeWhenIdle(*created.toTypedArray())
    }

    private fun viewModel(database: AppDatabase = db, widgets: WidgetRefresher = WidgetRefresher.NONE) = CountryViewModel(
        CountryRepository(database.userProgressDao()),
        FakeSpeech(),
        settings = settings,
        reminderScheduler = scheduler,
        savedStateHandle = SavedStateHandle(),
        widgets = widgets
    ).also { created += it }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    /** A file as the app writes it: one quiz with two answers, a bookmark, a flag row, dark theme and the reminder at 19:00. */
    private fun file(): String = JSONObject(
        """
        {"app":"World Flags","format":2,"exportedAt":1000,
         "progress":[{"code":"FR","favorite":true,"lastReviewed":10},{"code":"DE","favorite":false,"lastReviewed":20}],
         "skills":[{"code":"FR","skill":"FLAG","score":80,"box":3,"dueAt":500,"reviews":4,"correct":3,"lastResult":1,"lastReviewed":10}],
         "quizzes":[{"id":9223372036854775807,"mode":"FLAG_NAME","score":120,"total":190,"scope":"Europe","timestamp":30,"correct":8,"questionCount":10,"difficulty":"HARD"}],
         "answers":[{"quizId":9223372036854775807,"position":0,"target":"FR","pickedCode":"DE","pickedText":"Germany","correct":false,"timeMs":900},
                    {"quizId":9223372036854775807,"position":1,"target":"IT","pickedCode":null,"pickedText":null,"correct":false,"timeMs":10000}],
         "settings":{"themeMode":"DARK","reminderEnabled":"true","reminderMinuteOfDay":"1140"}}
        """
    ).toString()

    @Test
    fun aGoodFile_replacesTheDatabase_appliesTheSettings_andPlansTheReminder() = runBlocking {
        val vm = viewModel()
        db.userProgressDao().recordAnswer("JP", Skill.FLAG, false, Evidence.FULL, 2_000L, TimeZone.getDefault())

        val result = vm.importJson(file())

        assertTrue(result.isSuccess)
        assertEquals(listOf("FR"), db.userProgressDao().getAllSkills().first().map { it.countryCode })
        assertTrue(db.userProgressDao().getAllProgress().first().single { it.countryCode == "FR" }.isFavorite)
        assertEquals(ThemeMode.DARK, settings.themeMode)
        assertTrue(settings.reminderEnabled)
        assertEquals(1140, settings.reminderMinuteOfDay)
        assertEquals(listOf(1140), scheduler.planned)
        assertNull(vm.pendingImport.value)
    }

    @Test
    fun anImportDuringAStudySession_dropsItsPendingGrade_insteadOfWritingItIntoTheFile() = runBlocking {
        val vm = viewModel()
        // Mid-session: France graded, its grade pending.
        vm.startStudy(StudySession.DECK, listOf("FR", "JP"))
        vm.flipStudyCard()
        vm.gradeStudyCard(Grade.AGAIN)
        assertEquals("FR", vm.studySession.value?.pending?.code)

        vm.importJson(file()).getOrThrow()
        db.awaitPendingWrites()

        assertNull(vm.studySession.value)
        // The file's France, untouched by the Again: box 3, score 80, due at 500.
        val france = db.userProgressDao().getAllSkills().first().single()
        assertEquals(listOf("FR", "80", "3", "500"), listOf(france.countryCode, "${france.score}", "${france.box}", "${france.dueAt}"))

        // A finished session on its summary, long enough to log: neither its final grade nor its log row is written
        // over the file.
        vm.startStudy(StudySession.DECK, listOf("DE", "IT", "ES", "JP", "BR"))
        repeat(5) {
            vm.flipStudyCard()
            vm.gradeStudyCard(Grade.EASY)
        }
        assertTrue(vm.studySession.value!!.isOver)
        db.awaitPendingWrites()
        vm.importJson(file()).getOrThrow()
        db.awaitPendingWrites()
        assertEquals(listOf("FR"), db.userProgressDao().getAllSkills().first().map { it.countryCode })
        assertEquals(listOf("FLAG_NAME"), db.userProgressDao().getQuizHistory().first().map { it.mode })
    }

    @Test
    fun importedQuizzes_getNewIds_soLaterQuizzesStillSave() = runBlocking {
        val vm = viewModel()

        vm.importJson(file()).getOrThrow()

        val quizzes = db.userProgressDao().getQuizHistory().first()
        assertEquals(1, quizzes.size)
        assertTrue("the file's id must not be kept: ${quizzes[0].id}", quizzes[0].id < 1_000_000L)
        val answers = db.userProgressDao().answersOf(quizzes[0].id)
        assertEquals(listOf("FR", "IT"), answers.map { it.targetCode })
        // The sequence was not exhausted by Long.MAX_VALUE: the next quiz saves.
        db.userProgressDao().insertQuizWithAnswers(QuizScoreEntity(mode = "BLITZ", score = 10, total = 0, continentFilter = "Global", timestamp = 50L), emptyList())
        assertEquals(2, db.userProgressDao().getQuizHistory().first().size)
    }

    @Test
    fun aRefusedFile_changesNothing_andTheQuizGoesOn() = runBlocking {
        val vm = viewModel()
        db.userProgressDao().recordAnswer("JP", Skill.FLAG, true, Evidence.FULL, 2_000L, TimeZone.getDefault())
        vm.startQuiz(QuizMode.FLAG_NAME, "Europe", QuizDifficulty.NORMAL)
        assertNotNull(vm.quizSession.value)

        val result = vm.importJson("""{"app":"World Flags","format":2,"progress":[{"code":"france","favorite":false,"lastReviewed":0}],"skills":[],"quizzes":[],"answers":[]}""")

        assertTrue(result.exceptionOrNull() is ImportProblem.Damaged)
        assertNotNull("the quiz must not be dropped by a refused import", vm.quizSession.value)
        assertEquals(listOf("JP"), db.userProgressDao().getAllSkills().first().map { it.countryCode })
        assertNull(vm.pendingImport.value)
    }

    @Test
    fun aPreparedImport_waitsForTheConfirm_andCanBeCancelled() = runBlocking {
        val vm = viewModel()

        val prepared = vm.prepareImport(file()).getOrThrow()

        assertEquals(1, prepared.quizzes.size)
        assertEquals(prepared, vm.pendingImport.value)
        assertTrue(db.userProgressDao().getAllSkills().first().isEmpty())
        vm.cancelImport()
        assertNull(vm.pendingImport.value)
        assertTrue(vm.applyImport().isFailure)
    }

    @Test
    fun aWriteThatFails_comesBackAsAFailure_andTheQuizGoesOn() = runBlocking {
        val own = inMemoryDatabase()
        val vm = viewModel(own)
        vm.startQuiz(QuizMode.FLAG_NAME, "Europe", QuizDifficulty.NORMAL)
        vm.prepareImport(file()).getOrThrow()
        own.close()

        val result = vm.applyImport()

        assertTrue(result.isFailure)
        assertFalse(result.exceptionOrNull() is ImportProblem)
        assertNotNull("the quiz is dropped only after the database took the file", vm.quizSession.value)
        assertEquals(ThemeMode.SYSTEM, settings.themeMode)
        idle()
    }

    @Test
    fun aResetWhoseWriteFails_isReportedAsWriteError_insteadOfCrashing() {
        val own = inMemoryDatabase()
        val vm = viewModel(own)
        assertFalse(vm.writeError.value)
        own.close()

        vm.resetProgress()

        awaitCondition("the failed clear to be reported") { vm.writeError.value }
        vm.clearWriteError()
        assertFalse(vm.writeError.value)
    }

    private fun awaitCondition(what: String, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 10_000
        while (!condition()) {
            check(System.currentTimeMillis() < deadline) { "never happened: $what" }
            idle()
            Thread.sleep(25)
        }
    }

    @Test
    fun theFeedbackSettings_travelWithTheExport_andAnImportAppliesThem() = runBlocking {
        settings.soundEffects = false
        settings.haptics = true
        settings.language = "fr"
        val vm = viewModel()

        val json = vm.exportJson()
        val back = ProgressTransfer.fromJson(json).getOrThrow()
        assertEquals("false", back.settings["soundEffects"])
        assertEquals("true", back.settings["haptics"])
        assertEquals("fr", back.settings["language"])

        // Switched back on, then the file comes in: its values win.
        vm.setSoundEffects(true)
        vm.setHaptics(true)
        vm.setLanguage("")
        vm.importJson(json).getOrThrow()
        assertEquals(false, settings.soundEffects)
        assertEquals(true, settings.haptics)
        assertEquals("fr", settings.language)
        assertEquals("fr", vm.language.value)
        // A file naming a language the app lacks falls back to the system's.
        vm.importJson(json.replace("\"language\":\"fr\"", "\"language\":\"xx\"")).getOrThrow()
        assertEquals("", settings.language)
        assertEquals(com.example.data.settings.Feedback(sound = false, haptics = true), vm.feedback.value)
    }

    /** Waits until the view model's streak, collected meanwhile, reaches [current] days. */
    private fun CountryViewModel.awaitStreak(current: Int) {
        val collector = CoroutineScope(Dispatchers.Default).launch { streak.collect {} }
        try {
            awaitCondition("a streak of $current, not ${streak.value.current}") { streak.value.current == current }
        } finally {
            collector.cancel()
        }
    }

    /** A five-question quiz at noon [daysAgo] days before today: enough for a goal of 5, not for 10 or 20. */
    private fun fiveItems(daysAgo: Int) = runBlocking {
        val noon = DueForecast.startOfDay(System.currentTimeMillis(), TimeZone.getDefault(), -daysAgo) + 12 * 3_600_000L
        db.userProgressDao().insertQuizScore(QuizScoreEntity(mode = "FLAG_NAME", score = 50, total = 95, continentFilter = "Global", timestamp = noon, correct = 5, questionCount = 5))
    }

    @Test
    fun raisingTheDailyGoal_keepsTheDaysAlreadyPlayed_andTheChangeIsKept() {
        settings.dailyGoal = 5
        (1..3).forEach(::fiveItems)
        val vm = viewModel()
        vm.awaitStreak(3)

        vm.setDailyGoal(10)

        assertEquals(10, settings.dailyGoal)
        assertEquals(listOf(5), GoalHistory.of(10, settings.goalChanges).earlier.map { it.goal })
        // Not re-judged against 10: the three days stand, here and in a view model that starts from the settings.
        vm.awaitStreak(3)
        viewModel().awaitStreak(3)
        // The same goal again records nothing.
        val kept = settings.goalChanges
        vm.setDailyGoal(10)
        assertEquals(kept, settings.goalChanges)
    }

    @Test
    fun theGoalHistory_travelsWithTheExport_andAnImportRestoresItAsItWas() = runBlocking {
        settings.dailyGoal = 5
        val vm = viewModel()
        vm.setDailyGoal(20)
        vm.setDailyGoal(10)
        val exported = settings.goalChanges
        assertEquals(2, GoalHistory.of(10, exported).earlier.size)

        val json = vm.exportJson()
        val back = ProgressTransfer.fromJson(json).getOrThrow()
        assertEquals("10", back.settings["dailyGoal"])
        assertEquals(exported, back.settings["goalHistory"])

        // Changed on this phone since; the file brings its own goals back without counting the import as a change.
        vm.setDailyGoal(5)
        vm.importJson(json).getOrThrow()
        assertEquals(10, settings.dailyGoal)
        assertEquals(10, vm.dailyGoal.value)
        assertEquals(exported, settings.goalChanges)
    }

    @Test
    fun aFileFromBeforeTheGoalHistory_judgesAllItsDaysByItsGoal() = runBlocking {
        settings.dailyGoal = 5
        val vm = viewModel()
        vm.setDailyGoal(20)
        assertTrue(settings.goalChanges.isNotEmpty())

        vm.importJson(file().replace("\"themeMode\":\"DARK\"", "\"themeMode\":\"DARK\",\"dailyGoal\":\"10\"")).getOrThrow()

        assertEquals(10, settings.dailyGoal)
        assertEquals("", settings.goalChanges)
        // A file with no goal at all leaves the phone's as it is.
        vm.setDailyGoal(20)
        val kept = settings.goalChanges
        vm.importJson(file()).getOrThrow()
        assertEquals(20, settings.dailyGoal)
        assertEquals(kept, settings.goalChanges)
    }

    @Test
    fun theWidgetIsRedrawn_afterAResetAnImportAndAChangeOfGoalOrLanguage() = runBlocking {
        // What each redraw would show: the number of skill rows at that moment.
        val redraws = java.util.Collections.synchronizedList(mutableListOf<Int>())
        val vm = viewModel(widgets = WidgetRefresher { redraws += db.userProgressDao().getAllSkills().first().size })
        db.userProgressDao().recordAnswer("JP", Skill.FLAG, true, Evidence.FULL, 2_000L, TimeZone.getDefault())

        vm.setDailyGoal(20)
        awaitCondition("a redraw for the goal") { redraws.size == 1 }
        vm.setLanguage("de")
        awaitCondition("a redraw for the language") { redraws.size == 2 }
        assertEquals(listOf(1, 1), redraws.toList())

        vm.resetProgress()
        awaitCondition("a redraw after the reset") { redraws.size == 3 }
        assertEquals("drawn from the emptied database", 0, redraws.last())

        vm.importJson(file()).getOrThrow()
        awaitCondition("a redraw after the import") { redraws.size >= 4 }
        assertEquals("drawn from the imported rows", 1, redraws.last())
    }

    @Test
    fun aWidgetThatCannotBeRedrawn_failsNothing() = runBlocking {
        val vm = viewModel(widgets = WidgetRefresher { error("no widget host") })

        assertTrue(vm.importJson(file()).isSuccess)
        vm.setDailyGoal(5)
        vm.setLanguage("fr")
        vm.resetProgress()
        idle()
        db.awaitPendingWrites()

        assertEquals(5, settings.dailyGoal)
        assertFalse("the reset itself went through", vm.writeError.value)
        assertTrue(db.userProgressDao().getAllSkills().first().isEmpty())
    }

    @Test
    fun theExport_isCompactAndReadsBack() = runBlocking {
        val vm = viewModel()
        db.userProgressDao().toggleFavorite("FR", 1_000L)

        val json = vm.exportJson()

        assertFalse(json.contains("\n"))
        assertTrue(json.length < 2_000)
        val back = ProgressTransfer.fromJson(json).getOrThrow()
        assertEquals(listOf("FR"), back.progress.map { it.countryCode })
    }
}
