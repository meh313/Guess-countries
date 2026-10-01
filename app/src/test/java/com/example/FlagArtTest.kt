package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.core.app.ApplicationProvider
import com.example.quiz.allCountries
import com.example.support.dominantColour
import com.example.ui.components.flagArtFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The bundled flag images: one per country, the right file for the right country, and recognisably the
 * right flag. Native graphics decode the real WebP files.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class FlagArtTest {

    private val resources get() = ApplicationProvider.getApplicationContext<Context>().resources

    private fun bitmapOf(code: String): Bitmap {
        val id = flagArtFor(code)
        assertNotNull("no flag art for $code", id)
        return BitmapFactory.decodeResource(resources, id!!)
    }

    @Test
    fun everyCountryHasItsOwnFlagFile() {
        val ids = allCountries.map { flagArtFor(it.code) }
        assertTrue("a country has no flag art: ${allCountries.filterIndexed { i, _ -> ids[i] == null }.map { it.code }}", ids.none { it == null })
        assertEquals("two countries share one flag file", ids.size, ids.toSet().size)
    }

    @Test
    fun eachFlagFileIsNamedAfterItsCountry() {
        allCountries.forEach {
            assertEquals(
                "flag_${it.code.lowercase()}",
                resources.getResourceEntryName(flagArtFor(it.code)!!)
            )
        }
    }

    @Test
    fun everyFlagIsA3To2Image() {
        allCountries.forEach {
            val bitmap = bitmapOf(it.code)
            assertEquals("${it.code} width", 600, bitmap.width)
            assertEquals("${it.code} height", 400, bitmap.height)
        }
    }

    @Test
    fun aCountryWithoutArtGetsNoFlagFile() {
        assertEquals(null, flagArtFor("XX"))
    }

    private class Anchor(val left: Float, val top: Float, val right: Float, val bottom: Float, val colour: String)

    private fun dominantColour(bitmap: Bitmap, a: Anchor): String =
        bitmap.dominantColour(a.left, a.top, a.right, a.bottom).first

    /** Where each flag has which colour. Enough points to tell the 33 flags apart and catch a swapped file. */
    private val anchors: Map<String, List<Anchor>> = mapOf(
        "FR" to listOf(Anchor(0.02f, 0.1f, 0.3f, 0.9f, "blue"), Anchor(0.37f, 0.1f, 0.63f, 0.9f, "white"), Anchor(0.7f, 0.1f, 0.98f, 0.9f, "red")),
        "DE" to listOf(Anchor(0f, 0f, 1f, 0.28f, "black"), Anchor(0f, 0.38f, 1f, 0.62f, "red"), Anchor(0f, 0.72f, 1f, 1f, "yellow")),
        "IT" to listOf(Anchor(0.02f, 0.1f, 0.3f, 0.9f, "green"), Anchor(0.37f, 0.1f, 0.63f, 0.9f, "white"), Anchor(0.7f, 0.1f, 0.98f, 0.9f, "red")),
        "GB" to listOf(Anchor(0.46f, 0.46f, 0.54f, 0.54f, "red"), Anchor(0.46f, 0f, 0.54f, 0.1f, "red")),
        "ES" to listOf(Anchor(0.2f, 0.02f, 0.9f, 0.2f, "red"), Anchor(0.55f, 0.35f, 0.95f, 0.65f, "yellow"), Anchor(0.2f, 0.8f, 0.9f, 0.98f, "red")),
        "GR" to listOf(Anchor(0f, 0f, 0.08f, 0.1f, "blue"), Anchor(0.5f, 0.2f, 0.98f, 0.24f, "white"), Anchor(0.02f, 0.62f, 0.98f, 0.98f, "blue")),
        "SE" to listOf(Anchor(0.02f, 0.02f, 0.25f, 0.4f, "blue"), Anchor(0.02f, 0.42f, 0.98f, 0.58f, "yellow"), Anchor(0.4f, 0.62f, 0.98f, 0.98f, "blue")),
        "NO" to listOf(Anchor(0.02f, 0.02f, 0.2f, 0.3f, "red"), Anchor(0.02f, 0.44f, 0.98f, 0.56f, "blue"), Anchor(0.5f, 0.02f, 0.98f, 0.3f, "red")),
        "UA" to listOf(Anchor(0.02f, 0.02f, 0.98f, 0.45f, "blue"), Anchor(0.02f, 0.55f, 0.98f, 0.98f, "yellow")),
        "US" to listOf(Anchor(0.02f, 0.02f, 0.38f, 0.5f, "blue"), Anchor(0.45f, 0.02f, 0.98f, 0.06f, "red"), Anchor(0.02f, 0.94f, 0.98f, 0.99f, "red")),
        "CA" to listOf(Anchor(0.02f, 0.1f, 0.2f, 0.9f, "red"), Anchor(0.3f, 0.05f, 0.45f, 0.3f, "white"), Anchor(0.4f, 0.4f, 0.6f, 0.6f, "red"), Anchor(0.8f, 0.1f, 0.98f, 0.9f, "red")),
        "MX" to listOf(Anchor(0.02f, 0.1f, 0.3f, 0.9f, "green"), Anchor(0.37f, 0.02f, 0.63f, 0.25f, "white"), Anchor(0.7f, 0.1f, 0.98f, 0.9f, "red")),
        "BR" to listOf(Anchor(0f, 0f, 0.1f, 0.1f, "green"), Anchor(0.12f, 0.42f, 0.22f, 0.58f, "yellow"), Anchor(0.4f, 0.4f, 0.6f, 0.6f, "blue")),
        "AR" to listOf(Anchor(0.1f, 0.02f, 0.9f, 0.25f, "blue"), Anchor(0.02f, 0.4f, 0.3f, 0.6f, "white"), Anchor(0.1f, 0.78f, 0.9f, 0.98f, "blue")),
        "CO" to listOf(Anchor(0.02f, 0.02f, 0.98f, 0.45f, "yellow"), Anchor(0.02f, 0.55f, 0.98f, 0.73f, "blue"), Anchor(0.02f, 0.8f, 0.98f, 0.98f, "red")),
        "CL" to listOf(Anchor(0.02f, 0.02f, 0.3f, 0.45f, "blue"), Anchor(0.5f, 0.02f, 0.98f, 0.45f, "white"), Anchor(0.02f, 0.55f, 0.98f, 0.98f, "red")),
        "JP" to listOf(Anchor(0f, 0f, 0.1f, 0.1f, "white"), Anchor(0.4f, 0.4f, 0.6f, 0.6f, "red")),
        "CN" to listOf(Anchor(0.4f, 0.5f, 0.98f, 0.98f, "red"), Anchor(0f, 0f, 0.1f, 0.1f, "red")),
        "IN" to listOf(Anchor(0.02f, 0.02f, 0.98f, 0.28f, "orange"), Anchor(0.02f, 0.37f, 0.4f, 0.63f, "white"), Anchor(0.02f, 0.72f, 0.98f, 0.98f, "green"), Anchor(0.47f, 0.47f, 0.53f, 0.53f, "blue")),
        "KR" to listOf(Anchor(0f, 0f, 0.1f, 0.1f, "white"), Anchor(0.44f, 0.34f, 0.56f, 0.46f, "red"), Anchor(0.44f, 0.54f, 0.56f, 0.66f, "blue")),
        "VN" to listOf(Anchor(0f, 0f, 0.15f, 0.15f, "red"), Anchor(0.45f, 0.45f, 0.55f, 0.55f, "yellow")),
        "TH" to listOf(Anchor(0.02f, 0.02f, 0.98f, 0.14f, "red"), Anchor(0.02f, 0.19f, 0.98f, 0.31f, "white"), Anchor(0.02f, 0.4f, 0.98f, 0.6f, "blue"), Anchor(0.02f, 0.69f, 0.98f, 0.81f, "white"), Anchor(0.02f, 0.86f, 0.98f, 0.98f, "red")),
        "SA" to listOf(Anchor(0f, 0f, 0.1f, 0.1f, "green"), Anchor(0f, 0.9f, 0.1f, 1f, "green")),
        "EG" to listOf(Anchor(0.02f, 0.02f, 0.98f, 0.28f, "red"), Anchor(0.02f, 0.38f, 0.35f, 0.62f, "white"), Anchor(0.02f, 0.72f, 0.98f, 0.98f, "black")),
        "KE" to listOf(Anchor(0.02f, 0.02f, 0.3f, 0.2f, "black"), Anchor(0.02f, 0.4f, 0.2f, 0.6f, "red"), Anchor(0.02f, 0.8f, 0.3f, 0.98f, "green")),
        "ZA" to listOf(Anchor(0.7f, 0.02f, 0.98f, 0.25f, "red"), Anchor(0.7f, 0.75f, 0.98f, 0.98f, "blue"), Anchor(0f, 0.4f, 0.06f, 0.6f, "black")),
        "NG" to listOf(Anchor(0.02f, 0.1f, 0.3f, 0.9f, "green"), Anchor(0.37f, 0.1f, 0.63f, 0.9f, "white"), Anchor(0.7f, 0.1f, 0.98f, 0.9f, "green")),
        "MA" to listOf(Anchor(0.02f, 0.02f, 0.98f, 0.2f, "red"), Anchor(0f, 0.8f, 1f, 1f, "red")),
        "TZ" to listOf(Anchor(0.02f, 0.02f, 0.3f, 0.3f, "green"), Anchor(0.7f, 0.7f, 0.98f, 0.98f, "blue"), Anchor(0.45f, 0.45f, 0.55f, 0.55f, "black")),
        "AU" to listOf(Anchor(0.55f, 0.8f, 0.98f, 0.98f, "blue"), Anchor(0.2f, 0.7f, 0.3f, 0.85f, "white")),
        "NZ" to listOf(Anchor(0.02f, 0.75f, 0.4f, 0.98f, "blue"), Anchor(0.55f, 0.75f, 0.98f, 0.98f, "blue")),
        "FJ" to listOf(Anchor(0.55f, 0.02f, 0.98f, 0.4f, "blue"), Anchor(0.05f, 0.7f, 0.4f, 0.98f, "blue")),
        "AQ" to listOf(Anchor(0f, 0f, 0.08f, 0.08f, "blue"), Anchor(0.45f, 0.45f, 0.55f, 0.55f, "white")),
    )

    @Test
    fun theTableCoversEveryCountry() {
        assertEquals(allCountries.map { it.code }.toSet(), anchors.keys)
    }

    @Test
    fun eachFlagShowsTheColoursOfItsCountry() {
        val wrong = mutableListOf<String>()
        for ((code, boxes) in anchors) {
            val bitmap = bitmapOf(code)
            for (box in boxes) {
                val actual = dominantColour(bitmap, box)
                if (actual != box.colour) {
                    wrong += "$code at (${box.left}, ${box.top})-(${box.right}, ${box.bottom}): wanted ${box.colour}, got $actual"
                }
            }
        }
        assertTrue(wrong.joinToString("\n"), wrong.isEmpty())
    }

    @Test
    fun thailandHasFiveStripesNotThree() {
        // The old drawing gave Thailand three equal stripes, which is the Netherlands.
        val bitmap = bitmapOf("TH")
        val bands = listOf(0.08f, 0.25f, 0.5f, 0.75f, 0.92f).map {
            dominantColour(bitmap, Anchor(0.3f, it - 0.02f, 0.7f, it + 0.02f, ""))
        }
        assertEquals(listOf("red", "white", "blue", "white", "red"), bands)
    }
}
