package com.example

import android.os.Bundle
import android.os.Looper
import androidx.lifecycle.SavedStateHandle
import com.example.data.local.AppDatabase
import com.example.data.model.CountryRepository
import com.example.flashcards.StudySession
import com.example.progress.Grade
import com.example.progress.Skill
import com.example.quiz.sovereign
import com.example.support.awaitPendingWrites
import com.example.support.inMemoryDatabase
import com.example.ui.viewmodel.StudySessionBundle
import com.example.ui.viewmodel.StudyStateHolder
import com.example.ui.viewmodel.WriteErrors
import java.util.TimeZone
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** The study session across process death: what [StudyStateHolder] puts into the saved state and takes back out. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class StudyStateHolderTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: CountryRepository
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val twelve = sovereign.map { it.code }.take(12)

    @Before
    fun setUp() {
        db = inMemoryDatabase()
        repository = CountryRepository(db.userProgressDao())
    }

    @After
    fun tearDown() {
        scope.cancel()
        shadowOf(Looper.getMainLooper()).idle()
        db.awaitPendingWrites()
        db.close()
    }

    private fun holder(savedState: SavedStateHandle?, repository: CountryRepository = this.repository) =
        StudyStateHolder(repository, scope, { TimeZone.getTimeZone("UTC") }, Random(7), WriteErrors(), savedState)

    /** What the activity would hand the next process: the handle's saved state, restored into a fresh handle. */
    private fun SavedStateHandle.saved(): SavedStateHandle {
        val state = savedStateProvider().saveState()
        assertTrue("the session is saved under its key", state.containsKey(StudyStateHolder.SAVED_STUDY))
        return SavedStateHandle.createHandle(state, null)
    }

    /** The skill rows once [expected] of them have landed (the writes hop between the main thread and Room's executor). */
    private fun skillRows(expected: Int): List<com.example.data.local.SkillProgressEntity> {
        val deadline = System.currentTimeMillis() + 10_000
        while (true) {
            db.awaitPendingWrites()
            val rows = runBlocking { db.userProgressDao().getAllSkills().first() }
            if (rows.size >= expected || System.currentTimeMillis() > deadline) return rows
            Thread.sleep(20)
        }
    }

    private fun StudyStateHolder.flipAndGrade(grade: Grade) {
        flip()
        grade(grade)
    }

    @Test
    fun theSessionInProgress_survivesTheProcessThroughTheSavedState() {
        val handle = SavedStateHandle()
        val holder = holder(handle)
        holder.start(StudySession.DUE, twelve)
        holder.flipAndGrade(Grade.GOOD)
        holder.flipAndGrade(Grade.AGAIN)
        holder.flipAndGrade(Grade.GOOD)
        holder.undo()
        val before = holder.session.value!!
        assertTrue("the undone card is on top, flipped", before.isFlipped)
        assertEquals(2, before.graded.size)
        assertNull(before.pending)

        val after = holder(handle.saved()).session.value

        assertNotNull(after)
        assertEquals(before, after)
        assertEquals(before.queue, after!!.queue)
        assertEquals(before.graded, after.graded)
        assertEquals(before.pending, after.pending)
        assertEquals(before.isFlipped, after.isFlipped)
        assertEquals(before.position, after.position)
        assertEquals(before.cards, after.cards)
        assertEquals(StudySession.DUE, after.source)
    }

    @Test
    fun aPendingGrade_andAnAgainCardInTheQueue_areSavedAndRestored() {
        val handle = SavedStateHandle()
        val holder = holder(handle)
        holder.start(StudySession.WEAK, twelve)
        holder.flipAndGrade(Grade.AGAIN)
        holder.flipAndGrade(Grade.EASY)
        val before = holder.session.value!!
        assertNotNull(before.pending)
        assertTrue("the Again card comes back", before.queue.contains(twelve[0]))

        val revived = holder(handle.saved())
        val after = revived.session.value!!

        assertEquals(before, after)
        assertEquals(before.pending, after.pending)
        assertEquals(before.seed, after.seed)
        // The restored session goes on exactly as the original would have: the seed places the next Again card.
        holder.flipAndGrade(Grade.AGAIN)
        revived.flipAndGrade(Grade.AGAIN)
        assertEquals(holder.session.value, revived.session.value)
    }

    @Test
    fun withNoSession_theSavedStateRestoresNone() {
        val handle = SavedStateHandle()
        val holder = holder(handle)
        assertNull(holder(handle.saved()).session.value)

        holder.start(StudySession.DECK, twelve)
        holder.dismiss()

        assertNull(holder(handle.saved()).session.value)
    }

    @Test
    fun aFinishedSession_isRestoredOnItsSummary() {
        val handle = SavedStateHandle()
        val holder = holder(handle)
        holder.start(StudySession.DECK, twelve.take(2))
        holder.flipAndGrade(Grade.GOOD)
        holder.flipAndGrade(Grade.EASY)
        assertTrue(holder.session.value!!.isOver)

        val after = holder(handle.saved()).session.value!!

        assertTrue(after.isOver)
        assertEquals(holder.session.value, after)
    }

    @Test
    fun aSavedSessionThisVersionDidNotWrite_isIgnored() {
        val missingFields = SavedStateHandle()
        missingFields[StudyStateHolder.SAVED_STUDY] = Bundle().apply { putString("source", StudySession.DUE) }
        assertNull(holder(missingFields).session.value)

        val repeatedCard = SavedStateHandle()
        repeatedCard[StudyStateHolder.SAVED_STUDY] = Bundle().apply {
            putString("source", StudySession.DUE)
            putStringArrayList("cards", arrayListOf("FR", "FR")) // not distinct
            putStringArrayList("queue", arrayListOf("FR"))
            putStringArrayList("gradedCodes", arrayListOf())
            putStringArrayList("gradedGrades", arrayListOf())
            putBooleanArray("gradedFirstTime", booleanArrayOf())
        }
        assertNull(holder(repeatedCard).session.value)
    }

    @Test
    fun theSkillOfASession_isSavedAndRestored_andAnOldBundleIsAFlagSession() {
        val handle = SavedStateHandle()
        val holder = holder(handle)
        holder.start(StudySession.CAPITALS_DUE, twelve, Skill.CAPITAL)
        holder.flipAndGrade(Grade.GOOD)
        val before = holder.session.value!!
        assertEquals(Skill.CAPITAL, before.skill)

        val after = holder(handle.saved()).session.value!!

        assertEquals(before, after)
        assertEquals(Skill.CAPITAL, after.skill)

        // A bundle written before sessions had a skill: a flag session.
        val old = SavedStateHandle()
        old[StudyStateHolder.SAVED_STUDY] = Bundle().apply {
            putString("source", StudySession.DUE)
            putStringArrayList("cards", arrayListOf("FR", "DE"))
            putStringArrayList("queue", arrayListOf("FR", "DE"))
            putStringArrayList("gradedCodes", arrayListOf())
            putStringArrayList("gradedGrades", arrayListOf())
            putBooleanArray("gradedFirstTime", booleanArrayOf())
        }
        val restored = holder(old).session.value
        assertNotNull(restored)
        assertEquals(Skill.FLAG, restored!!.skill)
        assertEquals(listOf("FR", "DE"), restored.cards)

        // A skill this version does not know is not restored.
        val unknown = SavedStateHandle()
        unknown[StudyStateHolder.SAVED_STUDY] = StudySessionBundle.write(before).apply { putString("skill", "HANDWRITING") }
        assertNull(holder(unknown).session.value)
    }

    @Test
    fun aCapitalSession_writesItsGradesToTheCapitalSkill() {
        val three = twelve.take(3)
        val holder = holder(SavedStateHandle())
        holder.start(StudySession.CAPITALS_DUE, three, Skill.CAPITAL)
        holder.flipAndGrade(Grade.GOOD)
        holder.flipAndGrade(Grade.AGAIN)
        holder.flipAndGrade(Grade.EASY)
        assertEquals(StudySession.CAPITALS_DUE, holder.session.value!!.source)
        // The Again card comes back: grade it so the session ends and every grade is written.
        holder.flipAndGrade(Grade.GOOD)
        assertTrue(holder.session.value!!.isOver)

        val rows = skillRows(expected = 3)

        assertEquals(three.toSet(), rows.map { it.countryCode }.toSet())
        assertEquals(3, rows.size)
        assertTrue("every row is a capital row: $rows", rows.all { it.skill == Skill.CAPITAL.name })
        assertTrue(rows.none { it.skill == Skill.FLAG.name })
    }

    @Test
    fun aSavedSessionAboutACountryNoLongerInTheCatalog_isDropped() {
        val handle = SavedStateHandle()
        holder(handle).start(StudySession.DECK, twelve)

        val smallerWorld = CountryRepository(db.userProgressDao(), sovereign.drop(12))

        assertNull(holder(handle.saved(), smallerWorld).session.value)
    }
}
