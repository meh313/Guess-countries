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

    /** SHA-256 of each flag file as checked by eye against the real flag. Update it when artwork is re-rendered. */
    private val checkedFiles: Map<String, String> = mapOf(
        "FR" to "9e3d17be1a20d3f23f0d6dc5bee279253b9081d384ae443986fbe1b23e725aae",
        "DE" to "56485bdaedeee404fe79bc76fa7dd5798ca94243353fe3de172808d369925166",
        "IT" to "c6290fe1ae26151357e739a37b06f300ec8a02bd7eb675b8b253e09adacb7d24",
        "GB" to "9d39f23aca01a6e2e474311235bf514041411577e451af470ed12828510e51bc",
        "ES" to "24916c90f0a01dae0ec370d94caa562a74abfab3d16d3998c7f58622503e87d0",
        "GR" to "43b965941641d3cd52e71b591e687f53b7badd52ec50e1b6e6fc54d2054110a3",
        "SE" to "25ae7cde3a5803085936911159750b9d3ab20359004214bd7375810aabe7b6dc",
        "NO" to "0efd023cbfafc130d747f74d317723599b823167bcf021e11b3871040fec50d4",
        "UA" to "57f3d78306cd0f767cb6c659c77241419576b22771b16bee96d63678f1442640",
        "US" to "7fb2d2ea8419f92836f8097488642f6130a150bc9f215348048f5e071bcffc57",
        "CA" to "971cc2082c9c8ee8fc9850a4a4bf99d8844c08b32c12c61f22474bacc7c66b62",
        "MX" to "0760e6c78a89919e4444ce642e2a2dc3d8049df96ce95a40ded45332ee278a0a",
        "BR" to "0862db322c6857b72b8137612c328d350a1186a195923fb700be457659ce8672",
        "AR" to "c7af18589946a6f9483f37fb1bd07208e4d159120d42ca8280a2f3863372307f",
        "CO" to "f042fa3dac7cb09d46b218877934b76f768c04d85b89b1f99b58f5be2a7ef28e",
        "CL" to "c075a87a98893ab04672c0c40180ee0371ebddf5d7d96e44408232f917b29137",
        "JP" to "09454141e9b26aa5579c74ec9892d5ff1e336afe1039e3803200b77d19db2217",
        "CN" to "f2ce60621876087ffc91633fdb36f81eb3de1c6a2cab775614e91948785803c3",
        "IN" to "b50d3b828496ce1910bd3a89264c0266529772c3797f4c9b22671a7a252d44f4",
        "KR" to "49310909eb4abd6b4c93b6f9a1d377dcb9be61fd99219932f2e41969ee8d1885",
        "VN" to "23e4bc9b9940f972f338821e2e8507acc4807f81fd214a9568021ea3eff6debd",
        "TH" to "7c74acf873c4bd19a38abece236eeb863f678e6d67f3e31ff42e744b20e7b50d",
        "SA" to "668a78e7a27df8b8ad7c557de6da18832945d73b9a0dc69bd25e1dd03e835f61",
        "EG" to "2b734a0e3212a6bba0b3d46705e0d0c226c68be6a48d16e39ea7f3c6f416c339",
        "KE" to "56c97a78ad922a5a1cb52680dacb35037dff358d902d648dcf666cfd21977864",
        "ZA" to "f93306bd78a0c404ca5dd8f828429bec03ff36a7e42414db5b9dd1a30e7225a1",
        "NG" to "dea2eea4afa80a7412310c1eacaf6874372ff375a3cb28e44d2b1881ec9dfa38",
        "MA" to "dd35f0875f29a60061955bb80963f23439a084dda32db63c4a95dc8b0102d58e",
        "TZ" to "234a1d06689f601f1bfa71cf8df971a4c7d7ab00d8c665ee3bad564fbfe56505",
        "AU" to "75ce4e2866e1dcdf8f58b2cf9d79d1e79b419e40ffdbcb4927107363e948f3cb",
        "NZ" to "b83987ff94127c7c7f1948cc5afef81f397ed80284e9d0ca19bc1d0cd1ebd6b2",
        "FJ" to "2e756ed680b1023d6f707568c61d7da0a8f3fdf5cf3d88988e511378a434a27c",
        "AQ" to "9c4c8f768cb818838cf682bcf13cea034cb18501f773893607d7d24e35a7c2fc",
    )

    @Test
    fun everyFlagFileIsExactlyTheOneThatWasChecked() {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val changed = allCountries.filter {
            val bytes = resources.openRawResource(flagArtFor(it.code)!!).use { stream -> stream.readBytes() }
            digest.digest(bytes).joinToString("") { b -> "%02x".format(b) } != checkedFiles[it.code]
        }.map { it.code }
        assertTrue("flag files changed (re-check them, then update checkedFiles): $changed", changed.isEmpty())
        assertEquals(allCountries.map { it.code }.toSet(), checkedFiles.keys)
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
