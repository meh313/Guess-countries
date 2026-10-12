package com.example.data.local

import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [UserProgressEntity::class, SkillProgressEntity::class, QuizScoreEntity::class, QuizAnswerEntity::class],
    version = 2,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userProgressDao(): UserProgressDao

    companion object {
        const val NAME = "world_flags_db"

        /**
         * Version 1 kept one mastery score per country (+25 right, -10 wrong, 75 = mastered) fed by every quiz mode and
         * by flashcard self-grades. Version 2 keeps a Leitner state per country and skill, records every quiz answer,
         * and stores what a quiz row's numbers mean.
         *
         * Every reviewed country becomes a FLAG row: the old reviews cannot be split by skill, so capitals start fresh.
         * The score carries over, the box follows the old thresholds (100 -> 3, 75 -> 2, else 1) and the next review is
         * due from the upgrade, so nothing lapses on day one. Blitz rows kept their right-answer count in `total`; it
         * moves to `correct`. A fixed-length row's question count is the inverse of QuizEngine.maxScore.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `skill_progress` (`countryCode` TEXT NOT NULL, `skill` TEXT NOT NULL, " +
                        "`score` INTEGER NOT NULL, `box` INTEGER NOT NULL, `dueAt` INTEGER NOT NULL, `reviews` INTEGER NOT NULL, " +
                        "`correct` INTEGER NOT NULL, `lastResult` INTEGER NOT NULL, `lastReviewed` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`countryCode`, `skill`))"
                )
                db.execSQL(
                    "INSERT INTO skill_progress (countryCode, skill, score, box, dueAt, reviews, correct, lastResult, lastReviewed) " +
                        "SELECT countryCode, 'FLAG', masteryScore, " +
                        "CASE WHEN masteryScore >= 100 THEN 3 WHEN masteryScore >= 75 THEN 2 ELSE 1 END, " +
                        "CAST(strftime('%s','now') AS INTEGER) * 1000 + " +
                        "CASE WHEN masteryScore >= 100 THEN 604800000 WHEN masteryScore >= 75 THEN 259200000 ELSE 86400000 END, " +
                        "timesReviewed, timesCorrect, 0, lastReviewed " +
                        "FROM user_country_progress WHERE timesReviewed > 0"
                )
                // SQLite on Android 7 has no DROP COLUMN: rebuild the country table without the old counters.
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `user_country_progress_new` (`countryCode` TEXT NOT NULL, " +
                        "`isFavorite` INTEGER NOT NULL, `lastReviewed` INTEGER NOT NULL, PRIMARY KEY(`countryCode`))"
                )
                db.execSQL("INSERT INTO user_country_progress_new SELECT countryCode, isFavorite, lastReviewed FROM user_country_progress")
                db.execSQL("DROP TABLE user_country_progress")
                db.execSQL("ALTER TABLE user_country_progress_new RENAME TO user_country_progress")

                db.execSQL("ALTER TABLE quiz_scores ADD COLUMN correct INTEGER")
                db.execSQL("ALTER TABLE quiz_scores ADD COLUMN questionCount INTEGER")
                db.execSQL("ALTER TABLE quiz_scores ADD COLUMN difficulty TEXT")
                db.execSQL("UPDATE quiz_scores SET correct = total, total = 0 WHERE mode = 'BLITZ'")
                db.execSQL(
                    "UPDATE quiz_scores SET questionCount = CASE total WHEN 52 THEN 4 WHEN 70 THEN 5 WHEN 90 THEN 6 " +
                        "WHEN 112 THEN 7 WHEN 136 THEN 8 WHEN 162 THEN 9 WHEN 190 THEN 10 END WHERE mode != 'BLITZ'"
                )

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `quiz_answers` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`quizId` INTEGER NOT NULL, `position` INTEGER NOT NULL, `targetCode` TEXT NOT NULL, `pickedCode` TEXT, " +
                        "`pickedText` TEXT, `isCorrect` INTEGER NOT NULL, `timeMs` INTEGER NOT NULL, " +
                        "FOREIGN KEY(`quizId`) REFERENCES `quiz_scores`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_quiz_answers_quizId` ON `quiz_answers` (`quizId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_quiz_answers_targetCode` ON `quiz_answers` (`targetCode`)")
            }
        }

        @Volatile
        private var INSTANCE: AppDatabase? = null

        /** Closes and forgets the shared instance so each test starts from a fresh database. */
        @VisibleForTesting
        fun closeAndReset() {
            synchronized(this) {
                INSTANCE?.close()
                INSTANCE = null
            }
        }

        fun getDatabase(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    NAME
                ).addMigrations(MIGRATION_1_2).build().also { INSTANCE = it }
            }
    }
}
