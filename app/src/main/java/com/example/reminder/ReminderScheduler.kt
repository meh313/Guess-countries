package com.example.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.data.settings.PrefsUserSettings
import java.util.TimeZone

/** Plans and cancels the daily reminder. An interface so the view model can be tested without WorkManager. */
interface ReminderScheduler {
    /** Plans the reminder for [minuteOfDay] (minutes after local midnight), replacing any earlier plan. */
    fun schedule(minuteOfDay: Int)

    /** Cancels the plan; nothing more is sent. */
    fun cancel()
}

object NoReminderScheduler : ReminderScheduler {
    override fun schedule(minuteOfDay: Int) = Unit

    override fun cancel() = Unit
}

/**
 * An inexact alarm at the next HH:MM, which wakes the app even in Doze (`setAndAllowWhileIdle`, no permission needed),
 * and a WorkManager run that the alarm's broadcast starts ([ReminderReceiver]) to read the database and post the
 * notification off the main thread. The worker then sets the next alarm. Not a WorkManager delay: under Doze and App
 * Standby JobScheduler defers delayed work by hours, and a reminder that comes hours late is dropped, so the people
 * who rarely open the app (the ones the reminder is for) would never get it. Not a periodic request either: its period
 * drifts away from the wall clock, and a daily reminder has to stay at 19:00 through daylight saving.
 *
 * The planned moment is kept in [PrefsUserSettings.reminderDueAt], so the run can tell how late it is and a reboot or
 * time-zone change can plan again from the settings alone.
 */
class WorkManagerReminderScheduler(
    private val context: Context,
    private val now: () -> Long = System::currentTimeMillis,
    private val zone: () -> TimeZone = TimeZone::getDefault
) : ReminderScheduler {

    private val workManager: WorkManager get() = WorkManager.getInstance(context)
    private val alarmManager: AlarmManager get() = context.getSystemService(AlarmManager::class.java)

    /** Plans the first reminder after now in the current time zone, dropping any run the alarm already handed to WorkManager. */
    override fun schedule(minuteOfDay: Int) {
        // A run that is waiting carries the old time; it would notify at the old moment and plan from it.
        workManager.cancelUniqueWork(WORK_NAME)
        scheduleAfter(now(), minuteOfDay)
    }

    /**
     * Sets the alarm for the first [minuteOfDay] after [after] and stores that moment. The worker calls this from its
     * own run with the later of "now" and the moment it was due, so a run a second early cannot plan today's time again;
     * it leaves WorkManager alone, because cancelling the unique work from inside it would cancel that very run.
     */
    fun scheduleAfter(after: Long, minuteOfDay: Int) {
        // Register the channel now, so the system's notification settings for the app list it before the first run.
        ReminderNotifier.ensureChannel(context)
        val dueAt = ReminderSchedule.nextTrigger(after, minuteOfDay, zone())
        PrefsUserSettings(context).reminderDueAt = dueAt
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, dueAt, firePendingIntent(context))
    }

    override fun cancel() {
        alarmManager.cancel(firePendingIntent(context))
        PrefsUserSettings(context).reminderDueAt = 0L
        workManager.cancelUniqueWork(WORK_NAME)
    }

    /**
     * Called by [ReminderReceiver] when the alarm fires: hands the run to WorkManager with the moment it was planned
     * for. Expedited where that needs no foreground service (Android 12 and up), so it runs while the device is still
     * in Doze; plain work below that, and when the expedited quota is used up.
     */
    fun enqueueRun() {
        val dueAt = PrefsUserSettings(context).reminderDueAt.takeIf { it > 0L } ?: now()
        val request = OneTimeWorkRequest.Builder(ReminderWorker::class.java)
            .setInputData(workDataOf(KEY_DUE_AT to dueAt))
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            }
            .build()
        workManager.enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    companion object {
        const val WORK_NAME = "daily_reminder"

        /** Input of a run: when it was meant to run, so a run far past that moment can tell. */
        const val KEY_DUE_AT = "due_at"

        /** The request code of the alarm's PendingIntent; one code, so a new plan replaces the old alarm. */
        const val ALARM_REQUEST_CODE = 1

        /** The broadcast the alarm sends: [ReminderReceiver.ACTION_FIRE], addressed to the receiver by class. */
        fun fireIntent(context: Context): Intent =
            Intent(context, ReminderReceiver::class.java).setAction(ReminderReceiver.ACTION_FIRE)

        fun firePendingIntent(context: Context): PendingIntent =
            PendingIntent.getBroadcast(
                context,
                ALARM_REQUEST_CODE,
                fireIntent(context),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
    }
}
