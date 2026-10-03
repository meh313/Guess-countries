package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.core.app.ApplicationProvider
import com.example.R
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
    fun everyFlagIsA4To3Image() {
        allCountries.forEach {
            val bitmap = bitmapOf(it.code)
            assertEquals("${it.code} width", 600, bitmap.width)
            assertEquals("${it.code} height", 450, bitmap.height)
        }
    }

    @Test
    fun aCountryWithoutArtGetsNoFlagFile() {
        assertEquals(null, flagArtFor("XX"))
    }

    private class Anchor(val left: Float, val top: Float, val right: Float, val bottom: Float, val colour: String)

    private fun dominantColour(bitmap: Bitmap, a: Anchor): String =
        bitmap.dominantColour(a.left, a.top, a.right, a.bottom).first

    /**
     * Where each flag has which colour. These boxes check that a flag looks like its country (colours and
     * layout); they cannot tell very similar flags apart (Italy and Mexico differ only by the emblem), which
     * is what [everyFlagFileIsExactlyTheOneThatWasChecked] is for.
     */
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
        // Europe batch
        "NL" to listOf(Anchor(0.02f, 0.02f, 0.98f, 0.3f, "red"), Anchor(0.02f, 0.37f, 0.98f, 0.63f, "white"), Anchor(0.02f, 0.7f, 0.98f, 0.98f, "blue")),
        "LU" to listOf(Anchor(0.02f, 0.02f, 0.98f, 0.3f, "red"), Anchor(0.02f, 0.37f, 0.98f, 0.63f, "white"), Anchor(0.02f, 0.7f, 0.98f, 0.98f, "blue")),
        "RU" to listOf(Anchor(0.02f, 0.02f, 0.98f, 0.3f, "white"), Anchor(0.02f, 0.37f, 0.98f, 0.63f, "blue"), Anchor(0.02f, 0.7f, 0.98f, 0.98f, "red")),
        "RS" to listOf(Anchor(0.55f, 0.02f, 0.98f, 0.3f, "red"), Anchor(0.55f, 0.37f, 0.98f, 0.63f, "blue"), Anchor(0.55f, 0.7f, 0.98f, 0.98f, "white")),
        "SI" to listOf(Anchor(0.55f, 0.02f, 0.98f, 0.3f, "white"), Anchor(0.55f, 0.37f, 0.98f, 0.63f, "blue"), Anchor(0.55f, 0.7f, 0.98f, 0.98f, "red")),
        "SK" to listOf(Anchor(0.55f, 0.02f, 0.98f, 0.3f, "white"), Anchor(0.55f, 0.37f, 0.98f, 0.63f, "blue"), Anchor(0.55f, 0.7f, 0.98f, 0.98f, "red")),
        "HR" to listOf(Anchor(0.02f, 0.02f, 0.3f, 0.3f, "red"), Anchor(0.02f, 0.4f, 0.3f, 0.6f, "white"), Anchor(0.02f, 0.7f, 0.3f, 0.98f, "blue")),
        "MC" to listOf(Anchor(0.02f, 0.02f, 0.98f, 0.45f, "red"), Anchor(0.02f, 0.55f, 0.98f, 0.98f, "white")),
        "PL" to listOf(Anchor(0.02f, 0.02f, 0.98f, 0.45f, "white"), Anchor(0.02f, 0.55f, 0.98f, 0.98f, "red")),
        "IE" to listOf(Anchor(0.02f, 0.1f, 0.3f, 0.9f, "green"), Anchor(0.37f, 0.1f, 0.63f, 0.9f, "white"), Anchor(0.7f, 0.1f, 0.98f, 0.9f, "orange")),
        "RO" to listOf(Anchor(0.02f, 0.1f, 0.3f, 0.9f, "blue"), Anchor(0.37f, 0.1f, 0.63f, 0.9f, "yellow"), Anchor(0.7f, 0.1f, 0.98f, 0.9f, "red")),
        "AD" to listOf(Anchor(0.02f, 0.1f, 0.3f, 0.9f, "blue"), Anchor(0.37f, 0.02f, 0.63f, 0.2f, "yellow"), Anchor(0.7f, 0.1f, 0.98f, 0.9f, "red")),
        "MD" to listOf(Anchor(0.02f, 0.1f, 0.3f, 0.9f, "blue"), Anchor(0.37f, 0.02f, 0.63f, 0.15f, "yellow"), Anchor(0.7f, 0.1f, 0.98f, 0.9f, "red")),
        "HU" to listOf(Anchor(0.02f, 0.02f, 0.98f, 0.3f, "red"), Anchor(0.02f, 0.37f, 0.98f, 0.63f, "white"), Anchor(0.02f, 0.7f, 0.98f, 0.98f, "green")),
        "BG" to listOf(Anchor(0.02f, 0.02f, 0.98f, 0.3f, "white"), Anchor(0.02f, 0.37f, 0.98f, 0.63f, "green"), Anchor(0.02f, 0.7f, 0.98f, 0.98f, "red")),
        "AT" to listOf(Anchor(0.02f, 0.02f, 0.98f, 0.3f, "red"), Anchor(0.02f, 0.37f, 0.98f, 0.63f, "white"), Anchor(0.02f, 0.7f, 0.98f, 0.98f, "red")),
        "LV" to listOf(Anchor(0.02f, 0.02f, 0.98f, 0.35f, "red"), Anchor(0.02f, 0.42f, 0.98f, 0.58f, "white"), Anchor(0.02f, 0.65f, 0.98f, 0.98f, "red")),
        "BE" to listOf(Anchor(0.02f, 0.1f, 0.3f, 0.9f, "black"), Anchor(0.37f, 0.1f, 0.63f, 0.9f, "yellow"), Anchor(0.7f, 0.1f, 0.98f, 0.9f, "red")),
        "LT" to listOf(Anchor(0.02f, 0.37f, 0.98f, 0.63f, "green"), Anchor(0.02f, 0.7f, 0.98f, 0.98f, "red")),
        "EE" to listOf(Anchor(0.02f, 0.02f, 0.98f, 0.3f, "blue"), Anchor(0.02f, 0.37f, 0.98f, 0.63f, "black"), Anchor(0.02f, 0.7f, 0.98f, 0.98f, "white")),
        "DK" to listOf(Anchor(0.02f, 0.02f, 0.25f, 0.4f, "red"), Anchor(0.02f, 0.42f, 0.98f, 0.58f, "white"), Anchor(0.5f, 0.62f, 0.98f, 0.98f, "red")),
        "FI" to listOf(Anchor(0.02f, 0.02f, 0.25f, 0.4f, "white"), Anchor(0.02f, 0.42f, 0.98f, 0.58f, "blue"), Anchor(0.5f, 0.62f, 0.98f, 0.98f, "white")),
        "IS" to listOf(Anchor(0.02f, 0.02f, 0.2f, 0.3f, "blue"), Anchor(0.02f, 0.44f, 0.98f, 0.56f, "red"), Anchor(0.5f, 0.62f, 0.98f, 0.98f, "blue")),
        "CZ" to listOf(Anchor(0.02f, 0.4f, 0.3f, 0.6f, "blue"), Anchor(0.6f, 0.02f, 0.98f, 0.45f, "white"), Anchor(0.6f, 0.55f, 0.98f, 0.98f, "red")),
        "PT" to listOf(Anchor(0.02f, 0.1f, 0.25f, 0.9f, "green"), Anchor(0.7f, 0.1f, 0.98f, 0.9f, "red")),
        "MT" to listOf(Anchor(0.02f, 0.3f, 0.45f, 0.9f, "white"), Anchor(0.55f, 0.1f, 0.98f, 0.9f, "red")),
        "VA" to listOf(Anchor(0.02f, 0.1f, 0.45f, 0.9f, "yellow"), Anchor(0.55f, 0.02f, 0.98f, 0.2f, "white")),
        "CH" to listOf(Anchor(0.02f, 0.02f, 0.2f, 0.2f, "red"), Anchor(0.47f, 0.47f, 0.53f, 0.53f, "white"), Anchor(0.8f, 0.8f, 0.98f, 0.98f, "red")),
        "XK" to listOf(Anchor(0.02f, 0.02f, 0.98f, 0.2f, "blue"), Anchor(0.02f, 0.8f, 0.98f, 0.98f, "blue")),
        "AL" to listOf(Anchor(0.02f, 0.02f, 0.2f, 0.98f, "red"), Anchor(0.8f, 0.02f, 0.98f, 0.98f, "red")),
        "BY" to listOf(Anchor(0.2f, 0.02f, 0.98f, 0.6f, "red"), Anchor(0.2f, 0.75f, 0.98f, 0.98f, "green")),
        "BA" to listOf(Anchor(0.02f, 0.02f, 0.2f, 0.3f, "blue"), Anchor(0.02f, 0.7f, 0.5f, 0.98f, "blue"), Anchor(0.45f, 0.1f, 0.55f, 0.3f, "yellow")),
        "ME" to listOf(Anchor(0.08f, 0.08f, 0.3f, 0.3f, "red"), Anchor(0.7f, 0.7f, 0.92f, 0.92f, "red")),
        "MK" to listOf(Anchor(0f, 0f, 0.06f, 0.06f, "red"), Anchor(0.45f, 0.45f, 0.55f, 0.55f, "yellow")),
        "LI" to listOf(Anchor(0.4f, 0.02f, 0.98f, 0.45f, "blue"), Anchor(0.02f, 0.55f, 0.98f, 0.98f, "red")),
        "SM" to listOf(Anchor(0.02f, 0.02f, 0.98f, 0.2f, "white"), Anchor(0.02f, 0.8f, 0.98f, 0.98f, "blue")),
    )

    /**
     * Flags that must have anchors: the original set plus look-alike pairs added with each batch, where a
     * swapped file would otherwise pass every other check.
     */
    private val requiredAnchors = setOf(
        "FR", "DE", "IT", "GB", "ES", "GR", "SE", "NO", "UA", "US", "CA", "MX", "BR", "AR", "CO", "CL", "JP", "CN",
        "IN", "KR", "VN", "TH", "SA", "EG", "KE", "ZA", "NG", "MA", "TZ", "AU", "NZ", "FJ", "AQ",
        // Europe: look-alike bands (NL/LU/RU, RS/SI/SK/HR, MC/PL, IE, RO/AD/MD, HU/BG, AT/LV, BE, LT/EE, the Nordic crosses, CZ) and the rest
        "AL", "AD", "AT", "BY", "BE", "BA", "BG", "HR", "CZ", "DK", "EE", "FI",
        "HU", "IS", "IE", "XK", "LV", "LI", "LT", "LU", "MT", "MD", "MC", "ME",
        "NL", "MK", "PL", "PT", "RO", "RU", "SM", "RS", "SK", "SI", "CH", "VA"
    )

    @Test
    fun anchorsCoverTheOriginalSetAndTheLookAlikes() {
        val codes = allCountries.map { it.code }.toSet()
        assertTrue("anchors for countries not in the catalog: ${anchors.keys - codes}", codes.containsAll(anchors.keys))
        assertTrue("missing anchors: ${requiredAnchors - anchors.keys}", anchors.keys.containsAll(requiredAnchors))
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

    /**
     * SHA-256 of each flag file, written by tools/flag-checksums.py after the images were looked at. A
     * swapped or re-rendered flag fails here until someone checks it and regenerates the file.
     */
    private val checkedFiles: Map<String, String> =
        javaClass.getResourceAsStream("/flag-checksums.txt")!!.bufferedReader().readLines()
            .filter { it.isNotBlank() }
            .associate { line -> line.substringBefore(' ') to line.substringAfter(' ') }

    @Test
    fun everyFlagFileIsExactlyTheOneThatWasChecked() {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val changed = allCountries.filter {
            val bytes = resources.openRawResource(flagArtFor(it.code)!!).use { stream -> stream.readBytes() }
            digest.digest(bytes).joinToString("") { b -> "%02x".format(b) } != checkedFiles[it.code]
        }.map { it.code }
        assertTrue("flag files changed (look at them, then run tools/flag-checksums.py): $changed", changed.isEmpty())
        assertEquals(allCountries.map { it.code }.toSet(), checkedFiles.keys)
    }

    @Test
    fun everyBundledFlagFileBelongsToACatalogCountry() {
        // No orphan artwork, and FlagArt.kt (generated by tools/gen-flag-art.py) points each code at its file.
        val bundled = R.drawable::class.java.fields.map { it.name }.filter { it.startsWith("flag_") }.toSet()
        assertEquals(allCountries.map { "flag_${it.code.lowercase()}" }.toSet(), bundled)
        allCountries.forEach {
            val byName = resources.getIdentifier("flag_${it.code.lowercase()}", "drawable", resources.getResourcePackageName(flagArtFor(it.code)!!))
            assertEquals(it.code, byName, flagArtFor(it.code))
        }
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
