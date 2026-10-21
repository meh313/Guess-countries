package com.example

import android.app.UiModeManager
import android.content.Context
import android.os.Build
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ApplicationProvider
import com.example.data.settings.PrefsUserSettings
import com.example.data.settings.ThemeMode
import com.example.support.FreshDatabaseRule
import com.example.support.SettledTodayRule
import com.example.ui.viewmodel.CountryViewModel
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The status and navigation bar icons against the app's own theme choice, which never reaches the system before
 * Android 12 and may not after a restore: dark icons over the light theme, light icons over the dark one, whatever
 * the system says. On the oldest supported Android, on 12L and on the current one.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24, 32, 36], qualifiers = "w360dp-h640dp-xxhdpi")
class SystemBarsTest {

    /** The theme in the settings before the activity starts, as a restored backup leaves it. */
    @Retention(AnnotationRetention.RUNTIME)
    @Target(AnnotationTarget.FUNCTION)
    annotation class StoredTheme(val mode: ThemeMode)

    private class StoredThemeRule : TestWatcher() {
        override fun starting(description: Description) {
            val mode = description.getAnnotation(StoredTheme::class.java)?.mode ?: return
            PrefsUserSettings(ApplicationProvider.getApplicationContext()).themeMode = mode
        }
    }

    private val composeRule = createAndroidComposeRule<MainActivity>()

    @get:Rule val chain: RuleChain =
        RuleChain.outerRule(FreshDatabaseRule()).around(SettledTodayRule()).around(StoredThemeRule()).around(composeRule)

    private val rule get() = composeRule

    private val viewModel get() = ViewModelProvider(rule.activity)[CountryViewModel::class.java]

    private val bars: WindowInsetsControllerCompat
        get() = rule.activity.window.let { WindowCompat.getInsetsController(it, it.decorView) }

    private fun choose(mode: ThemeMode) {
        rule.runOnUiThread { viewModel.setThemeMode(mode) }
        rule.waitForIdle()
    }

    /** Dark icons (for a light theme) or light ones (for a dark theme) on the status bar, and on the navigation bar where Android can (8.0 and newer). */
    private fun assertIconsFor(dark: Boolean) {
        assertEquals("status bar icons for a ${if (dark) "dark" else "light"} theme", !dark, bars.isAppearanceLightStatusBars)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            assertEquals("navigation bar icons for a ${if (dark) "dark" else "light"} theme", !dark, bars.isAppearanceLightNavigationBars)
        }
    }

    @Test
    fun onALightSystem_theDarkChoiceGetsLightIcons_andBack() {
        rule.waitForIdle()
        assertIconsFor(dark = false)

        choose(ThemeMode.DARK)
        assertIconsFor(dark = true)

        choose(ThemeMode.LIGHT)
        assertIconsFor(dark = false)
        choose(ThemeMode.SYSTEM)
        assertIconsFor(dark = false)
    }

    @Test
    @Config(qualifiers = "+night")
    fun onADarkSystem_theLightChoiceGetsDarkIcons() {
        rule.waitForIdle()
        assertIconsFor(dark = true)

        choose(ThemeMode.LIGHT)
        assertIconsFor(dark = false)
    }

    @Test
    @StoredTheme(ThemeMode.DARK)
    fun afterARestore_theStoredChoiceSetsTheBars_andReachesTheSystemAgain() {
        // The setting came back with the backup, the system's own copy did not (it is still "follow the system").
        rule.waitForIdle()
        assertIconsFor(dark = true)
        // From Android 12 the start hands the choice to the system again (before that there is nothing to hand over).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = ApplicationProvider.getApplicationContext<Context>().getSystemService(UiModeManager::class.java)
            assertEquals(UiModeManager.MODE_NIGHT_YES, shadowOf(manager).applicationNightMode)
        }
    }
}
