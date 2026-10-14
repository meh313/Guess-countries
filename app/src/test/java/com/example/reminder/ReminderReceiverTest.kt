package com.example.reminder

import android.Manifest
import android.app.AlarmManager
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
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
import com.example.data.settings.PrefsUserSettings
import com.example.support.FreshDatabaseRule
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.TimeZone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager

/**
 * The receiver behind the reminder alarm and the system events after which the alarm has to be set again: the alarm
 * going off hands the run to WorkManager; a reboot, a time or time-zone change or an update plans the alarm again in
 * the zone the phone is in now, and only when the reminder is on.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ReminderReceiverTest {

    // The app database is a singleton the worker opens itself, so start every test with a fresh one.
    @get:Rule val freshDatabase = FreshDatabaseRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val berlin = TimeZone.getTimeZone("Europe/Berlin")
    private val tokyo = TimeZone.getTimeZone("Asia/Tokyo")
    private val berlinId = ZoneId.of("Europe/Berlin")

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int = 0, second: Int = 0): Long =
        ZonedDateTime.of(year, month, day, hour, minute, second, 0, berlinId).toInstant().toEpochMilli()

    /** The wall clock the scheduler and the worker see. Monday 5 October 2026, mid-afternoon. */
    private var wall = at(2026, 10, 5, 15)

    private lateinit var workManager: WorkManager
    private lateinit var scheduler: WorkManagerReminderScheduler
    private lateinit var zoneBefore: TimeZone

    private val receiver = ReminderReceiver()

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

        // The system events are re-planned by a scheduler built from the context alone, in the phone's own zone.
        zoneBefore = TimeZone.getDefault()
        TimeZone.setDefault(berlin)
    }

    @After
    fun tearDown() {
        TimeZone.setDefault(zoneBefore)
    }

    // --- helpers ----------------------------------------------------------------------------------------------------

    private val alarmManager: AlarmManager get() = context.getSystemService(AlarmManager::class.java)

    private fun alarms(): List<ShadowAlarmManager.ScheduledAlarm> = shadowOf(alarmManager).scheduledAlarms

    private fun settings() = PrefsUserSettings(context)

    private fun runs(): List<WorkInfo> = workManager.getWorkInfosForUniqueWork(WorkManagerReminderScheduler.WORK_NAME).get()

    private fun deliver(intent: Intent) = receiver.onReceive(context, intent)

    /** Delivers the alarm's broadcast at [now] and waits for the run it starts to end. */
    private fun fire(now: Long) {
        wall = now
        val earlier = runs().map { it.id }.toSet()
        deliver(WorkManagerReminderScheduler.fireIntent(context))
        val run = runs().single { it.id !in earlier }
        val deadline = System.currentTimeMillis() + 10_000
        while (workManager.getWorkInfoById(run.id).get()?.state != WorkInfo.State.SUCCEEDED) {
            check(System.currentTimeMillis() < deadline) { "the reminder work never finished: ${workManager.getWorkInfoById(run.id).get()}" }
            Thread.sleep(10)
        }
    }

    private fun notifications(): List<Notification> =
        shadowOf(context.getSystemService(NotificationManager::class.java)).allNotifications

    private fun wallClockOf(millis: Long, zone: TimeZone): LocalTime = Instant.ofEpochMilli(millis).atZone(zone.toZoneId()).toLocalTime()

    /** The alarm set from the real clock: at 19:00 on the wall clock of [zone], within the next day (25 hours when the clocks go back). */
    private fun assertPlannedAtSevenInTheEvening(zone: TimeZone, notBefore: Long) {
        val alarm = alarms().single()
        assertEquals(AlarmManager.RTC_WAKEUP, alarm.type)
        assertEquals(LocalTime.of(19, 0), wallClockOf(alarm.triggerAtMs, zone))
        assertTrue("planned in the past: ${alarm.triggerAtMs} <= $notBefore", alarm.triggerAtMs > notBefore)
        assertTrue("planned too far ahead", alarm.triggerAtMs - System.currentTimeMillis() <= Duration.ofHours(25).toMillis())
        assertEquals(alarm.triggerAtMs, settings().reminderDueAt)
    }

    // --- the alarm going off ----------------------------------------------------------------------------------------

    @Test
    fun fire_handsTheRunToWorkManager_whichPostsTheReminder_andSetsTomorrowsAlarm() {
        scheduler.schedule(19 * 60)

        fire(now = at(2026, 10, 5, 19, 0, 5))

        assertEquals(1, notifications().size)
        assertEquals("Time for a quick quiz", notifications().single().extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertEquals(WorkInfo.State.SUCCEEDED, runs().single().state)
        assertEquals(at(2026, 10, 6, 19), alarms().single().triggerAtMs)
    }

    @Test
    fun fire_tellsTheRunWhenItWasDue_soARunHoursLateIsDropped() {
        scheduler.schedule(19 * 60)

        // The alarm was due at 19:00; the phone only came back at 23:30.
        fire(now = at(2026, 10, 5, 23, 30))

        assertEquals(0, notifications().size)
        assertEquals(at(2026, 10, 6, 19), alarms().single().triggerAtMs)
    }

    @Test
    fun fire_withoutAStoredDueTime_treatsTheRunAsOnTime() {
        scheduler.schedule(19 * 60)
        settings().reminderDueAt = 0L

        fire(now = at(2026, 10, 5, 19))

        // Not dropped as decades late because the due time read as zero.
        assertEquals(1, notifications().size)
    }

    // --- the system events ------------------------------------------------------------------------------------------

    @Test
    fun eachSystemEvent_withTheReminderOn_setsTheAlarmAgain() {
        val events = listOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED
        )
        for (action in events) {
            scheduler.cancel()
            assertEquals(action, 0, alarms().size)
            val before = System.currentTimeMillis()

            deliver(Intent(action))

            assertPlannedAtSevenInTheEvening(berlin, notBefore = before)
        }
    }

    @Test
    fun bootCompleted_withTheReminderOff_plansNothing() {
        settings().reminderEnabled = false

        deliver(Intent(Intent.ACTION_BOOT_COMPLETED))

        assertEquals(emptyList<ShadowAlarmManager.ScheduledAlarm>(), alarms())
        assertEquals(0L, settings().reminderDueAt)
    }

    @Test
    fun aTimeZoneChange_movesTheAlarmToTheNewZonesSevenInTheEvening() {
        val before = System.currentTimeMillis()
        deliver(Intent(Intent.ACTION_TIMEZONE_CHANGED))
        assertPlannedAtSevenInTheEvening(berlin, notBefore = before)
        val inBerlin = alarms().single().triggerAtMs

        // The phone lands in Tokyo.
        TimeZone.setDefault(tokyo)
        deliver(Intent(Intent.ACTION_TIMEZONE_CHANGED))

        assertPlannedAtSevenInTheEvening(tokyo, notBefore = before)
        assertNotEquals(inBerlin, alarms().single().triggerAtMs)
        assertEquals(1, alarms().size)
    }

    @Test
    fun aTimeZoneChangeBroadcast_reachesTheReceiverDeclaredInTheManifest() {
        val before = System.currentTimeMillis()

        context.sendBroadcast(Intent(Intent.ACTION_TIMEZONE_CHANGED))
        // Broadcasts are delivered through the main looper.
        shadowOf(Looper.getMainLooper()).idle()

        assertPlannedAtSevenInTheEvening(berlin, notBefore = before)
    }

    @Test
    fun aBroadcastItDoesNotKnow_doesNothing() {
        deliver(Intent("com.example.reminder.SOMETHING_ELSE"))

        assertEquals(emptyList<ShadowAlarmManager.ScheduledAlarm>(), alarms())
        assertEquals(emptyList<WorkInfo>(), runs())
    }
}
