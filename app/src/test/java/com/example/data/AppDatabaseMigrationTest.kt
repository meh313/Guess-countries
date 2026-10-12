package com.example.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.progress.Skill
import com.example.support.inMemoryDatabase
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Upgrading a version 1 database (v1.9 and earlier) to version 2. The old database is built from the committed
 * schema file, so the fixture is the real one, and opened through Room, whose own validation throws on any table
 * that does not match what the entities declare.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AppDatabaseMigrationTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val name = "migration_test.db"
    private var migrated: AppDatabase? = null

    private val day = 86_400_000L

    @Before
    fun freshFile() {
        context.deleteDatabase(name)
    }

    @After
    fun close() {
        migrated?.close()
        context.deleteDatabase(name)
    }

    /** Creates the version 1 database exactly as Room 1 did, from app/schemas, and seeds it. */
    private fun createVersion1() {
        val schema = JSONObject(File("schemas/com.example.data.local.AppDatabase/1.json").readText()).getJSONObject("database")
        assertEquals(1, schema.getInt("version"))
        val db = SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name), null)
        try {
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val e = entities.getJSONObject(i)
                db.execSQL(e.getString("createSql").replace("\${TABLE_NAME}", e.getString("tableName")))
            }
            val setup = schema.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) db.execSQL(setup.getString(i))
            db.version = 1

            fun progress(code: String, mastery: Int, reviewed: Int, correct: Int, favorite: Int, last: Long) =
                db.execSQL(
                    "INSERT INTO user_country_progress (countryCode, isFavorite, masteryScore, timesReviewed, timesCorrect, lastReviewed) " +
                        "VALUES ('$code', $favorite, $mastery, $reviewed, $correct, $last)"
                )
            progress("FR", 100, 5, 4, 0, 1_000L)
            progress("DE", 75, 3, 3, 1, 2_000L)
            progress("JP", 25, 2, 1, 0, 3_000L)
            progress("IT", 0, 1, 0, 0, 4_000L)
            progress("BR", 0, 0, 0, 1, 5_000L) // a bookmark alone, never reviewed

            fun quiz(mode: String, score: Int, total: Int, scope: String, at: Long) =
                db.execSQL("INSERT INTO quiz_scores (mode, score, total, continentFilter, timestamp) VALUES ('$mode', $score, $total, '$scope', $at)")
            quiz("BLITZ", 340, 14, "Global", 10_000L)
            quiz("FLAG_NAME", 120, 190, "Global", 20_000L)
            quiz("CAPITAL", 30, 90, "Africa", 30_000L)
        } finally {
            db.close()
        }
    }

    private fun openMigrated(): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(AppDatabase.MIGRATION_1_2)
            .allowMainThreadQueries()
            .build()
            .also { migrated = it }

    @Test
    fun reviewedCountriesBecomeFlagRows_withTheOldScoreAndABoxFromTheOldThresholds() {
        createVersion1()
        // SQLite's strftime counts whole seconds, so the due dates can sit up to a second before the test's own clock.
        val start = System.currentTimeMillis() - 1_000

        val db = openMigrated()
        db.openHelper.writableDatabase // runs the migration and Room's schema validation
        val end = System.currentTimeMillis()

        val rows = runBlocking { db.userProgressDao().getAllSkills().first() }.associateBy { it.countryCode }
        assertEquals(setOf("FR", "DE", "JP", "IT"), rows.keys)
        assertTrue(rows.values.all { it.skill == Skill.FLAG.name })

        val fr = rows.getValue("FR")
        assertEquals(100, fr.score)
        assertEquals(3, fr.box)
        assertEquals(5, fr.reviews)
        assertEquals(4, fr.correct)
        assertEquals(0, fr.lastResult)
        assertEquals(1_000L, fr.lastReviewed)
        assertTrue("due a week after the upgrade", fr.dueAt in (start + 7 * day)..(end + 7 * day))

        assertEquals(2, rows.getValue("DE").box)
        assertTrue(rows.getValue("DE").dueAt in (start + 3 * day)..(end + 3 * day))
        assertEquals(1, rows.getValue("JP").box)
        assertEquals(1, rows.getValue("IT").box)
        assertTrue(rows.getValue("IT").dueAt in (start + day)..(end + day))
    }

    @Test
    fun bookmarksAndLastReviewedSurvive_andTheOldCountersAreGone() {
        createVersion1()
        val db = openMigrated()

        val progress = runBlocking { db.userProgressDao().getAllProgress().first() }.associateBy { it.countryCode }
        assertEquals(setOf("FR", "DE", "JP", "IT", "BR"), progress.keys)
        assertTrue(progress.getValue("BR").isFavorite)
        assertTrue(progress.getValue("DE").isFavorite)
        assertEquals(1_000L, progress.getValue("FR").lastReviewed)

        val columns = db.openHelper.readableDatabase.query("PRAGMA table_info(user_country_progress)").use { c ->
            generateSequence { if (c.moveToNext()) c.getString(c.getColumnIndexOrThrow("name")) else null }.toList()
        }
        assertEquals(listOf("countryCode", "isFavorite", "lastReviewed"), columns)
    }

    @Test
    fun quizRows_keepTheirMeaning_andBlitzMovesItsRightAnswersToCorrect() {
        createVersion1()
        val db = openMigrated()

        val quizzes = runBlocking { db.userProgressDao().getQuizHistory().first() }.associateBy { it.mode }
        val blitz = quizzes.getValue("BLITZ")
        assertEquals(14, blitz.correct)
        assertEquals(0, blitz.total)
        assertNull(blitz.questionCount)
        assertNull(blitz.difficulty)

        val flag = quizzes.getValue("FLAG_NAME")
        assertEquals(190, flag.total)
        assertEquals(10, flag.questionCount)
        assertNull(flag.correct)

        assertEquals(6, quizzes.getValue("CAPITAL").questionCount)
        assertEquals(90, quizzes.getValue("CAPITAL").total)
    }

    @Test
    fun theMigratedDatabase_hasTheSameTablesAsAFreshOne_andOpensAgainWithoutMigrating() {
        createVersion1()
        val db = openMigrated()
        val fresh = inMemoryDatabase()

        fun tableInfo(database: AppDatabase, table: String) =
            database.openHelper.readableDatabase.query("PRAGMA table_info($table)").use { c ->
                generateSequence {
                    if (c.moveToNext()) {
                        listOf("name", "type", "notnull", "pk").joinToString("|") { c.getString(c.getColumnIndexOrThrow(it)) }
                    } else {
                        null
                    }
                }.toList()
            }
        for (table in listOf("user_country_progress", "skill_progress", "quiz_scores", "quiz_answers")) {
            assertEquals(table, tableInfo(fresh, table), tableInfo(db, table))
        }
        fresh.close()

        assertEquals(2, db.openHelper.readableDatabase.version)
        db.close()
        val again = openMigrated()
        assertEquals(4, runBlocking { again.userProgressDao().getAllSkills().first() }.size)
    }

    @Test
    fun anEmptyVersion1Database_migratesToEmptyTables() {
        val schema = JSONObject(File("schemas/com.example.data.local.AppDatabase/1.json").readText()).getJSONObject("database")
        val raw = SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name), null)
        val entities = schema.getJSONArray("entities")
        for (i in 0 until entities.length()) {
            val e = entities.getJSONObject(i)
            raw.execSQL(e.getString("createSql").replace("\${TABLE_NAME}", e.getString("tableName")))
        }
        val setup = schema.getJSONArray("setupQueries")
        for (i in 0 until setup.length()) raw.execSQL(setup.getString(i))
        raw.version = 1
        raw.close()

        val db = openMigrated()
        assertTrue(runBlocking { db.userProgressDao().getAllSkills().first() }.isEmpty())
        assertTrue(runBlocking { db.userProgressDao().getAllProgress().first() }.isEmpty())
    }
}
