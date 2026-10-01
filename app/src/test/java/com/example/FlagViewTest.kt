package com.example

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.model.Country
import com.example.quiz.allCountries
import com.example.support.crop
import com.example.support.dominantColour
import com.example.support.drawWindow
import com.example.ui.components.FlagView
import com.example.ui.theme.WorldFlagsTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** What FlagView really puts on screen, read back from the rendered pixels. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w360dp-h640dp-xxhdpi")
class FlagViewTest {

    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private fun country(code: String) = allCountries.single { it.code == code }

    private fun show(country: Country, overlay: Boolean = false) {
        rule.setContent {
            WorldFlagsTheme {
                Box(Modifier.size(300.dp, 200.dp)) {
                    FlagView(country, Modifier.fillMaxSize().testTag("flag"), showEmojiOverlay = overlay)
                }
            }
        }
        rule.waitForIdle()
    }

    private fun flagPixels() =
        rule.drawWindow().crop(rule.onNodeWithTag("flag", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot)

    @Test
    fun drawsTheBundledArtworkOfTheCountry() {
        show(country("TH"))

        val flag = flagPixels()
        val bands = listOf(0.08f, 0.25f, 0.5f, 0.75f, 0.92f).map { flag.dominantColour(0.3f, it - 0.02f, 0.7f, it + 0.02f).first }
        assertEquals(listOf("red", "white", "blue", "white", "red"), bands)
    }

    @Test
    fun drawsADifferentFlagForADifferentCountry() {
        show(country("MX"))

        val flag = flagPixels()
        assertEquals("green", flag.dominantColour(0.05f, 0.2f, 0.25f, 0.8f).first)
        assertEquals("red", flag.dominantColour(0.75f, 0.2f, 0.95f, 0.8f).first)
    }

    @Test
    fun theEmojiIsNotDrawnOverTheArtworkByDefault() {
        show(country("TH"))

        val (colour, share) = flagPixels().dominantColour(0.4f, 0.4f, 0.6f, 0.6f)
        assertEquals("blue", colour)
        assertTrue("the middle of the Thai blue stripe is only $share blue", share > 0.98f)
    }

    @Test
    fun theEmojiOverlayCanBeSwitchedOn() {
        show(country("TH"), overlay = true)

        val (_, share) = flagPixels().dominantColour(0.4f, 0.4f, 0.6f, 0.6f)
        assertTrue("nothing was drawn over the artwork ($share of it is still blue)", share < 0.98f)
    }

    @Test
    fun aCountryWithoutArtworkGetsAPlaceholderInsteadOfCrashing() {
        show(country("TH").copy(code = "XX"))

        rule.onNodeWithTag("flag", useUnmergedTree = true).assertIsDisplayed()
        // None of the Thai stripes is drawn.
        val bands = listOf(0.08f, 0.5f, 0.92f).map { flagPixels().dominantColour(0.3f, it - 0.02f, 0.7f, it + 0.02f).first }
        assertEquals(1, bands.toSet().size)
    }
}
