package com.example.reminder

import com.example.progress.PracticeStreak
import java.util.Calendar
import java.util.GregorianCalendar
import java.util.TimeZone

/** The pure rules of the daily reminder: when the next one is due and whether it should be sent at all. */
object ReminderSchedule {
    const val MINUTES_PER_DAY = 24 * 60

    /** 19:00, as minutes after local midnight. */
    const val DEFAULT_MINUTE_OF_DAY = 19 * 60

    /**
     * A reminder that comes later than this after its time is dropped. A phone that was off or stopped at 19:00 should not
     * nudge at 7 a.m. about last night; tonight's reminder is already planned.
     */
    const val MAX_LATENESS_MILLIS = 3 * 60 * 60 * 1000L

    data class Message(val title: String, val text: String)

    /**
     * The first moment after [after] at which the wall clock of [zone] shows [minuteOfDay] (minutes after midnight): today
     * if that is still ahead, else tomorrow. The wall-clock time is kept across daylight-saving changes, so a day can be
     * 23 or 25 hours long. A time the clocks skip over (02:30 on the day they go forward) lands an hour later.
     */
    fun nextTrigger(after: Long, minuteOfDay: Int, zone: TimeZone): Long {
        val minute = minuteOfDay.coerceIn(0, MINUTES_PER_DAY - 1)
        val calendar = GregorianCalendar(zone).apply { timeInMillis = after }
        fun setClock() {
            calendar.set(Calendar.HOUR_OF_DAY, minute / 60)
            calendar.set(Calendar.MINUTE, minute % 60)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
        }
        setClock()
        if (calendar.timeInMillis <= after) {
            calendar.add(Calendar.DAY_OF_YEAR, 1)
            setClock()
        }
        return calendar.timeInMillis
    }

    /** The reminder goes out only when it is switched on, notifications are allowed and no quiz was finished today. */
    fun shouldNotify(
        enabled: Boolean,
        notificationsAllowed: Boolean,
        quizTimestamps: Collection<Long>,
        now: Long,
        zone: TimeZone
    ): Boolean = enabled && notificationsAllowed && !PracticeStreak.summary(quizTimestamps, now, zone).practicedToday

    fun isTooLate(dueAt: Long, now: Long): Boolean = now - dueAt > MAX_LATENESS_MILLIS

    /** Words for a reminder sent while today has no quiz yet: [streak] is the run that ends yesterday (or 0). */
    fun message(streak: PracticeStreak.Summary): Message =
        if (streak.current > 0) {
            Message("Keep your streak", "One quiz keeps your ${streak.current}-day streak")
        } else {
            Message("Time for a quick quiz", "A quick quiz starts a new streak")
        }
}
