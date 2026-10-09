package com.example

import com.example.data.model.CountryRepository
import com.example.data.settings.InMemoryUserSettings
import com.example.data.settings.ReminderPrefs
import com.example.reminder.ReminderScheduler
import com.example.support.FakeSpeech
import com.example.support.closeWhenIdle
import com.example.support.inMemoryDatabase
import com.example.ui.viewmodel.CountryViewModel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The reminder switch and time through the view model, with a scheduler that only writes down what it is told. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CountryViewModelReminderTest {

    private class RecordingScheduler : ReminderScheduler {
        val calls = mutableListOf<String>()

        override fun schedule(minuteOfDay: Int) {
            calls += "schedule($minuteOfDay)"
        }

        override fun cancel() {
            calls += "cancel"
        }
    }

    private lateinit var db: com.example.data.local.AppDatabase
    private val scheduler = RecordingScheduler()
    private val settings = InMemoryUserSettings()

    @Before
    fun setUp() {
        db = inMemoryDatabase()
    }

    @After
    fun tearDown() {
        db.closeWhenIdle()
    }

    private fun viewModel() =
        CountryViewModel(CountryRepository(db.userProgressDao()), FakeSpeech(), settings = settings, reminderScheduler = scheduler)

    @Test
    fun theReminderStartsOff_andNothingIsPlanned() {
        val vm = viewModel()

        assertEquals(ReminderPrefs(enabled = false, minuteOfDay = 19 * 60), vm.reminder.value)
        assertEquals(emptyList<String>(), scheduler.calls)
    }

    @Test
    fun switchingOn_plansTheReminderAtTheStoredTime() {
        val vm = viewModel()

        vm.setReminderEnabled(true)

        assertEquals(ReminderPrefs(enabled = true, minuteOfDay = 1_140), vm.reminder.value)
        assertEquals(listOf("schedule(1140)"), scheduler.calls)
        assertEquals(true, settings.reminderEnabled)
    }

    @Test
    fun switchingOff_cancelsThePlan() {
        val vm = viewModel()
        vm.setReminderEnabled(true)

        vm.setReminderEnabled(false)

        assertEquals(false, vm.reminder.value.enabled)
        assertEquals(listOf("schedule(1140)", "cancel"), scheduler.calls)
        assertEquals(false, settings.reminderEnabled)
    }

    @Test
    fun changingTheTimeWhileOn_plansTheReminderAgain() {
        val vm = viewModel()
        vm.setReminderEnabled(true)

        vm.setReminderTime(hour = 7, minute = 30)

        assertEquals(7 * 60 + 30, vm.reminder.value.minuteOfDay)
        assertEquals(7, vm.reminder.value.hour)
        assertEquals(30, vm.reminder.value.minute)
        assertEquals(listOf("schedule(1140)", "schedule(450)"), scheduler.calls)
        assertEquals(450, settings.reminderMinuteOfDay)
    }

    @Test
    fun changingTheTimeWhileOff_onlyStoresIt() {
        val vm = viewModel()

        vm.setReminderTime(hour = 6, minute = 0)
        assertEquals(emptyList<String>(), scheduler.calls)

        vm.setReminderEnabled(true)
        assertEquals(listOf("schedule(360)"), scheduler.calls)
    }

    @Test
    fun aTimeOutsideTheDay_isHeldToTheDay() {
        val vm = viewModel()

        vm.setReminderTime(hour = 30, minute = 0)

        assertEquals(24 * 60 - 1, vm.reminder.value.minuteOfDay)
    }

    @Test
    fun aReminderThatWasOn_isPlannedAgainWhenTheAppStarts() {
        settings.reminderEnabled = true
        settings.reminderMinuteOfDay = 21 * 60

        val vm = viewModel()

        assertEquals(ReminderPrefs(enabled = true, minuteOfDay = 1_260), vm.reminder.value)
        assertEquals(listOf("schedule(1260)"), scheduler.calls)
    }

    @Test
    fun theSettingsSurviveAnotherViewModel() {
        val first = viewModel()
        first.setReminderTime(hour = 8, minute = 15)
        first.setReminderEnabled(true)

        val second = viewModel()

        assertEquals(ReminderPrefs(enabled = true, minuteOfDay = 8 * 60 + 15), second.reminder.value)
    }
}
