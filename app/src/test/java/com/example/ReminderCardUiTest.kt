package com.example

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.text.format.DateFormat
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.example.reminder.WorkManagerReminderScheduler
import com.example.support.FreshDatabaseRule
import com.example.support.TestWorkManagerRule
import com.example.support.eventually
import com.example.support.openTab
import com.example.ui.viewmodel.CountryViewModel
import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** The daily reminder card on the Progress screen, in the real activity with a WorkManager that only records. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w360dp-h800dp-xxhdpi")
class ReminderCardUiTest {

    private val composeRule = createAndroidComposeRule<MainActivity>()

    @get:Rule val chain: RuleChain =
        RuleChain.outerRule(FreshDatabaseRule()).around(TestWorkManagerRule()).around(composeRule)

    private val rule get() = composeRule
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val application get() = context as Application
    private val viewModel get() = ViewModelProvider(rule.activity)[CountryViewModel::class.java]

    private fun allowNotifications() = shadowOf(application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)

    private fun forbidNotifications() = shadowOf(application).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)

    private fun openProgress() {
        rule.openTab("stats")
        rule.onNodeWithTag("reminder_switch").performScrollTo()
    }

    private fun pendingReminders(): List<WorkInfo> =
        WorkManager.getInstance(context).getWorkInfosForUniqueWork(WorkManagerReminderScheduler.WORK_NAME).get()
            .filter { it.state == WorkInfo.State.ENQUEUED }

    /** The time as the card writes it: the way the device writes times. */
    private fun clockText(hour: Int, minute: Int): String =
        DateFormat.getTimeFormat(context).format(
            Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
            }.time
        )

    /** Answers the permission prompt the switch opened, as the system would. */
    private fun answerPermissionPrompt(granted: Boolean) {
        val request = shadowOf(rule.activity).lastRequestedPermission
        assertNotNull("the switch should have asked for the notification permission", request)
        assertEquals(listOf(Manifest.permission.POST_NOTIFICATIONS), request!!.requestedPermissions.toList())
        if (granted) allowNotifications() else forbidNotifications()
        rule.runOnUiThread {
            rule.activity.onRequestPermissionsResult(
                request.requestCode,
                request.requestedPermissions,
                intArrayOf(if (granted) PackageManager.PERMISSION_GRANTED else PackageManager.PERMISSION_DENIED)
            )
        }
        rule.waitForIdle()
    }

    @Test
    fun theReminderIsOffByDefault() {
        openProgress()

        rule.onNodeWithTag("stats_reminder").assertIsDisplayed()
        rule.onNodeWithTag("reminder_switch").assertIsOff()
        rule.onNodeWithTag("reminder_time_btn").assertDoesNotExist()
        rule.onNodeWithTag("reminder_permission_hint").assertDoesNotExist()
    }

    @Test
    fun switchingOn_whenNotificationsAreAllowed_showsTheTimeAndPlansTheReminder() {
        allowNotifications()
        openProgress()

        rule.onNodeWithTag("reminder_switch").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("reminder_switch").assertIsOn()
        rule.onNodeWithTag("reminder_time_btn").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("reminder_time_btn").assertTextContains("Remind me at ${clockText(19, 0)}")
        rule.eventually("a planned reminder") { assertEquals(1, pendingReminders().size) }
        assertEquals(true, viewModel.reminder.value.enabled)
    }

    @Test
    fun switchingOff_cancelsThePlannedReminder() {
        allowNotifications()
        openProgress()
        rule.onNodeWithTag("reminder_switch").performClick()
        rule.eventually("a planned reminder") { assertEquals(1, pendingReminders().size) }

        rule.onNodeWithTag("reminder_switch").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("reminder_switch").assertIsOff()
        rule.onNodeWithTag("reminder_time_btn").assertDoesNotExist()
        rule.eventually("no planned reminder") { assertEquals(0, pendingReminders().size) }
        assertEquals(false, viewModel.reminder.value.enabled)
    }

    @Test
    fun withoutThePermission_theSwitchAsksForIt_andStaysOffWhenItIsRefused() {
        forbidNotifications()
        openProgress()

        rule.onNodeWithTag("reminder_switch").performClick()
        rule.waitForIdle()
        answerPermissionPrompt(granted = false)

        rule.onNodeWithTag("reminder_switch").assertIsOff()
        rule.onNodeWithTag("reminder_permission_hint").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("reminder_settings_btn").assertIsDisplayed()
        assertEquals(false, viewModel.reminder.value.enabled)
        assertEquals(0, pendingReminders().size)
    }

    @Test
    fun withoutThePermission_theSwitchTurnsOnOnceItIsGranted() {
        forbidNotifications()
        openProgress()

        rule.onNodeWithTag("reminder_switch").performClick()
        rule.waitForIdle()
        answerPermissionPrompt(granted = true)

        rule.onNodeWithTag("reminder_switch").assertIsOn()
        rule.onNodeWithTag("reminder_permission_hint").assertDoesNotExist()
        rule.eventually("a planned reminder") { assertEquals(1, pendingReminders().size) }
    }

    @Test
    fun theSettingsButton_opensTheNotificationSettingsOfTheApp() {
        forbidNotifications()
        openProgress()
        rule.onNodeWithTag("reminder_switch").performClick()
        rule.waitForIdle()
        answerPermissionPrompt(granted = false)

        rule.onNodeWithTag("reminder_settings_btn").performScrollTo().performClick()

        val opened = shadowOf(rule.activity).nextStartedActivity
        assertNotNull(opened)
        assertEquals(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS, opened.action)
        assertEquals(context.packageName, opened.getStringExtra(android.provider.Settings.EXTRA_APP_PACKAGE))
    }

    @Test
    fun aReminderThatIsOn_butCannotBeShown_saysSo() {
        allowNotifications()
        viewModel.setReminderEnabled(true)
        forbidNotifications()

        // The card looks again whenever the screen comes back.
        rule.activityRule.scenario.recreate()
        rule.waitForIdle()
        openProgress()

        rule.onNodeWithTag("reminder_switch").assertIsOn()
        rule.onNodeWithTag("reminder_permission_hint").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun theTimeButton_opensThePickerAndCancelKeepsTheTime() {
        allowNotifications()
        viewModel.setReminderTime(hour = 8, minute = 30)
        viewModel.setReminderEnabled(true)
        openProgress()
        rule.onNodeWithTag("reminder_time_btn").assertTextContains("Remind me at ${clockText(8, 30)}")

        rule.onNodeWithTag("reminder_time_btn").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("reminder_time_cancel").assertIsDisplayed()
        rule.onNodeWithTag("reminder_time_cancel").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("reminder_time_cancel").assertDoesNotExist()
        assertEquals(8 * 60 + 30, viewModel.reminder.value.minuteOfDay)
    }

    @Test
    fun confirmingThePicker_keepsTheShownTime() {
        allowNotifications()
        viewModel.setReminderTime(hour = 8, minute = 30)
        viewModel.setReminderEnabled(true)
        openProgress()

        rule.onNodeWithTag("reminder_time_btn").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("reminder_time_confirm").performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("reminder_time_confirm").assertDoesNotExist()
        assertEquals(8 * 60 + 30, viewModel.reminder.value.minuteOfDay)
    }

    @Test
    fun typingATimeInThePicker_movesTheReminder() {
        allowNotifications()
        viewModel.setReminderTime(hour = 8, minute = 30)
        viewModel.setReminderEnabled(true)
        openProgress()

        rule.onNodeWithTag("reminder_time_btn").performClick()
        rule.waitForIdle()
        // The keyboard mode of the picker has a text field for the hour and one for the minutes. The toggle between the
        // two modes is the only control whose description ends in "for the time input".
        rule.onNode(hasContentDescription("for the time input", substring = true)).performClick()
        rule.waitForIdle()
        val fields = rule.onAllNodes(hasSetTextAction())
        fields[0].performTextReplacement("9")
        fields[1].performTextReplacement("45")
        rule.waitForIdle()
        rule.onNodeWithTag("reminder_time_confirm").performClick()
        rule.waitForIdle()

        assertEquals(9 * 60 + 45, viewModel.reminder.value.minuteOfDay)
        rule.onNodeWithTag("reminder_time_btn").assertTextContains("Remind me at ${clockText(9, 45)}")
        // The plan moved with it.
        rule.eventually("one planned reminder") { assertEquals(1, pendingReminders().size) }
    }
}
