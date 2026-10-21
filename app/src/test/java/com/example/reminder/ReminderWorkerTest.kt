package com.example.reminder

import android.Manifest
import android.app.AlarmManager
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Looper
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.example.MainActivity
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
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager

/**
 * The reminder against a shadow AlarmManager, a real WorkManager in test mode and a clock the test moves by hand:
 * planning the alarm, the alarm going off (its broadcast reaches [ReminderReceiver], which hands the run to WorkManager),
 * the run that sends the notification, and the next alarm it sets. No sleeps and no real time. Runs on the oldest
 * supported Android (no channels, no permission), on 13 (the permission arrives) and on the current one.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24, 33, 36])
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
        scheduler = WorkManagerReminderScheduler(context, now = { wall }, zone = { berlin })

        PrefsUserSettings(context).apply {
            reminderEnabled = true
            reminderMinuteOfDay = 19 * 60
        }
        shadowOf(context as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    }

    // --- helpers ----------------------------------------------------------------------------------------------------

    private val alarmManager: AlarmManager get() = context.getSystemService(AlarmManager::class.java)

    /** The reminder's alarms only: WorkManager sets one of its own (the force-stop detector) on older Android versions. */
    private fun alarms(): List<ShadowAlarmManager.ScheduledAlarm> =
        shadowOf(alarmManager).scheduledAlarms.filter { shadowOf(it.operation).savedIntent?.action == ReminderReceiver.ACTION_FIRE }

    /** The one alarm that is set, which is the next reminder. */
    private fun nextAlarm(): ShadowAlarmManager.ScheduledAlarm = alarms().single()

    private fun plan(minuteOfDay: Int = 19 * 60): ShadowAlarmManager.ScheduledAlarm {
        scheduler.schedule(minuteOfDay)
        return nextAlarm()
    }

    private fun runs(): List<WorkInfo> = workManager.getWorkInfosForUniqueWork(WorkManagerReminderScheduler.WORK_NAME).get()

    /**
     * The system fires [alarm] at [now]: its broadcast reaches the receiver declared in the manifest, which hands the run
     * to WorkManager. Waits for that run to end.
     */
    private fun fire(alarm: ShadowAlarmManager.ScheduledAlarm, now: Long) {
        wall = now
        val earlier = runs().map { it.id }.toSet()
        shadowOf(alarmManager).fireAlarm(alarm)
        // Broadcasts are delivered through the main looper.
        shadowOf(Looper.getMainLooper()).idle()
        val run = runs().single { it.id !in earlier }
        val deadline = System.currentTimeMillis() + 10_000
        while (workManager.getWorkInfoById(run.id).get()?.state != WorkInfo.State.SUCCEEDED) {
            check(System.currentTimeMillis() < deadline) { "the reminder work never finished: ${workManager.getWorkInfoById(run.id).get()}" }
            Thread.sleep(10)
        }
    }

    /** A finished quiz of [questions] questions at [timestamp]; a row from before the count was recorded when null. */
    private fun finishQuizAt(timestamp: Long, questions: Int? = null) = runBlocking {
        AppDatabase.getDatabase(context).userProgressDao().insertQuizScore(
            QuizScoreEntity(mode = "FLAG_NAME", score = 10, total = 190, continentFilter = "Global", timestamp = timestamp, questionCount = questions)
        )
    }

    private fun notifications(): List<Notification> =
        shadowOf(context.getSystemService(NotificationManager::class.java)).allNotifications

    private fun Notification.title(): String = extras.getCharSequence(Notification.EXTRA_TITLE).toString()

    private fun Notification.text(): String = extras.getCharSequence(Notification.EXTRA_TEXT).toString()

    // --- planning ---------------------------------------------------------------------------------------------------

    @Test
    fun planning_setsOneWakingAlarmAtTheNextTimeOfDay_andRemembersIt() {
        val alarm = plan()

        assertEquals(at(2026, 10, 5, 19), alarm.triggerAtMs)
        assertEquals(AlarmManager.RTC_WAKEUP, alarm.type)
        // Allowed while idle: Doze would otherwise hold it back for hours, past the point where the reminder is dropped.
        assertTrue(alarm.isAllowWhileIdle)
        assertEquals(at(2026, 10, 5, 19), PrefsUserSettings(context).reminderDueAt)
    }

    @Test
    fun theAlarm_wakesTheReceiverWithTheFireAction() {
        val operation = shadowOf(plan().operation)

        assertTrue(operation.isBroadcastIntent)
        assertEquals(WorkManagerReminderScheduler.ALARM_REQUEST_CODE, operation.requestCode)
        assertEquals(ReminderReceiver.ACTION_FIRE, operation.savedIntent.action)
        assertEquals(ReminderReceiver::class.java.name, operation.savedIntent.component?.className)
    }

    @Test
    fun planning_afterTheTimeHasPassed_isTomorrow() {
        wall = at(2026, 10, 5, 20)

        assertEquals(at(2026, 10, 6, 19), plan().triggerAtMs)
    }

    @Test
    fun planningTwice_leavesOneAlarm_andTheLastTimeWins() {
        plan(minuteOfDay = 19 * 60)
        plan(minuteOfDay = 8 * 60)

        assertEquals(1, alarms().size)
        // 15:00 to 08:00 tomorrow.
        assertEquals(at(2026, 10, 6, 8), nextAlarm().triggerAtMs)
        assertEquals(at(2026, 10, 6, 8), PrefsUserSettings(context).reminderDueAt)
    }

    @Test
    fun cancelling_stopsTheReminder() {
        plan()

        scheduler.cancel()

        assertEquals(emptyList<ShadowAlarmManager.ScheduledAlarm>(), alarms())
        assertEquals(0L, PrefsUserSettings(context).reminderDueAt)
    }

    @Test
    fun switchingTheReminderOn_registersTheChannelBeforeTheFirstRun() {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
        val manager = context.getSystemService(NotificationManager::class.java)
        assertEquals(null, manager.getNotificationChannel(ReminderNotifier.CHANNEL_ID))

        plan()

        assertEquals("Daily reminder", manager.getNotificationChannel(ReminderNotifier.CHANNEL_ID).name)
    }

    // --- the run ----------------------------------------------------------------------------------------------------

    @Test
    fun nothingPracticedToday_sendsOneReminderAndPlansTomorrow() {
        val alarm = plan()

        fire(alarm, now = at(2026, 10, 5, 19, 0, 5))

        val sent = notifications()
        assertEquals(1, sent.size)
        assertEquals("Time for a quick quiz", sent.single().title())
        assertEquals("A quick quiz starts a new streak", sent.single().text())

        // 19:00 the next day, and the one alarm that is set.
        assertEquals(at(2026, 10, 6, 19), nextAlarm().triggerAtMs)
        assertEquals(at(2026, 10, 6, 19), PrefsUserSettings(context).reminderDueAt)
    }

    @Test
    fun theRun_isHandedToWorkManagerUnderTheReminderName() {
        fire(plan(), now = at(2026, 10, 5, 19))

        assertEquals(listOf(WorkInfo.State.SUCCEEDED), runs().map { it.state })
    }

    @Test
    fun theNotificationOpensTheQuizTab_withoutRestartingARunningApp() {
        fire(plan(), now = at(2026, 10, 5, 19))

        val opened = shadowOf(notifications().single().contentIntent).savedIntent
        assertEquals(MainActivity::class.java.name, opened.component?.className)
        assertEquals(MainActivity.TAB_QUIZ, opened.getStringExtra(MainActivity.EXTRA_OPEN_TAB))
        // CLEAR_TOP without SINGLE_TOP would finish the running activity, and a quiz in progress with it.
        assertTrue(opened.flags and Intent.FLAG_ACTIVITY_SINGLE_TOP != 0)
        assertTrue(opened.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    }

    @Test
    fun aFailureWhileNotifying_stillPlansTomorrow() {
        val alarm = plan()
        // The database cannot be read: every query throws.
        AppDatabase.getDatabase(context).close()

        fire(alarm, now = at(2026, 10, 5, 19))

        assertEquals(0, notifications().size)
        assertEquals(at(2026, 10, 6, 19), nextAlarm().triggerAtMs)
        assertEquals(WorkInfo.State.SUCCEEDED, runs().single().state)
    }

    @Test
    fun aStreakAtRisk_isNamedInTheReminder() {
        finishQuizAt(at(2026, 10, 3, 9))
        finishQuizAt(at(2026, 10, 4, 9))
        val alarm = plan()

        fire(alarm, now = at(2026, 10, 5, 19))

        assertEquals("Keep your streak", notifications().single().title())
        assertEquals("One quiz keeps your 2-day streak", notifications().single().text())
    }

    @Test
    fun aQuizFinishedToday_keepsTheReminderQuiet_butTomorrowIsStillPlanned() {
        finishQuizAt(at(2026, 10, 5, 10))
        val alarm = plan()

        fire(alarm, now = at(2026, 10, 5, 19))

        assertEquals(0, notifications().size)
        assertEquals(at(2026, 10, 6, 19), nextAlarm().triggerAtMs)
    }

    @Test
    fun aQuizTooShortForTheGoal_doesNotKeepTheReminderQuiet() {
        finishQuizAt(at(2026, 10, 5, 10), questions = 4)
        finishQuizAt(at(2026, 10, 5, 11), questions = 5)
        val alarm = plan()

        fire(alarm, now = at(2026, 10, 5, 19))

        assertEquals(1, notifications().size)
    }

    @Test
    fun theGoalFromTheSettings_decidesWhetherTodayCounts() {
        PrefsUserSettings(context).dailyGoal = 5
        finishQuizAt(at(2026, 10, 5, 11), questions = 5)
        val alarm = plan()

        fire(alarm, now = at(2026, 10, 5, 19))

        assertEquals(0, notifications().size)
    }

    @Test
    fun switchedOffWhileWaiting_sendsNothing_andSetsNoNextAlarm() {
        val alarm = plan()
        PrefsUserSettings(context).reminderEnabled = false

        fire(alarm, now = at(2026, 10, 5, 19))

        assertEquals(0, notifications().size)
        assertEquals(emptyList<ShadowAlarmManager.ScheduledAlarm>(), alarms())
    }

    @Test
    fun withoutPermissionToNotify_nothingIsSent_butTomorrowIsPlanned() {
        // Below Android 13 there is no notification permission to deny.
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        shadowOf(context as Application).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val alarm = plan()

        fire(alarm, now = at(2026, 10, 5, 19))

        assertEquals(0, notifications().size)
        assertEquals(at(2026, 10, 6, 19), nextAlarm().triggerAtMs)
    }

    @Test
    fun aRunMoreThanThreeHoursLate_isDropped_andPlansTheNextDay() {
        val alarm = plan()

        // The phone was off at 19:00 and woke at 23:30; the run learns the planned moment from the stored due time.
        fire(alarm, now = at(2026, 10, 5, 23, 30))

        assertEquals(0, notifications().size)
        assertEquals(at(2026, 10, 6, 19), nextAlarm().triggerAtMs)
    }

    @Test
    fun aRunJustUnderThreeHoursLate_isStillSent() {
        val alarm = plan()

        fire(alarm, now = at(2026, 10, 5, 21, 59))

        assertEquals(1, notifications().size)
    }

    @Test
    fun aRunASecondEarly_doesNotPlanTodayAgain() {
        val alarm = plan()

        fire(alarm, now = at(2026, 10, 5, 18, 59, 59))

        // Planned from the due moment, not from the clock: tomorrow, not one second from now.
        assertEquals(at(2026, 10, 6, 19), nextAlarm().triggerAtMs)
    }

    @Test
    fun theNextRunAfterDaylightSavingStarts_isStillAtTheSameWallClockTime() {
        wall = at(2026, 3, 28, 15)
        val alarm = plan()

        fire(alarm, now = at(2026, 3, 28, 19, 0, 1))

        // 19:00 on the 29th, which is only 23 hours away because the clocks went forward.
        assertEquals(at(2026, 3, 29, 19), nextAlarm().triggerAtMs)
        assertEquals(Duration.ofHours(23).minusSeconds(1).toMillis(), nextAlarm().triggerAtMs - wall)
    }

    @Test
    fun theReminderKeepsComingEveryDay() {
        var alarm = plan()
        repeat(3) { day ->
            fire(alarm, now = at(2026, 10, 5 + day, 19))
            alarm = nextAlarm()
        }

        // Every run posts under the same id, so one reminder is on screen however many days have passed.
        assertEquals(1, notifications().size)
        assertEquals(1, alarms().size)
        assertEquals(at(2026, 10, 8, 19), alarm.triggerAtMs)
    }

    @Test
    fun todaysReminderAlerts_evenWhileYesterdaysIsStillInTheShade() {
        fire(plan(), now = at(2026, 10, 5, 19))
        val yesterdays = notifications().single()

        // Neither tapped nor swiped away: the next day's run replaces it under the same id.
        fire(nextAlarm(), now = at(2026, 10, 6, 19))

        val todays = notifications().single()
        assertTrue("the second day posts a reminder of its own", todays !== yesterdays)
        // An alert-once notification replaced under its id makes no sound: each day's reminder must be heard.
        assertEquals(0, todays.flags and Notification.FLAG_ONLY_ALERT_ONCE)
        assertEquals(0, yesterdays.flags and Notification.FLAG_ONLY_ALERT_ONCE)
    }

    @Test
    fun planningAgainAfterARun_replacesTheAlarmTheRunSet() {
        fire(plan(), now = at(2026, 10, 5, 19))
        wall = at(2026, 10, 5, 20)

        plan(minuteOfDay = 8 * 60)

        assertEquals(1, alarms().size)
        assertEquals(at(2026, 10, 6, 8), nextAlarm().triggerAtMs)
    }
}
