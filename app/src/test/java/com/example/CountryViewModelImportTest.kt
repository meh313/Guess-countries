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
import com.example.progress.Evidence
import com.example.progress.Skill
import com.example.quiz.QuizDifficulty
import com.example.quiz.QuizMode
import com.example.reminder.ReminderScheduler
import com.example.support.FakeSpeech
import com.example.support.closeWhenIdle
import com.example.support.inMemoryDatabase
import com.example.ui.viewmodel.CountryViewModel
import kotlinx.coroutines.flow.first
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

    private fun viewModel(database: AppDatabase = db) = CountryViewModel(
        CountryRepository(database.userProgressDao()),
        FakeSpeech(),
        settings = settings,
        reminderScheduler = scheduler,
        savedStateHandle = SavedStateHandle()
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
        db.userProgressDao().recordAnswer("JP", Skill.FLAG, false, Evidence.FULL, 2_000L)

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
        db.userProgressDao().recordAnswer("JP", Skill.FLAG, true, Evidence.FULL, 2_000L)
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
