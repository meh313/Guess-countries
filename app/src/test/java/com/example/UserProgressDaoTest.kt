package com.example

import com.example.data.local.AppDatabase
import com.example.data.local.Confusion
import com.example.data.local.QuizAnswerEntity
import com.example.data.local.QuizScoreEntity
import com.example.data.local.RecentTarget
import com.example.data.local.SkillProgressEntity
import com.example.data.local.UserProgressEntity
import com.example.data.model.CountryRepository
import com.example.progress.Evidence
import com.example.progress.Grade
import com.example.progress.PracticeStreak
import com.example.progress.Skill
import com.example.progress.SkillState
import com.example.support.inMemoryDatabase
import com.example.support.seedQuiz
import java.util.TimeZone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Progress writes go through real SQL on an in-memory Room database. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class UserProgressDaoTest {

    private lateinit var db: AppDatabase
    private val dao get() = db.userProgressDao()

    private val day = 86_400_000L

    @Before
    fun setUp() {
        db = inMemoryDatabase()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun row(code: String): UserProgressEntity? =
        runBlocking { dao.getAllProgress().first() }.firstOrNull { it.countryCode == code }

    private fun skill(code: String, skill: Skill = Skill.FLAG): SkillProgressEntity? =
        runBlocking { dao.getAllSkills().first() }.firstOrNull { it.countryCode == code && it.skill == skill.name }

    private fun answer(code: String, correct: Boolean, now: Long, skill: Skill = Skill.FLAG, evidence: Evidence = Evidence.FULL) =
        runBlocking { dao.recordAnswer(code, skill, correct, evidence, now, TimeZone.getDefault()) }

    // --- bookmarks ----------------------------------------------------------------------------------------------------

    @Test
    fun toggleFavorite_createsTheRowOnFirstUseAndFlipsItEachTime() {
        runBlocking { dao.toggleFavorite("FR", 100L) }
        assertTrue(row("FR")!!.isFavorite)
        assertNull("a bookmark is not a review", skill("FR"))

        runBlocking { dao.toggleFavorite("FR", 200L) }
        assertFalse(row("FR")!!.isFavorite)
    }

    @Test
    fun toggleFavorite_onlyTouchesThatCountry() {
        runBlocking {
            dao.toggleFavorite("FR", 1L)
            dao.toggleFavorite("DE", 1L)
            dao.toggleFavorite("FR", 2L)
        }

        assertFalse(row("FR")!!.isFavorite)
        assertTrue(row("DE")!!.isFavorite)
        assertNull(row("IT"))
    }

    // --- answers ------------------------------------------------------------------------------------------------------

    @Test
    fun theFirstRightAnswer_createsTheCountryRowAndAFlagRowInBoxOne() {
        val state = answer("FR", true, 10L)

        assertEquals(SkillState(score = 20, box = 1, dueAt = 10L + day, reviews = 1, correct = 1, lastResult = 1, lastReviewed = 10L), state)
        assertEquals(state, skill("FR")!!.toState())
        assertEquals(10L, row("FR")!!.lastReviewed)
        assertFalse(row("FR")!!.isFavorite)
    }

    @Test
    fun answersInTheSameSittingRaiseTheScoreButNotTheBox_answersOnDueDaysPromote() {
        answer("FR", true, 10L)
        answer("FR", true, 20L)
        assertEquals(40, skill("FR")!!.score)
        assertEquals(1, skill("FR")!!.box)

        answer("FR", true, 10L + day) // due: box 2
        answer("FR", true, 10L + 5 * day) // due: box 3
        val r = skill("FR")!!
        assertEquals(3, r.box)
        assertEquals(80, r.score)
        assertEquals(4, r.reviews)
        assertEquals(4, r.correct)
    }

    @Test
    fun aWrongAnswer_resetsTheBoxAndCountsTheReviewOnly() {
        answer("FR", true, 1L)
        answer("FR", true, 1L + day)
        answer("FR", false, 2L + day)

        val r = skill("FR")!!
        assertEquals(20, r.score)
        assertEquals(1, r.box)
        assertEquals(3, r.reviews)
        assertEquals(2, r.correct)
        assertEquals(-1, r.lastResult)
        assertEquals(2L + day, r.lastReviewed)
    }

    @Test
    fun theFlagAndTheCapitalAreKeptApart() {
        answer("FR", true, 1L, skill = Skill.FLAG)
        answer("FR", false, 2L, skill = Skill.CAPITAL)

        assertEquals(20, skill("FR", Skill.FLAG)!!.score)
        assertEquals(1, skill("FR", Skill.FLAG)!!.correct)
        assertEquals(0, skill("FR", Skill.CAPITAL)!!.score)
        assertEquals(1, skill("FR", Skill.CAPITAL)!!.reviews)
        assertEquals("the country row is shared", 2L, row("FR")!!.lastReviewed)
    }

    @Test
    fun lightEvidence_staysUnderTheCap() {
        repeat(12) { answer("FR", true, 1L + it * day, evidence = Evidence.LIGHT) }

        assertEquals(60, skill("FR")!!.score)
        assertEquals(1, skill("FR")!!.box)
    }

    @Test
    fun grades_goThroughTheSameRules() {
        runBlocking { dao.recordGrade("FR", Skill.FLAG, Grade.GOOD, 5L, TimeZone.getDefault()) }
        assertEquals(8, skill("FR")!!.score)

        runBlocking { dao.recordGrade("FR", Skill.FLAG, Grade.AGAIN, 6L, TimeZone.getDefault()) }
        val r = skill("FR")!!
        assertEquals(0, r.score)
        assertEquals(1, r.box)
        assertEquals(2, r.reviews)
        assertEquals(6L + day, r.dueAt)
    }

    @Test
    fun bookmarkAndAnswersDoNotOverwriteEachOther() {
        runBlocking { dao.toggleFavorite("FR", 1L) }
        answer("FR", true, 2L)
        answer("FR", true, 3L)
        assertTrue("an answer must not clear the bookmark", row("FR")!!.isFavorite)
        assertEquals(40, skill("FR")!!.score)

        runBlocking { dao.toggleFavorite("FR", 4L) }
        assertFalse(row("FR")!!.isFavorite)
        assertEquals("toggling must not reset the skill", 40, skill("FR")!!.score)
        assertEquals(2, skill("FR")!!.reviews)
    }

    @Test
    fun manyConcurrentAnswers_areAllCounted() {
        // Every answer is its own transaction on Room's single writer, so none is lost under this load.
        runBlocking {
            coroutineScope {
                (1..200).map { i -> async(Dispatchers.Default) { dao.recordAnswer("FR", Skill.FLAG, true, Evidence.FULL, i.toLong(), TimeZone.getDefault()) } }.awaitAll()
            }
        }

        val r = skill("FR")!!
        assertEquals(200, r.reviews)
        assertEquals(200, r.correct)
        assertEquals(100, r.score)
        assertEquals("all in one sitting: one promotion only", 1, r.box)
    }

    @Test
    fun concurrentBookmarkTapsAndAnswers_doNotLoseEachOther() {
        runBlocking {
            coroutineScope {
                val taps = (1..101).map { i -> async(Dispatchers.Default) { dao.toggleFavorite("FR", i.toLong()) } }
                val answers = (1..100).map { i -> async(Dispatchers.Default) { dao.recordAnswer("FR", Skill.FLAG, i % 2 == 0, Evidence.FULL, i.toLong(), TimeZone.getDefault()) } }
                (taps + answers).awaitAll()
            }
        }

        assertTrue("101 taps leave the bookmark on", row("FR")!!.isFavorite)
        assertEquals("every answer is counted", 100, skill("FR")!!.reviews)
        assertEquals(50, skill("FR")!!.correct)
    }

    @Test
    fun upsertSkill_seedsAStateAsItIs() {
        val state = SkillState(score = 80, box = 3, dueAt = 7 * day, reviews = 3, correct = 3, lastResult = 1, lastReviewed = 6L)
        runBlocking { dao.upsertSkill(SkillProgressEntity.of("BR", Skill.FLAG, state)) }

        assertEquals(state, skill("BR")!!.toState())
        assertNull("seeding a skill does not create the country row", row("BR"))
    }

    // --- quizzes ------------------------------------------------------------------------------------------------------

    @Test
    fun repository_usesItsClockForAnswersAndQuizResults() {
        val repository = CountryRepository(dao, now = { 777L })

        runBlocking {
            repository.recordAnswer("FR", Skill.FLAG, true, Evidence.FULL, TimeZone.getDefault())
            repository.saveStudySession("Due today", 8, 10)
        }

        assertEquals(777L, skill("FR")!!.lastReviewed)
        assertEquals(777L, row("FR")!!.lastReviewed)
        assertEquals(777L, runBlocking { dao.getQuizHistory().first() }.single().timestamp)
    }

    @Test
    fun seedQuiz_writesARowWithoutAnswers_andReturnsTheEarlierTimes() {
        val first = runBlocking { dao.seedQuiz("FLAG_NAME", 120, 190, "Global", at = 100L) }
        val second = runBlocking { dao.seedQuiz("CAPITAL", 50, 190, "Asia", at = 200L) }

        assertEquals(emptyList<Long>(), first)
        assertEquals(listOf(100L), second)
        val quizzes = runBlocking { dao.getQuizHistory().first() }
        assertEquals(listOf(200L, 100L), quizzes.map { it.timestamp })
        assertTrue(runBlocking { dao.answersOf(quizzes.first().id) }.isEmpty())
    }

    @Test
    fun quizTimestamps_listsWhenEveryQuizWasSaved() {
        runBlocking {
            dao.insertQuizScore(QuizScoreEntity(mode = "FLAG_NAME", score = 10, total = 190, continentFilter = "Global", timestamp = 300L))
            dao.insertQuizScore(QuizScoreEntity(mode = "BLITZ", score = 50, total = 0, continentFilter = "Global", timestamp = 100L, correct = 4))
            dao.insertQuizScore(QuizScoreEntity(mode = "CAPITAL", score = 0, total = 190, continentFilter = "Asia", timestamp = 200L))
        }

        assertEquals(listOf(100L, 200L, 300L), runBlocking { dao.quizTimestamps() }.sorted())
    }

    @Test
    fun practiceSessions_carryTheSizeOfEachQuizOrStudySession() {
        runBlocking {
            dao.insertQuizScore(QuizScoreEntity(mode = "FLAG_NAME", score = 10, total = 190, continentFilter = "Global", timestamp = 100L, questionCount = 10))
            dao.insertQuizScore(QuizScoreEntity(mode = "BLITZ", score = 0, total = 0, continentFilter = "Global", timestamp = 200L, correct = 0, questionCount = 0))
            dao.insertQuizScore(QuizScoreEntity(mode = "FLASHCARDS", score = 0, total = 0, continentFilter = "Due today", timestamp = 300L, correct = 8, questionCount = 12))
            // A row from before the count was recorded falls back to its best possible score.
            dao.insertQuizScore(QuizScoreEntity(mode = "CAPITAL", score = 0, total = 190, continentFilter = "Asia", timestamp = 400L))
        }

        val sessions = runBlocking { dao.practiceSessions() }.map { it.toPracticeSession() }.sortedBy { it.at }
        assertEquals(
            listOf(PracticeStreak.Session(100L, 10), PracticeStreak.Session(200L, 0), PracticeStreak.Session(300L, 12), PracticeStreak.Session(400L, 190)),
            sessions
        )
    }

    @Test
    fun aBlitzRowMigratedWithoutItsCount_countsItsRightAnswers() {
        // What version 2.0 to 3.1 made of a 1.x blitz: its right answers in correct, nothing in questionCount.
        val migrated = QuizScoreEntity(mode = "BLITZ", score = 340, total = 0, continentFilter = "Global", timestamp = 100L, correct = 14)
        runBlocking {
            dao.insertQuizScore(migrated)
            // A blitz of today's version keeps its count, even 0; only a missing one falls back.
            dao.insertQuizScore(QuizScoreEntity(mode = "BLITZ", score = 0, total = 0, continentFilter = "Global", timestamp = 200L, correct = 0, questionCount = 0))
        }

        assertEquals(14, migrated.practiceItems)
        val sessions = runBlocking { dao.practiceSessions() }.map { it.toPracticeSession() }.sortedBy { it.at }
        assertEquals(listOf(PracticeStreak.Session(100L, 14), PracticeStreak.Session(200L, 0)), sessions)
        assertEquals(sessions, runBlocking { dao.getQuizHistory().first() }.map { it.toPracticeSession() }.sortedBy { it.at })
    }

    @Test
    fun quizTimestamps_isEmptyBeforeTheFirstQuiz() {
        assertTrue(runBlocking { dao.quizTimestamps() }.isEmpty())
    }

    private fun quiz(at: Long) = QuizScoreEntity(mode = "FLAG_NAME", score = 10, total = 190, continentFilter = "Global", timestamp = at)

    private fun answerRow(position: Int, target: String, picked: String?, correct: Boolean) =
        QuizAnswerEntity(quizId = 0, position = position, targetCode = target, pickedCode = picked, pickedText = picked, isCorrect = correct, timeMs = 1_500L)

    @Test
    fun insertQuizWithAnswers_returnsTheEarlierQuizzesAndSavesTheNewOne() {
        val first = runBlocking { dao.insertQuizWithAnswers(quiz(100L), emptyList()) }
        val second = runBlocking { dao.insertQuizWithAnswers(quiz(200L), emptyList()) }
        val third = runBlocking { dao.insertQuizScoreAfterReading(quiz(300L)) }

        assertEquals(emptyList<Long>(), first)
        assertEquals(listOf(100L), second)
        assertEquals(listOf(100L, 200L), third.sorted())
        assertEquals(listOf(100L, 200L, 300L), runBlocking { dao.quizTimestamps() }.sorted())
    }

    @Test
    fun insertQuizWithAnswers_storesTheAnswersUnderTheQuiz() {
        runBlocking { dao.insertQuizWithAnswers(quiz(100L), listOf(answerRow(0, "FR", "FR", true), answerRow(1, "DE", "AT", false), answerRow(2, "JP", null, false))) }

        val id = runBlocking { dao.getQuizHistory().first() }.single().id
        val answers = runBlocking { dao.answersOf(id) }
        assertEquals(listOf("FR", "DE", "JP"), answers.map { it.targetCode })
        assertEquals(listOf(true, false, false), answers.map { it.isCorrect })
        assertEquals("AT", answers[1].pickedCode)
        assertNull("a time-out picked nothing", answers[2].pickedCode)
        assertTrue(answers.all { it.quizId == id })
    }

    @Test
    fun recentTargets_listsTheTargetsOfTheQuizzesSinceAMoment_withTheirModeAndTime() {
        runBlocking {
            dao.insertQuizWithAnswers(quiz(100L), listOf(answerRow(0, "FR", "FR", true)))
            dao.insertQuizWithAnswers(quiz(200L).copy(mode = "CAPITAL"), listOf(answerRow(0, "DE", "DE", true)))
            dao.insertQuizWithAnswers(quiz(300L), listOf(answerRow(0, "JP", "JP", true), answerRow(1, "BR", "AR", false)))
            dao.insertQuizWithAnswers(QuizScoreEntity(mode = "FLASHCARDS", score = 0, total = 0, continentFilter = "Due today", timestamp = 400L), listOf(answerRow(0, "IT", null, false)))
        }

        val since200 = runBlocking { dao.recentTargets(200L).first() }
        assertEquals(setOf("DE", "JP", "BR"), since200.map { it.code }.toSet())
        assertEquals(RecentTarget("DE", "CAPITAL", 200L), since200.single { it.code == "DE" })
        assertEquals(RecentTarget("BR", "FLAG_NAME", 300L), since200.single { it.code == "BR" })
        assertEquals("study sessions do not count", setOf("FR", "DE", "JP", "BR"), runBlocking { dao.recentTargets(0L).first() }.map { it.code }.toSet())
        assertTrue(runBlocking { dao.recentTargets(301L).first() }.isEmpty())
    }

    @Test
    fun confusions_countBothDirectionsAsOnePair_mostOftenFirst() {
        runBlocking {
            dao.insertQuizWithAnswers(
                quiz(100L),
                listOf(
                    answerRow(0, "TD", "RO", false),
                    answerRow(1, "RO", "TD", false),
                    // Right answers and time-outs mix nothing up.
                    answerRow(2, "FR", "FR", true),
                    answerRow(3, "JP", null, false)
                )
            )
            dao.insertQuizWithAnswers(
                quiz(200L),
                listOf(answerRow(0, "RO", "TD", false), answerRow(1, "ID", "MC", false), answerRow(2, "AU", "NZ", false), answerRow(3, "NZ", "AU", false), answerRow(4, "ID", "MC", false))
            )
        }

        val pairs = runBlocking { dao.confusions().first() }

        // Chad for Romania and Romania for Chad are one pair; ties stand in code order.
        assertEquals(listOf(Confusion("RO", "TD", 3), Confusion("AU", "NZ", 2), Confusion("ID", "MC", 2)), pairs)
    }

    @Test
    fun confusions_isEmptyWithoutAWrongPick() {
        runBlocking { dao.insertQuizWithAnswers(quiz(100L), listOf(answerRow(0, "FR", "FR", true), answerRow(1, "DE", null, false))) }

        assertTrue(runBlocking { dao.confusions().first() }.isEmpty())
    }

    @Test
    fun reviewableQuizIds_andQuiz_findTheLogRowsThatHaveAnswers() {
        runBlocking {
            dao.insertQuizWithAnswers(quiz(100L), listOf(answerRow(0, "FR", "FR", true)))
            dao.insertQuizScoreAfterReading(quiz(200L))
        }

        val quizzes = runBlocking { dao.getQuizHistory().first() }
        val withAnswers = quizzes.single { it.timestamp == 100L }
        assertEquals(listOf(withAnswers.id), runBlocking { dao.reviewableQuizIds().first() })
        assertEquals(withAnswers, runBlocking { dao.quiz(withAnswers.id) })
        assertNull(runBlocking { dao.quiz(999L) })
    }

    @Test
    fun repository_review_pairsTheRowWithItsAnswers() {
        val repository = CountryRepository(dao)
        runBlocking { dao.insertQuizWithAnswers(quiz(100L), listOf(answerRow(0, "FR", "FR", true), answerRow(1, "DE", "AT", false))) }
        val id = runBlocking { dao.getQuizHistory().first() }.single().id

        val review = runBlocking { repository.review(id) }!!

        assertEquals(id, review.entry.id)
        assertEquals(listOf("FR", "DE"), review.answers.map { it.targetCode })
        assertNull(runBlocking { repository.review(id + 1) })
    }

    @Test
    fun deletingAQuiz_removesItsAnswers() {
        runBlocking { dao.insertQuizWithAnswers(quiz(100L), listOf(answerRow(0, "FR", "FR", true))) }
        val id = runBlocking { dao.getQuizHistory().first() }.single().id

        db.openHelper.writableDatabase.execSQL("DELETE FROM quiz_scores WHERE id = $id")

        assertTrue(runBlocking { dao.answersOf(id) }.isEmpty())
    }

    @Test
    fun clearAll_emptiesEveryTable_inOneGo() = runBlocking {
        answer("FR", true, 10L)
        answer("DE", false, 11L, skill = Skill.CAPITAL)
        dao.toggleFavorite("JP", 12L)
        dao.insertQuizWithAnswers(
            QuizScoreEntity(mode = "FLAG_NAME", score = 10, total = 190, continentFilter = "Global", timestamp = 20L),
            listOf(QuizAnswerEntity(quizId = 0, position = 0, targetCode = "FR", pickedCode = "FR", pickedText = "France", isCorrect = true, timeMs = 1L))
        )

        dao.clearAll()

        assertTrue(dao.getAllProgress().first().isEmpty())
        assertTrue(dao.getAllSkills().first().isEmpty())
        assertTrue(dao.getQuizHistory().first().isEmpty())
        assertTrue(dao.recentTargets(0L).first().isEmpty())
        // Life goes on afterwards.
        answer("FR", true, 30L)
        assertEquals(1, dao.getAllSkills().first().size)
    }

    @Test
    fun snapshotAndReplaceAll_carryEveryTableAcross_andReplaceWhatWasThere() = runBlocking {
        answer("FR", true, 10L)
        answer("DE", false, 11L, skill = Skill.CAPITAL)
        dao.toggleFavorite("JP", 12L)
        dao.insertQuizWithAnswers(
            QuizScoreEntity(mode = "FLAG_NAME", score = 10, total = 190, continentFilter = "Global", timestamp = 20L),
            listOf(QuizAnswerEntity(quizId = 0, position = 0, targetCode = "FR", pickedCode = "FR", pickedText = "France", isCorrect = true, timeMs = 1L))
        )
        val snapshot = dao.snapshot(exportedAt = 99L)
        assertEquals(3, snapshot.progress.size)
        assertEquals(2, snapshot.skills.size)
        assertEquals(1, snapshot.quizzes.size)
        assertEquals(1, snapshot.answers.size)
        assertEquals(snapshot.quizzes.single().id, snapshot.answers.single().quizId)
        assertEquals(99L, snapshot.exportedAt)

        // Something else happens, then the snapshot replaces it.
        dao.clearAll()
        answer("BR", true, 30L)
        dao.replaceAll(snapshot)

        assertEquals(snapshot.progress.toSet(), dao.getAllProgress().first().toSet())
        assertEquals(snapshot.skills.toSet(), dao.getAllSkills().first().toSet())
        // The quiz comes back with a new id (the file's ids only tie answers to quizzes), its answer re-pointed at it.
        val quizzes = dao.getQuizHistory().first()
        assertEquals(snapshot.quizzes.map { it.copy(id = 0) }, quizzes.map { it.copy(id = 0) })
        assertEquals(listOf("FR"), dao.recentTargets(0L).first().map { it.code })
        assertEquals(1, dao.answersOf(quizzes.single().id).size)
    }
}
