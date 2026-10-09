package com.example.reminder

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.local.AppDatabase
import com.example.data.settings.PrefsUserSettings
import com.example.progress.PracticeStreak
import java.util.TimeZone

/**
 * The daily reminder: sends the notification when nothing was practiced today, then plans the next one. When the
 * reminder was switched off while this work waited, it ends the chain by doing nothing.
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
        if (!ReminderSchedule.isTooLate(dueAt, at)) {
            val timestamps = AppDatabase.getDatabase(applicationContext).userProgressDao().quizTimestamps()
            val allowed = ReminderNotifier.areNotificationsAllowed(applicationContext)
            if (ReminderSchedule.shouldNotify(enabled = true, allowed, timestamps, at, zone())) {
                ReminderNotifier.show(applicationContext, ReminderSchedule.message(PracticeStreak.summary(timestamps, at, zone())))
            }
        }
        // From the later of "now" and the planned moment, so a run a second early cannot plan today's time again.
        WorkManagerReminderScheduler(applicationContext, now, zone).scheduleNext(id, settings.reminderMinuteOfDay, maxOf(at, dueAt))
        return Result.success()
    }
}
