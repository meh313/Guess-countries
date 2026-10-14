package com.example.reminder

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.local.AppDatabase
import com.example.data.settings.PrefsUserSettings
import com.example.progress.PracticeStreak
import java.util.TimeZone

/**
 * The daily reminder's run, started by [ReminderReceiver] when the alarm fires: sends the notification when today's
 * goal is not met yet, then sets the next alarm. When the reminder was switched off while this run waited, it ends the
 * reminder by doing nothing.
 */
class ReminderWorker(
    context: Context,
    params: WorkerParameters,
    private val now: () -> Long,
    private val zone: () -> TimeZone
) : CoroutineWorker(context, params) {

    /** The constructor WorkManager's own factory looks for; tests build the worker with a fixed clock through the other. */
    constructor(context: Context, params: WorkerParameters) : this(context, params, System::currentTimeMillis, TimeZone::getDefault)

    override suspend fun doWork(): Result {
        val settings = PrefsUserSettings(applicationContext)
        if (!settings.reminderEnabled) return Result.success()

        val at = now()
        val dueAt = inputData.getLong(WorkManagerReminderScheduler.KEY_DUE_AT, at)
        try {
            if (!ReminderSchedule.isTooLate(dueAt, at)) notifyIfNothingPracticed(at)
        } catch (e: Exception) {
            // Tonight's reminder is lost, tomorrow's must not be: without the next alarm nothing comes until the app is opened.
            Log.w(TAG, "The reminder could not be shown", e)
        } finally {
            // From the later of "now" and the planned moment, so a run a second early cannot plan today's time again.
            WorkManagerReminderScheduler(applicationContext, now, zone).scheduleAfter(maxOf(at, dueAt), settings.reminderMinuteOfDay)
        }
        return Result.success()
    }

    /** The sessions in the log and the goal from the settings decide whether today still needs a nudge. */
    private suspend fun notifyIfNothingPracticed(at: Long) {
        val sessions = AppDatabase.getDatabase(applicationContext).userProgressDao().practiceSessions().map { it.toPracticeSession() }
        val goal = PrefsUserSettings(applicationContext).dailyGoal
        val allowed = ReminderNotifier.areNotificationsAllowed(applicationContext)
        if (ReminderSchedule.shouldNotify(enabled = true, allowed, sessions, goal, at, zone())) {
            ReminderNotifier.show(applicationContext, ReminderSchedule.message(PracticeStreak.summary(sessions, goal, at, zone())))
        }
    }

    private companion object {
        const val TAG = "ReminderWorker"
    }
}
