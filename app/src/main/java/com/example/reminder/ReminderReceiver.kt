package com.example.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.data.settings.PrefsUserSettings

/**
 * The daily reminder's broadcasts. [ACTION_FIRE] is the alarm going off: the run goes to WorkManager, which reads the
 * database and posts the notification off the main thread ([ReminderWorker]). After a reboot, a time or time-zone
 * change or an update of the app, the alarm is planned again in the current zone, from the settings alone, so a phone
 * that is never opened still gets its reminder at the chosen wall-clock time.
 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_FIRE -> WorkManagerReminderScheduler(context).enqueueRun()
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> replan(context)
        }
    }

    companion object {
        /** Sent by the alarm; nothing else sends it, and the receiver is not exported. */
        const val ACTION_FIRE = "com.example.reminder.FIRE"

        /** Plans the reminder again when it is on; a reminder that is off stays off. */
        fun replan(context: Context) {
            val settings = PrefsUserSettings(context)
            if (settings.reminderEnabled) WorkManagerReminderScheduler(context).schedule(settings.reminderMinuteOfDay)
        }
    }
}
