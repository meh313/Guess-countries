package com.example.reminder

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R

/** Posts the daily reminder. */
object ReminderNotifier {
    const val CHANNEL_ID = "reminders"
    const val NOTIFICATION_ID = 1

    /** True when a notification would actually be shown: the permission (Android 13 and up) and the app-wide switch. */
    fun areNotificationsAllowed(context: Context): Boolean {
        val permitted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        return permitted && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    /**
     * Registers the channel. Called when the reminder is switched on, not only when the first notification is posted,
     * so the app's notification settings page already lists it while the player is looking at that page.
     */
    fun ensureChannel(context: Context) {
        NotificationManagerCompat.from(context).createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName("Daily reminder")
                .setDescription("A nudge to finish a quiz when you have not practiced today")
                .build()
        )
    }

    /**
     * Opens the app on the Quiz tab. SINGLE_TOP hands the intent to the activity that is already running, so a quiz in
     * progress survives the tap; without it CLEAR_TOP would finish that activity and its view model.
     */
    fun openQuizIntent(context: Context): Intent =
        Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(MainActivity.EXTRA_OPEN_TAB, MainActivity.TAB_QUIZ)

    // The permission is checked just above (areNotificationsAllowed), which lint cannot follow through the call.
    @SuppressLint("MissingPermission")
    fun show(context: Context, message: ReminderSchedule.Message) {
        if (!areNotificationsAllowed(context)) return
        val manager = NotificationManagerCompat.from(context)
        ensureChannel(context)
        val open = PendingIntent.getActivity(
            context,
            0,
            openQuizIntent(context),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_flag)
            .setContentTitle(message.title)
            .setContentText(message.text)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }
}
