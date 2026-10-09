package com.example.reminder

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.TestDriver
import androidx.work.testing.WorkManagerTestInitHelper
import com.example.data.local.AppDatabase
import com.example.data.local.QuizScoreEntity
import com.example.data.settings.PrefsUserSettings
import com.example.support.FreshDatabaseRule
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.TimeZone
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The reminder chain against a real WorkManager in test mode and a clock the test moves by hand: planning, the run that
 * sends the notification, and the next run it plans. No sleeps and no real time.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ReminderWorkerTest {

    // The app database is a singleton the worker opens itself, so start every test with a fresh one.
    @get:Rule val freshDatabase = FreshDatabaseRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val berlin = TimeZone.getTimeZone("Europe/Berlin")
    private val berlinId = ZoneId.of("Europe/Berlin")

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int = 0, second: Int = 0): Long =
        ZonedDateTime.of(year, month, day, hour, minute, second, 0, berlinId).toInstant().toEpochMilli()

    /** The wall clock the scheduler and the worker see. Monday 5 October 2026, mid-afternoon. */
    private var wall = at(2026, 10, 5, 15)

    private lateinit var workManager: WorkManager
    private lateinit var driver: TestDriver
    private lateinit var scheduler: WorkManagerReminderScheduler

    @Before
    fun setUp() {
        val factory = object : WorkerFactory() {
            override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters): ListenableWorker? =
                if (workerClassName == ReminderWorker::class.java.name) {
                    ReminderWorker(appContext, workerParameters, now = { wall }, zone = { berlin })
                } else {
                    null
                }
        }
        val configuration = Configuration.Builder()
            .setMinimumLoggingLevel(Log.DEBUG)
            .setExecutor(SynchronousExecutor())
            .setWorkerFactory(factory)
            .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, configuration)
        workManager = WorkManager.getInstance(context)
        driver = WorkManagerTestInitHelper.getTestDriver(context)!!
        scheduler = WorkManagerReminderScheduler(context, now = { wall }, zone = { berlin })

        PrefsUserSettings(context).apply {
            reminderEnabled = true
            reminderMinuteOfDay = 19 * 60
        }
        shadowOf(context as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    }

    // --- helpers ----------------------------------------------------------------------------------------------------

    private fun chain(): List<WorkInfo> = workManager.getWorkInfosForUniqueWork(WorkManagerReminderScheduler.WORK_NAME).get()

    private fun pending(): List<WorkInfo> = chain().filter { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.BLOCKED }

    private fun plan(minuteOfDay: Int = 19 * 60): WorkInfo {
        scheduler.schedule(minuteOfDay)
        return pending().single()
    }

    /** Lets the planned work run now: moves the clock to [now], marks the delay as over and waits for the worker to end. */
    private fun runDueWork(info: WorkInfo, now: Long) {
        wall = now
        driver.setInitialDelayMet(info.id)
        val deadline = System.currentTimeMillis() + 10_000
        while (workManager.getWorkInfoById(info.id).get()?.state != WorkInfo.State.SUCCEEDED) {
            check(System.currentTimeMillis() < deadline) { "the reminder work never finished: ${workManager.getWorkInfoById(info.id).get()}" }
            Thread.sleep(10)
        }
    }

    private fun finishQuizAt(timestamp: Long) = runBlocking {
        AppDatabase.getDatabase(context).userProgressDao().insertQuizScore(
            QuizScoreEntity(mode = "FLAG_NAME", score = 10, total = 190, continentFilter = "Global", timestamp = timestamp)
        )
    }

    private fun notifications(): List<Notification> =
        shadowOf(context.getSystemService(NotificationManager::class.java)).allNotifications

    private fun Notification.title(): String = extras.getCharSequence(Notification.EXTRA_TITLE).toString()

    private fun Notification.text(): String = extras.getCharSequence(Notification.EXTRA_TEXT).toString()

    // --- planning ---------------------------------------------------------------------------------------------------

    @Test
    fun planning_waitsUntilTheNextTimeOfDay() {
        val info = plan()

        assertEquals(WorkInfo.State.ENQUEUED, info.state)
        // 15:00 to 19:00.
        assertEquals(Duration.ofHours(4).toMillis(), info.initialDelayMillis)
    }

    @Test
    fun planning_afterTheTimeHasPassed_waitsUntilTomorrow() {
        wall = at(2026, 10, 5, 20)

        assertEquals(Duration.ofHours(23).toMillis(), plan().initialDelayMillis)
    }

    @Test
    fun planningTwice_leavesOneRequest_andTheLastTimeWins() {
        plan(minuteOfDay = 19 * 60)
        val second = plan(minuteOfDay = 8 * 60)

        assertEquals(1, pending().size)
        assertEquals(second.id, pending().single().id)
        // 15:00 to 08:00 tomorrow.
        assertEquals(Duration.ofHours(17).toMillis(), second.initialDelayMillis)
    }

    @Test
    fun cancelling_stopsTheReminder() {
        plan()

        scheduler.cancel()

        assertEquals(emptyList<WorkInfo>(), pending())
        assertEquals(WorkInfo.State.CANCELLED, chain().single().state)
    }

    // --- the run ----------------------------------------------------------------------------------------------------

    @Test
    fun nothingPracticedToday_sendsOneReminderAndPlansTomorrow() {
        val info = plan()

        runDueWork(info, now = at(2026, 10, 5, 19, 0, 5))

        val sent = notifications()
        assertEquals(1, sent.size)
        assertEquals("Time for a quick quiz", sent.single().title())
        assertEquals("A quick quiz starts a new streak", sent.single().text())

        val next = pending().single()
        assertNotEquals(info.id, next.id)
        // 19:00:05 to 19:00 the next day.
        assertEquals(Duration.ofHours(24).minusSeconds(5).toMillis(), next.initialDelayMillis)
    }

    @Test
    fun aStreakAtRisk_isNamedInTheReminder() {
        finishQuizAt(at(2026, 10, 3, 9))
        finishQuizAt(at(2026, 10, 4, 9))
        val info = plan()

        runDueWork(info, now = at(2026, 10, 5, 19))

        assertEquals("Keep your streak", notifications().single().title())
        assertEquals("One quiz keeps your 2-day streak", notifications().single().text())
    }

    @Test
    fun aQuizFinishedToday_keepsTheReminderQuiet_butTomorrowIsStillPlanned() {
        finishQuizAt(at(2026, 10, 5, 10))
        val info = plan()

        runDueWork(info, now = at(2026, 10, 5, 19))

        assertEquals(0, notifications().size)
        assertEquals(1, pending().size)
    }

    @Test
    fun switchedOffWhileWaiting_endsTheChain() {
        val info = plan()
        PrefsUserSettings(context).reminderEnabled = false

        runDueWork(info, now = at(2026, 10, 5, 19))

        assertEquals(0, notifications().size)
        assertEquals(emptyList<WorkInfo>(), pending())
    }

    @Test
    fun withoutPermissionToNotify_nothingIsSent_butTomorrowIsPlanned() {
        shadowOf(context as Application).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val info = plan()

        runDueWork(info, now = at(2026, 10, 5, 19))

        assertEquals(0, notifications().size)
        assertEquals(1, pending().size)
    }

    @Test
    fun aRunMoreThanThreeHoursLate_isDropped_andPlansTheNextDay() {
        val info = plan()

        // The phone was off at 19:00 and woke at 23:30.
        runDueWork(info, now = at(2026, 10, 5, 23, 30))

        assertEquals(0, notifications().size)
        val next = pending().single()
        // 23:30 to 19:00 the next day.
        assertEquals(Duration.ofHours(19).plusMinutes(30).toMillis(), next.initialDelayMillis)
    }

    @Test
    fun aRunJustUnderThreeHoursLate_isStillSent() {
        val info = plan()

        runDueWork(info, now = at(2026, 10, 5, 21, 59))

        assertEquals(1, notifications().size)
    }

    @Test
    fun aRunASecondEarly_doesNotPlanTodayAgain() {
        val info = plan()

        runDueWork(info, now = at(2026, 10, 5, 18, 59, 59))

        // Planned from the due moment, not from the clock: tomorrow, not one second from now.
        assertEquals(Duration.ofHours(24).plusSeconds(1).toMillis(), pending().single().initialDelayMillis)
    }

    @Test
    fun theNextRunAfterDaylightSavingStarts_isStillAtTheSameWallClockTime() {
        wall = at(2026, 3, 28, 15)
        val info = plan()

        runDueWork(info, now = at(2026, 3, 28, 19, 0, 1))

        // 19:00:01 on the 28th to 19:00 on the 29th, which is only 23 hours away because the clocks went forward.
        assertEquals(Duration.ofHours(23).minusSeconds(1).toMillis(), pending().single().initialDelayMillis)
    }

    @Test
    fun theReminderKeepsComingEveryDay() {
        var info = plan()
        repeat(3) { day ->
            runDueWork(info, now = at(2026, 10, 5 + day, 19))
            info = pending().single()
        }

        // Every run posts under the same id, so one reminder is on screen however many days have passed.
        assertEquals(1, notifications().size)
        assertEquals(3, chain().count { it.state == WorkInfo.State.SUCCEEDED })
        assertEquals(1, pending().size)
    }

    @Test
    fun aRunRepeatedWhileTheNextIsWaiting_addsNoSecondOne() {
        val info = plan()
        runDueWork(info, now = at(2026, 10, 5, 19))
        assertEquals(1, pending().size)

        // The process died after planning and WorkManager ran the same work again: it sees the next one waiting.
        runBlocking { scheduler.scheduleNext(runningId = info.id, minuteOfDay = 19 * 60, after = wall) }

        assertEquals(1, pending().size)
    }
}
