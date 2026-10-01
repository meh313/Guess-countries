package com.example

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.example.support.contrastRatio
import com.example.ui.theme.AfricaColor
import com.example.ui.theme.AmericasColor
import com.example.ui.theme.AntarcticaColor
import com.example.ui.theme.AsiaColor
import com.example.ui.theme.EuropeColor
import com.example.ui.theme.MasteredColor
import com.example.ui.theme.NeedsPracticeColor
import com.example.ui.theme.OceaniaColor
import com.example.ui.theme.StreakColor
import com.example.ui.theme.Typography
import com.example.ui.theme.WorldFlagsTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The pairs the UI relies on for text on coloured surfaces must meet WCAG AA (4.5:1). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ThemeContrastTest {

    @get:Rule val rule = createComposeRule()

    private fun schemeFor(dark: Boolean): ColorScheme {
        lateinit var scheme: ColorScheme
        rule.setContent { WorldFlagsTheme(darkTheme = dark) { scheme = MaterialTheme.colorScheme } }
        rule.waitForIdle()
        return scheme
    }

    @Test
    fun selectedSavedChip_labelIsReadableInTheLightTheme() {
        val s = schemeFor(dark = false)
        assertTrue("onSecondary on secondary = ${contrastRatio(s.onSecondary, s.secondary)}", contrastRatio(s.onSecondary, s.secondary) >= 4.5)
    }

    @Test
    fun selectedSavedChip_labelIsReadableInTheDarkTheme() {
        val s = schemeFor(dark = true)
        assertTrue("onSecondary on secondary = ${contrastRatio(s.onSecondary, s.secondary)}", contrastRatio(s.onSecondary, s.secondary) >= 4.5)
    }

    @Test
    fun theOldChipColours_wouldHaveFailedInTheLightTheme() {
        // Documents the bug: black label on the light theme's navy secondary colour.
        val s = schemeFor(dark = false)
        assertTrue(contrastRatio(Color.Black, s.secondary) < 3.0)
    }

    private fun assertReadable(name: String, background: Color, text: Color, minimum: Double = 4.5) {
        val ratio = contrastRatio(text, background)
        assertTrue("$name is %.2f:1, needs $minimum:1".format(ratio), ratio >= minimum)
    }

    @Test
    fun whiteTextOnEveryContinentColour_isReadable() {
        mapOf(
            "Africa" to AfricaColor,
            "Americas" to AmericasColor,
            "Asia" to AsiaColor,
            "Europe" to EuropeColor,
            "Oceania" to OceaniaColor,
            "Antarctica" to AntarcticaColor
        ).forEach { (name, color) -> assertReadable("white on $name", color, Color.White) }
    }

    @Test
    fun whiteTextOnTheStreakBadgeAndGradeButtons_isReadable() {
        assertReadable("white on streak badge", StreakColor, Color.White)
        assertReadable("white on Needs Practice", NeedsPracticeColor, Color.White)
        assertReadable("white on Mastered", MasteredColor, Color.White)
    }

    private fun containerPairs(s: ColorScheme) =
        mapOf(
            "primaryContainer" to (s.primaryContainer to s.onPrimaryContainer),
            "secondaryContainer" to (s.secondaryContainer to s.onSecondaryContainer),
            "tertiaryContainer" to (s.tertiaryContainer to s.onTertiaryContainer),
            "surfaceContainer" to (s.surfaceContainer to s.onSurface),
            "primary" to (s.primary to s.onPrimary),
            "secondary" to (s.secondary to s.onSecondary)
        )

    @Test
    fun containerRoles_areReadableInTheLightTheme() {
        containerPairs(schemeFor(dark = false)).forEach { (name, pair) -> assertReadable("light $name", pair.first, pair.second) }
    }

    @Test
    fun containerRoles_areReadableInTheDarkTheme() {
        containerPairs(schemeFor(dark = true)).forEach { (name, pair) -> assertReadable("dark $name", pair.first, pair.second) }
    }

    private fun assertNotMaterialDefaults(s: ColorScheme) {
        // Unset roles silently fall back to the baseline purple palette; the nav bar and cards used to show it.
        assertTrue(s.secondaryContainer != Color(0xFFE8DEF8))
        assertTrue(s.primaryContainer != Color(0xFFEADDFF))
        assertTrue(s.surfaceContainer != Color(0xFFF3EDF7))
    }

    @Test
    fun lightContainerRoles_areNotMaterialsDefaultLavender() = assertNotMaterialDefaults(schemeFor(dark = false))

    @Test
    fun darkContainerRoles_areNotMaterialsDefaultLavender() = assertNotMaterialDefaults(schemeFor(dark = true))

    @Test
    fun bodyText_scalesItsLineHeightWithTheFontSize() {
        // A fixed 24sp line height made every smaller Text (badges, captions) oversized and double-spaced.
        assertTrue(Typography.bodyLarge.lineHeight.isEm)
    }
}
