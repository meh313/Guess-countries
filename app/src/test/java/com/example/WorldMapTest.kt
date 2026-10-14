package com.example

import android.content.Context
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.Country
import com.example.data.model.MapCountry
import com.example.ui.components.MapViewport
import com.example.data.model.WorldMapData
import com.example.quiz.allCountries
import com.example.support.WorldMapFixture
import com.example.support.crop
import com.example.support.drawWindow
import com.example.ui.components.LocalWorldMapData
import com.example.ui.components.WorldMap
import com.example.ui.components.mapViewport
import com.example.ui.theme.WorldFlagsTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/** What WorldMap puts on screen, read back from the rendered pixels, over the fixture map and over the real asset. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w360dp-h640dp-xxhdpi")
class WorldMapTest {

    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val aspect = 1.5f
    private val france = allCountries.single { it.code == "FR" }

    /** A catalog country under the fixture's code, so the map looks the code up in the fixture. */
    private fun mapped(code: String): Country = allCountries.singleOrNull { it.code == code } ?: france.copy(code = code)

    private var primary = 0
    private var sea = 0

    /** What is on screen; content is set once per test and follows these. */
    private var shownCountry by mutableStateOf<Country?>(null)
    private var shownZoom by mutableStateOf(true)
    private var shownData by mutableStateOf(WorldMapFixture.data)
    private var contentSet = false

    private fun show(country: Country, zoom: Boolean = true, data: WorldMapData = WorldMapFixture.data) {
        rule.runOnUiThread {
            shownCountry = country
            shownZoom = zoom
            shownData = data
        }
        if (!contentSet) {
            contentSet = true
            rule.setContent {
                WorldFlagsTheme {
                    primary = MaterialTheme.colorScheme.primary.toArgb()
                    sea = MaterialTheme.colorScheme.surfaceContainerLow.toArgb()
                    CompositionLocalProvider(LocalWorldMapData provides shownData) {
                        Box(Modifier.size(340.dp, 420.dp)) {
                            shownCountry?.let { WorldMap(it, Modifier.width(300.dp), aspectRatio = aspect, zoom = shownZoom) }
                        }
                    }
                }
            }
        }
        rule.waitForIdle()
    }

    private fun mapPixels(): Bitmap =
        rule.drawWindow().crop(rule.onNodeWithTag("world_map", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot)

    /** The share of the pixels in the box (edges as fractions of the bitmap) that are [argb], give or take antialiasing. */
    private fun Bitmap.share(argb: Int, left: Float, top: Float, right: Float, bottom: Float): Float {
        var hits = 0
        var total = 0
        for (y in (top * height).toInt() until (bottom * height).toInt()) {
            for (x in (left * width).toInt() until (right * width).toInt()) {
                if (close(getPixel(x, y), argb)) hits++
                total++
            }
        }
        return if (total == 0) 0f else hits.toFloat() / total
    }

    private fun close(a: Int, b: Int): Boolean =
        abs(((a shr 16) and 0xFF) - ((b shr 16) and 0xFF)) <= 12 &&
            abs(((a shr 8) and 0xFF) - ((b shr 8) and 0xFF)) <= 12 &&
            abs((a and 0xFF) - (b and 0xFF)) <= 12

    /** Where a longitude falls across the box, as a fraction, under [viewport]. */
    private fun MapViewport.fx(lon: Float) = ((lon - west) / width).coerceIn(0f, 1f)

    /** Where a latitude falls down the box, as a fraction, under [viewport]. */
    private fun MapViewport.fy(lat: Float) = ((north - lat) / height).coerceIn(0f, 1f)

    @Test
    fun theCountryIsFilledWithThePrimaryColourInsideItsBox_andNowhereOutsideIt() {
        val place = WorldMapFixture.data["AA"]!!
        show(mapped("AA"))

        val map = mapPixels()
        val viewport = mapViewport(place, zoom = true, aspect = aspect)
        // 60° wide, so the padded frame is 90°: no inset to put the primary colour elsewhere.
        assertTrue(viewport.width >= 60f)
        val (l, t, r, b) = listOf(viewport.fx(-30f), viewport.fy(10f), viewport.fx(30f), viewport.fy(-10f))
        val m = 0.02f
        assertTrue("inside", map.share(primary, l + m, t + m, r - m, b - m) > 0.97f)
        assertTrue("above", map.share(primary, 0f, 0f, 1f, t - m) < 0.002f)
        assertTrue("below", map.share(primary, 0f, b + m, 1f, 1f) < 0.002f)
        assertTrue("left", map.share(primary, 0f, 0f, l - m, 1f) < 0.002f)
        assertTrue("right", map.share(primary, r + m, 0f, 1f, 1f) < 0.002f)
    }

    @Test
    fun aCountryAcrossTheAntimeridian_isDrawnInOnePiece() {
        val place = WorldMapFixture.data["XX"]!!
        show(mapped("XX"))

        val map = mapPixels()
        val viewport = mapViewport(place, zoom = true, aspect = aspect)
        assertEquals("the box is centred on the antimeridian", 180f, (viewport.west + viewport.east) / 2, 0.01f)
        // The ring west of 180°, the ring east of it, and the column where they meet: all filled.
        listOf(viewport.fx(175f), 0.5f, viewport.fx(185f)).forEach { x ->
            assertTrue("column at $x", map.share(primary, x - 0.01f, 0.4f, x + 0.01f, 0.6f) > 0.98f)
        }
        // Past the country on either side is not primary.
        assertTrue(map.share(primary, viewport.fx(165f) - 0.03f, 0.4f, viewport.fx(165f) - 0.01f, 0.6f) < 0.01f)
    }

    @Test
    fun theWholeWorld_leavesSeaAtThePoles_andStillShowsTheCountry() {
        val place = WorldMapFixture.data["AA"]!!
        show(mapped("AA"), zoom = false)

        val map = mapPixels()
        val viewport = mapViewport(place, zoom = false, aspect = aspect)
        assertTrue("the top rows", map.share(sea, 0.05f, 0.01f, 0.95f, 0.05f) > 0.98f)
        assertTrue("the bottom rows", map.share(sea, 0.05f, 0.95f, 0.95f, 0.99f) > 0.98f)
        // West of the capital dot at (10, 5): the band sampled is country fill only.
        val equator = map.share(primary, 0.43f, viewport.fy(8f), 0.49f, viewport.fy(-8f))
        assertTrue("the equator: $equator primary between ${viewport.fy(8f)} and ${viewport.fy(-8f)}", equator > 0.95f)
    }

    @Test
    fun theMapIsAnImageDescribedByTheCountryAndItsRegion() {
        show(france.copy(code = "AA"))

        rule.onNodeWithTag("world_map")
            .assertContentDescriptionEquals("Map: France, in ${france.regionLabel}")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Image))
    }

    @Test
    fun aCountryTheDataDoesNotKnow_getsNoMap() {
        show(mapped("ZZ"))

        rule.onAllNodesWithTag("world_map").assertCountEquals(0)
    }

    @Test
    fun theZoomedViewport_framesTheMainland_notTheFarFlungTerritory() {
        val ff = WorldMapFixture.data["FF"]!!
        val viewport = mapViewport(ff, zoom = true, aspect = aspect)

        // The 13° × 9° frame padded by a quarter each side is 19.5° × 13.5°, widened to the aspect: 20.25° × 13.5°.
        assertEquals(13.5f, viewport.height, 0.01f)
        assertEquals(20.25f, viewport.width, 0.01f)
        assertEquals(1.5f, (viewport.west + viewport.east) / 2, 0.01f)
        assertTrue("French Guiana is out of frame", viewport.west > -20f)
    }

    @Test
    fun theZoomedViewport_hasAFloor_aPointSize_andAPolarBand() {
        val speck = mapViewport(WorldMapFixture.data["SS"]!!, zoom = true, aspect = aspect)
        assertEquals(12f, speck.width, 0.01f)
        assertEquals(8f, speck.height, 0.01f)
        assertEquals(100.5f, (speck.west + speck.east) / 2, 0.01f)

        val point = mapViewport(WorldMapFixture.data["PP"]!!, zoom = true, aspect = aspect)
        assertEquals(6f, point.width, 0.01f)
        assertEquals(4f, point.height, 0.01f)
        assertEquals(12.45f, (point.west + point.east) / 2, 0.01f)

        val polar = mapViewport(WorldMapFixture.data["AQ"]!!, zoom = true, aspect = aspect)
        assertEquals(-180f, polar.west, 0f)
        assertEquals(180f, polar.east, 0f)
        assertEquals("the band sits at the bottom, the rest of the world above it", -90f, polar.south, 0f)
        assertEquals(240f, polar.height, 0.01f)
    }

    @Test
    fun theBundledAsset_rendersTheAwkwardCountries_andKeepsBrazilInsideItsBox() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assumeTrue("world.json is not bundled yet", WorldMapData.isBundled(context))
        val data = WorldMapData.load(context)

        // France (a far-flung territory), the Vatican (a point), Kiribati (astride the antimeridian) and Antarctica.
        listOf("FR", "VA", "KI", "AQ").forEach { code ->
            assertTrue("$code is not in the asset", data[code] != null)
            show(mapped(code), data = data)
            mapPixels()
        }

        val brazil: MapCountry = data["BR"]!!
        show(mapped("BR"), data = data)
        val map = mapPixels()
        val viewport = mapViewport(brazil, zoom = true, aspect = aspect)
        val box = brazil.bbox
        val (l, t, r, b) = listOf(viewport.fx(box.west), viewport.fy(box.north), viewport.fx(box.east), viewport.fy(box.south))
        val m = 0.015f
        assertTrue("Brazil fills a good part of its box", map.share(primary, l, t, r, b) > 0.3f)
        // Nothing primary outside the box but the inset, which sits in the bottom-end corner.
        assertTrue("above", map.share(primary, 0f, 0f, 1f, t - m) < 0.003f)
        assertTrue("left", map.share(primary, 0f, 0f, l - m, 0.8f) < 0.003f)
        assertTrue("right", map.share(primary, r + m, 0f, 1f, 0.8f) < 0.003f)
        assertTrue("below", map.share(primary, 0f, b + m, 0.65f, 1f) < 0.003f)
    }
}
