package com.example

import com.example.data.local.AppDatabase
import com.example.data.local.QuizScoreEntity
import com.example.data.local.UserProgressEntity
import com.example.data.model.CountryRepository
import com.example.support.inMemoryDatabase
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

    @Test
    fun toggleFavorite_createsTheRowOnFirstUseAndFlipsItEachTime() {
        runBlocking { dao.toggleFavorite("FR", 100L) }
        assertTrue(row("FR")!!.isFavorite)
        assertEquals(0, row("FR")!!.masteryScore)

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

    @Test
    fun correctReviews_add25PointsUpTo100AndCountEveryReview() {
        val seen = mutableListOf<Int>()
        repeat(5) {
            runBlocking { dao.recordReview("FR", true, 10L + it) }
            seen += row("FR")!!.masteryScore
        }

        assertEquals(listOf(25, 50, 75, 100, 100), seen)
        val r = row("FR")!!
        assertEquals(5, r.timesReviewed)
        assertEquals(5, r.timesCorrect)
        assertEquals(14L, r.lastReviewed)
    }

    @Test
    fun wrongReviews_remove10PointsDownTo0WithoutCountingAsCorrect() {
        runBlocking {
            dao.recordReview("FR", true, 1L) // 25
            dao.recordReview("FR", false, 2L) // 15
            dao.recordReview("FR", false, 3L) // 5
            dao.recordReview("FR", false, 4L) // 0, not -5
            dao.recordReview("FR", false, 5L) // still 0
        }

        val r = row("FR")!!
        assertEquals(0, r.masteryScore)
        assertEquals(5, r.timesReviewed)
        assertEquals(1, r.timesCorrect)
        assertEquals(5L, r.lastReviewed)
    }

    @Test
    fun bookmarkAndMasteryDoNotOverwriteEachOther() {
        runBlocking {
            dao.toggleFavorite("FR", 1L)
            dao.recordReview("FR", true, 2L)
            dao.recordReview("FR", true, 3L)
        }
        val afterReviews = row("FR")!!
        assertTrue("a review must not clear the bookmark", afterReviews.isFavorite)
        assertEquals(50, afterReviews.masteryScore)

        runBlocking { dao.toggleFavorite("FR", 4L) }
        val afterToggle = row("FR")!!
        assertFalse(afterToggle.isFavorite)
        assertEquals("toggling must not reset mastery", 50, afterToggle.masteryScore)
        assertEquals(2, afterToggle.timesReviewed)
    }

    @Test
    fun reviewingACountryNeverSeenBefore_startsFromAnEmptyRow() {
        runBlocking { dao.recordReview("JP", false, 9L) }

        val r = row("JP")!!
        assertFalse(r.isFavorite)
        assertEquals(0, r.masteryScore)
        assertEquals(1, r.timesReviewed)
    }

    @Test
    fun manyConcurrentReviews_areAllCounted() {
        // A read-modify-write from a stale snapshot loses updates under this load.
        runBlocking {
            coroutineScope {
                (1..200).map { i -> async(Dispatchers.Default) { dao.recordReview("FR", true, i.toLong()) } }.awaitAll()
            }
        }

        val r = row("FR")!!
        assertEquals(200, r.timesReviewed)
        assertEquals(200, r.timesCorrect)
        assertEquals(100, r.masteryScore)
    }

    @Test
    fun concurrentBookmarkTapsAndReviews_doNotLoseEachOther() {
        runBlocking {
            coroutineScope {
                val taps = (1..101).map { i -> async(Dispatchers.Default) { dao.toggleFavorite("FR", i.toLong()) } }
                val reviews = (1..100).map { i -> async(Dispatchers.Default) { dao.recordReview("FR", i % 2 == 0, i.toLong()) } }
                (taps + reviews).awaitAll()
            }
        }

        val r = row("FR")!!
        assertTrue("101 taps leave the bookmark on", r.isFavorite)
        assertEquals("every review is counted", 100, r.timesReviewed)
        assertEquals(50, r.timesCorrect)
    }

    @Test
    fun repository_usesItsClockForReviewsAndQuizResults() {
        val repository = CountryRepository(dao, now = { 777L })

        runBlocking {
            repository.recordReview("FR", true)
            repository.saveQuizScore("FLAG_NAME", 120, 190, "Global")
        }

        assertEquals(777L, row("FR")!!.lastReviewed)
        assertEquals(777L, runBlocking { dao.getQuizHistory().first() }.single().timestamp)
    }

    @Test
    fun quizTimestamps_listsWhenEveryQuizWasSaved() {
        runBlocking {
            dao.insertQuizScore(QuizScoreEntity(mode = "FLAG_NAME", score = 10, total = 190, continentFilter = "Global", timestamp = 300L))
            dao.insertQuizScore(QuizScoreEntity(mode = "BLITZ", score = 50, total = 4, continentFilter = "Global", timestamp = 100L))
            dao.insertQuizScore(QuizScoreEntity(mode = "CAPITAL", score = 0, total = 190, continentFilter = "Asia", timestamp = 200L))
        }

        assertEquals(listOf(100L, 200L, 300L), runBlocking { dao.quizTimestamps() }.sorted())
    }

    @Test
    fun quizTimestamps_isEmptyBeforeTheFirstQuiz() {
        assertTrue(runBlocking { dao.quizTimestamps() }.isEmpty())
    }

    @Test
    fun insertQuizScoreAfterReading_returnsTheEarlierQuizzesAndSavesTheNewOne() {
        fun quiz(at: Long) = QuizScoreEntity(mode = "FLAG_NAME", score = 10, total = 190, continentFilter = "Global", timestamp = at)

        val first = runBlocking { dao.insertQuizScoreAfterReading(quiz(100L)) }
        val second = runBlocking { dao.insertQuizScoreAfterReading(quiz(200L)) }
        val third = runBlocking { dao.insertQuizScoreAfterReading(quiz(300L)) }

        assertEquals(emptyList<Long>(), first)
        assertEquals(listOf(100L), second)
        assertEquals(listOf(100L, 200L), third.sorted())
        assertEquals(listOf(100L, 200L, 300L), runBlocking { dao.quizTimestamps() }.sorted())
    }
}
