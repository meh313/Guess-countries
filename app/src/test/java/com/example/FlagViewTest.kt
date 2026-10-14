package com.example

import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.model.Country
import com.example.quiz.allCountries
import com.example.support.colourName
import com.example.support.crop
import com.example.support.dominantColour
import com.example.support.drawWindow
import com.example.ui.components.FlagAspectRatio
import com.example.ui.components.FlagBitmaps
import com.example.ui.components.FlagView
import com.example.ui.components.flagArtFor
import com.example.ui.theme.WorldFlagsTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
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
                Box(Modifier.size(300.dp, 300.dp)) {
                    FlagView(country, Modifier.width(300.dp).testTag("flag"), showEmojiOverlay = overlay)
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
    fun theBoxHasTheShapeOfTheArtworkSoCirclesStayRound() {
        show(country("JP"))

        val box = rule.onNodeWithTag("flag", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertEquals(FlagAspectRatio, box.width / box.height, 0.01f)
        // The red disc is a circle: as wide as it is tall.
        val flag = flagPixels()
        fun redRun(vertical: Boolean): Int {
            val n = if (vertical) flag.height else flag.width
            return (0 until n).count {
                val pixel = if (vertical) flag.getPixel(flag.width / 2, it) else flag.getPixel(it, flag.height / 2)
                colourName(pixel) == "red"
            }
        }
        val across = redRun(vertical = false)
        val down = redRun(vertical = true)
        assertTrue("disc is $across px wide and $down px tall", kotlin.math.abs(across - down) <= 4)
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

    /** A thumbnail is decoded off the main thread and arrives on a later frame; waits until it has been drawn. */
    private fun awaitThumbnail(centreColour: String) {
        rule.waitUntil(timeoutMillis = 10_000) {
            // The decoded bitmap reaches the state through the main thread's queue, which waitUntil alone does not run.
            shadowOf(Looper.getMainLooper()).idle()
            flagPixels().dominantColour(0.4f, 0.4f, 0.6f, 0.6f).first == centreColour
        }
        rule.waitForIdle()
    }

    @Test
    fun aThumbnailDrawsTheSameArtworkOnceItIsDecoded() {
        // 80dp at xxhdpi is 240px: the 256px bucket, decoded at half size (300×225).
        rule.setContent {
            WorldFlagsTheme {
                Box(Modifier.size(300.dp, 300.dp)) {
                    FlagView(country("TH"), Modifier.width(80.dp).testTag("flag"), thumbnail = true)
                }
            }
        }
        rule.waitForIdle()

        awaitThumbnail(centreColour = "blue")

        val flag = flagPixels()
        val bands = listOf(0.08f, 0.25f, 0.5f, 0.75f, 0.92f).map { flag.dominantColour(0.3f, it - 0.02f, 0.7f, it + 0.02f).first }
        assertEquals(listOf("red", "white", "blue", "white", "red"), bands)
        val box = rule.onNodeWithTag("flag", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertEquals(FlagAspectRatio, box.width / box.height, 0.01f)
    }

    @Test
    fun aThumbnailOfAnotherCountryDrawsThatCountrysFlag() {
        rule.setContent {
            WorldFlagsTheme {
                Box(Modifier.size(300.dp, 300.dp)) {
                    FlagView(country("MX"), Modifier.width(150.dp).testTag("flag"), thumbnail = true)
                }
            }
        }
        rule.waitForIdle()

        awaitThumbnail(centreColour = "white")

        val flag = flagPixels()
        assertEquals("green", flag.dominantColour(0.05f, 0.2f, 0.25f, 0.8f).first)
        assertEquals("red", flag.dominantColour(0.75f, 0.2f, 0.95f, 0.8f).first)
    }

    @Test
    fun theSampleSizeHalvesTheArtworkAsOftenAsTheTargetAllows() {
        assertEquals(1, FlagBitmaps.sampleSizeFor(sourceWidth = 600, targetWidth = 600))
        assertEquals(2, FlagBitmaps.sampleSizeFor(sourceWidth = 600, targetWidth = 300))
        assertEquals(4, FlagBitmaps.sampleSizeFor(sourceWidth = 600, targetWidth = 150))
        assertEquals(8, FlagBitmaps.sampleSizeFor(sourceWidth = 600, targetWidth = 75))
        // A target between two halvings keeps the larger copy: the bitmap is never narrower than the box.
        assertEquals(2, FlagBitmaps.sampleSizeFor(sourceWidth = 600, targetWidth = 200))
        // Unknown or full width: the artwork as it is.
        assertEquals(1, FlagBitmaps.sampleSizeFor(sourceWidth = 600, targetWidth = FlagBitmaps.FULL_WIDTH))
        assertEquals(1, FlagBitmaps.sampleSizeFor(sourceWidth = 600, targetWidth = 0))
    }

    @Test
    fun theCacheHandsOutTheSameBitmapForTheSameFlagAndWidth() {
        val resources = rule.activity.resources
        val art = flagArtFor("TH")!!

        val first = FlagBitmaps.load(resources, art, 120)
        val second = FlagBitmaps.load(resources, art, 128)

        assertNotNull(first)
        assertSame("120px and 128px share the 128px bucket", first, second)
        assertSame(first, FlagBitmaps.peek(art, 100))
        // 128px out of a 600px-wide file: four times smaller, 150px wide.
        assertEquals(150, first!!.width)
        // Another bucket is another decode.
        val larger = FlagBitmaps.load(resources, art, 500)
        assertNotNull(larger)
        assertTrue("a 512px bucket keeps the full 600px artwork", larger!!.width == 600)
        assertTrue(larger !== first)
    }
}
