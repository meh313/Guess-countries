package com.example

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.example.support.contrastRatio
import com.example.ui.components.MissLabelColours
import com.example.ui.screens.HERO_BANNER_ALPHA
import com.example.ui.theme.AfricaColor
import com.example.ui.theme.AmericasColor
import com.example.ui.theme.AntarcticaColor
import com.example.ui.theme.AsiaColor
import com.example.ui.theme.EuropeColor
import com.example.ui.theme.LightPrimaryContainer
import com.example.ui.theme.MasteredColor
import com.example.ui.theme.NeedsPracticeColor
import com.example.ui.theme.OceaniaColor
import com.example.ui.theme.OceanBluePrimary
import com.example.ui.theme.StreakColor
import com.example.ui.theme.Typography
import com.example.ui.theme.WorldFlagsTheme
import org.junit.Assert.assertTrue
import kotlin.math.pow
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

    /** The app's scheme, or with [dynamic] the wallpaper's (Android 12+; here the default system palette). */
    private fun schemeFor(dark: Boolean, dynamic: Boolean = false): ColorScheme {
        lateinit var scheme: ColorScheme
        rule.setContent { WorldFlagsTheme(darkTheme = dark, dynamicColor = dynamic) { scheme = MaterialTheme.colorScheme } }
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

    /**
     * The grade buttons (Again, Good, Easy in FlashcardScreen) draw white text on NeedsPracticeColor, the theme's
     * primary and MasteredColor. The two fixed colours and the light theme's primary carry white text.
     */
    @Test
    fun whiteTextOnTheGradeButtonColours_isReadable() {
        assertReadable("white on Again (NeedsPracticeColor)", NeedsPracticeColor, Color.White)
        assertReadable("white on Good (light primary)", OceanBluePrimary, Color.White)
        assertReadable("white on Good (light primary, from the scheme)", schemeFor(dark = false).primary, Color.White)
        assertReadable("white on Easy (MasteredColor)", MasteredColor, Color.White)
    }

    @Test
    fun whiteTextOnTheDarkPrimary_isNotReadable_soTheGoodButtonMustUseOnPrimaryThere() {
        // Documents why a white label cannot sit on the dark theme's pale primary blue: onPrimary (navy) can, and the
        // "primary" pair in containerPairs holds it to 4.5:1 in both themes.
        val s = schemeFor(dark = true)
        assertTrue("white on dark primary = ${contrastRatio(Color.White, s.primary)}", contrastRatio(Color.White, s.primary) < 4.5)
        assertReadable("onPrimary on dark primary", s.primary, s.onPrimary)
    }

    /** The secondary text colour (captions, hints, notes) on the two surfaces it is drawn on. */
    private fun secondaryTextPairs(s: ColorScheme) =
        mapOf(
            "onSurfaceVariant on background" to (s.background to s.onSurfaceVariant),
            "onSurfaceVariant on surface" to (s.surface to s.onSurfaceVariant)
        )

    @Test
    fun secondaryText_isReadableInTheLightTheme() {
        secondaryTextPairs(schemeFor(dark = false)).forEach { (name, pair) -> assertReadable("light $name", pair.first, pair.second) }
    }

    @Test
    fun secondaryText_isReadableInTheDarkTheme() {
        secondaryTextPairs(schemeFor(dark = true)).forEach { (name, pair) -> assertReadable("dark $name", pair.first, pair.second) }
    }

    @Test
    fun outlineOnTheLightBackground_isNotReadableAsText() {
        // Documents why captions and hints moved from outline to onSurfaceVariant: outline is a border colour and
        // falls short of 4.5:1 on the light background (it may be left on icons and borders, which need 3:1).
        val s = schemeFor(dark = false)
        val ratio = contrastRatio(s.outline, s.background)
        assertTrue("outline on light background = %.2f:1, expected under 4.5:1".format(ratio), ratio < 4.5)
    }

    private fun containerPairs(s: ColorScheme) =
        mapOf(
            "primaryContainer" to (s.primaryContainer to s.onPrimaryContainer),
            "secondaryContainer" to (s.secondaryContainer to s.onSecondaryContainer),
            "tertiaryContainer" to (s.tertiaryContainer to s.onTertiaryContainer),
            "surfaceContainerLowest" to (s.surfaceContainerLowest to s.onSurface),
            "surfaceContainerLow" to (s.surfaceContainerLow to s.onSurface),
            "surfaceContainer" to (s.surfaceContainer to s.onSurface),
            "surfaceContainerHigh" to (s.surfaceContainerHigh to s.onSurface),
            "surfaceContainerHighest" to (s.surfaceContainerHighest to s.onSurface),
            "primary" to (s.primary to s.onPrimary),
            "secondary" to (s.secondary to s.onSecondary),
            // Text buttons, "Score:", "Flag meaning" and the icons draw the primary colour as text.
            "primary text on background" to (s.background to s.primary),
            "primary text on surface" to (s.surface to s.primary),
            "primary text on surfaceContainerHigh" to (s.surfaceContainerHigh to s.primary)
        )

    @Test
    fun containerRoles_areReadableInTheLightTheme() {
        containerPairs(schemeFor(dark = false)).forEach { (name, pair) -> assertReadable("light $name", pair.first, pair.second) }
    }

    @Test
    fun containerRoles_areReadableInTheDarkTheme() {
        containerPairs(schemeFor(dark = true)).forEach { (name, pair) -> assertReadable("dark $name", pair.first, pair.second) }
    }

    /** The "You picked" and "Correct" labels after a miss, on the primary container they sit on. */
    private fun assertMissLabelsReadable(name: String, s: ColorScheme) {
        val labels = MissLabelColours.on(s.primaryContainer)
        assertReadable("$name You picked", s.primaryContainer, labels.picked)
        assertReadable("$name Correct", s.primaryContainer, labels.correct)
    }

    @Test
    fun theMissLabels_areReadableInTheLightTheme() = assertMissLabelsReadable("light", schemeFor(dark = false))

    @Test
    fun theMissLabels_areReadableInTheDarkTheme() = assertMissLabelsReadable("dark", schemeFor(dark = true))

    @Test
    fun theMissLabels_areReadableWithWallpaperColours_light() = assertMissLabelsReadable("dynamic light", schemeFor(dark = false, dynamic = true))

    @Test
    fun theMissLabels_areReadableWithWallpaperColours_dark() = assertMissLabelsReadable("dynamic dark", schemeFor(dark = true, dynamic = true))

    /**
     * A grey of Material's [tone] (CIE L*, 0 black to 100 white). A wallpaper scheme sets each role at a fixed tone
     * whatever the wallpaper's hue (primary 40 and onPrimary 100 in light, 80 and 20 in dark; primaryContainer 90 and
     * 30), and the tone alone fixes a colour's luminance, so these greys stand for every wallpaper's colours here.
     */
    private fun toneGrey(tone: Double): Color {
        val luminance = if (tone > 8) ((tone + 16) / 116).pow(3) else tone / 903.3
        val encoded = if (luminance <= 0.0031308) 12.92 * luminance else 1.055 * luminance.pow(1 / 2.4) - 0.055
        return Color(encoded.toFloat(), encoded.toFloat(), encoded.toFloat())
    }

    @Test
    fun theMissLabels_areReadableOnAnyWallpapersContainer() {
        for ((name, container) in listOf("light (tone 90)" to toneGrey(90.0), "dark (tone 30)" to toneGrey(30.0))) {
            val labels = MissLabelColours.on(container)
            assertReadable("$name You picked", container, labels.picked)
            assertReadable("$name Correct", container, labels.correct)
        }
    }

    @Test
    fun theAnswerListsRedAndGreen_wouldNotHaveBeenReadableAsMissLabels() {
        // Documents the bug: the labels used the answer list's red and green, under 4.5:1 on the light container and
        // under even 3:1 on the dark one.
        val s = schemeFor(dark = true)
        assertTrue(contrastRatio(Color(0xFFC62828), s.primaryContainer) < 3.0)
        assertTrue(contrastRatio(Color(0xFF2E7D32), s.primaryContainer) < 3.0)
        assertTrue(contrastRatio(Color(0xFFC62828), LightPrimaryContainer) < 4.5)
        assertTrue(contrastRatio(Color(0xFF2E7D32), LightPrimaryContainer) < 4.5)
    }

    /**
     * The Explore hero: its title and subtitle in [text] over [primary] with the world map drawn on it at [alpha]. The
     * worst place is over a pure white or pure black pixel of the picture; the canvas blends it in sRGB.
     */
    private fun heroWorstContrast(primary: Color, text: Color, alpha: Float = HERO_BANNER_ALPHA): Double =
        listOf(Color.White, Color.Black).minOf { pixel ->
            val behind = Color(
                red = primary.red + (pixel.red - primary.red) * alpha,
                green = primary.green + (pixel.green - primary.green) * alpha,
                blue = primary.blue + (pixel.blue - primary.blue) * alpha
            )
            contrastRatio(text, behind)
        }

    private fun assertHeroReadable(name: String, primary: Color, text: Color) {
        val ratio = heroWorstContrast(primary, text)
        assertTrue("$name hero text is %.2f:1 at worst, needs 4.5:1".format(ratio), ratio >= 4.5)
    }

    @Test
    fun theExploreHero_isReadableInTheLightTheme() = schemeFor(dark = false).let { assertHeroReadable("light", it.primary, it.onPrimary) }

    @Test
    fun theExploreHero_isReadableInTheDarkTheme() = schemeFor(dark = true).let { assertHeroReadable("dark", it.primary, it.onPrimary) }

    @Test
    fun theExploreHero_isReadableWithWallpaperColours_light() =
        schemeFor(dark = false, dynamic = true).let { assertHeroReadable("dynamic light", it.primary, it.onPrimary) }

    @Test
    fun theExploreHero_isReadableWithWallpaperColours_dark() =
        schemeFor(dark = true, dynamic = true).let { assertHeroReadable("dynamic dark", it.primary, it.onPrimary) }

    @Test
    fun theExploreHero_isReadableWithAnyWallpapersColours() {
        assertHeroReadable("wallpaper light (tones 40, 100)", toneGrey(40.0), toneGrey(100.0))
        assertHeroReadable("wallpaper dark (tones 80, 20)", toneGrey(80.0), toneGrey(20.0))
    }

    @Test
    fun theHeroAsItWas_wasNotReadable() {
        // Documents the bug: white text over the map at 0.45 on the dark theme's pale primary, and even the right
        // text colour falls short over a light part of the map that strong.
        val dark = schemeFor(dark = true)
        assertTrue(heroWorstContrast(dark.primary, Color.White, alpha = 0.45f) < 3.0)
        assertTrue(heroWorstContrast(dark.primary, dark.onPrimary, alpha = 0.45f) < 4.5)
    }

    private fun assertNotMaterialDefaults(s: ColorScheme) {
        // Unset roles silently fall back to the baseline purple palette; the nav bar, cards, dialogs and the switch
        // track used to show it. Both themes' defaults are listed, so the check holds whichever scheme is passed.
        val lavender = setOf(
            Color(0xFFE8DEF8), Color(0xFFEADDFF), Color(0xFFF3EDF7), Color(0xFFF7F2FA), Color(0xFFECE6F0), Color(0xFFE6E0E9),
            Color(0xFF4A4458), Color(0xFF4F378B), Color(0xFF211F26), Color(0xFF1D1B20), Color(0xFF2B2930), Color(0xFF36343B)
        )
        mapOf(
            "secondaryContainer" to s.secondaryContainer,
            "primaryContainer" to s.primaryContainer,
            "surfaceContainerLowest" to s.surfaceContainerLowest,
            "surfaceContainerLow" to s.surfaceContainerLow,
            "surfaceContainer" to s.surfaceContainer,
            "surfaceContainerHigh" to s.surfaceContainerHigh,
            "surfaceContainerHighest" to s.surfaceContainerHighest
        ).forEach { (name, color) -> assertTrue("$name is Material's default", color !in lavender) }
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
