package com.example.reminder

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.await
import androidx.work.workDataOf
import java.util.TimeZone
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first

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
 * One-time work with an initial delay to the next HH:MM, which the worker renews after each run. Not a periodic
 * request: its period drifts away from the wall clock, and a daily reminder has to stay at 19:00 through daylight saving.
 * The unique name `daily_reminder` keeps one plan at a time.
 */
class WorkManagerReminderScheduler(
    private val context: Context,
    private val now: () -> Long = System::currentTimeMillis,
    private val zone: () -> TimeZone = TimeZone::getDefault
) : ReminderScheduler {

    private val workManager get() = WorkManager.getInstance(context)

    /** Replaces the chain, so the first reminder is worked out again in the current time zone. */
    override fun schedule(minuteOfDay: Int) {
        workManager.enqueueUniqueWork(
            WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request(ReminderSchedule.nextTrigger(now(), minuteOfDay, zone()), now())
        )
    }

    override fun cancel() {
        workManager.cancelUniqueWork(WORK_NAME)
    }

    /**
     * Called by the worker that is running: plans the reminder after the one that just ran. Appended, never replaced,
     * because replacing from inside the running work would cancel that very work. Nothing is added when a next one is
     * already waiting, so a run repeated after the process died does not leave two.
     */
    suspend fun scheduleNext(runningId: UUID, minuteOfDay: Int, after: Long) {
        val waiting = workManager.getWorkInfosForUniqueWorkFlow(WORK_NAME).first()
            .any { it.id != runningId && (it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.BLOCKED) }
        if (waiting) return
        val due = ReminderSchedule.nextTrigger(after, minuteOfDay, zone())
        workManager.enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request(due, now())).await()
    }

    companion object {
        const val WORK_NAME = "daily_reminder"

        /** Input of a request: when it is meant to run, so a run far past that moment can tell. */
        const val KEY_DUE_AT = "due_at"

        private fun request(dueAt: Long, now: Long): OneTimeWorkRequest =
            OneTimeWorkRequest.Builder(ReminderWorker::class.java)
                .setInitialDelay((dueAt - now).coerceAtLeast(0), TimeUnit.MILLISECONDS)
                .setInputData(workDataOf(KEY_DUE_AT to dueAt))
                .build()
    }
}
