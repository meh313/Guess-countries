package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidPath
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
import com.example.ui.components.MapPaths
import com.example.ui.components.WorldMap
import com.example.ui.components.mapViewport
import com.example.ui.theme.WorldFlagsTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
    private var land = 0

    /** What is on screen; content is set once per test and follows these. */
    private var shownCountry by mutableStateOf<Country?>(null)
    private var shownZoom by mutableStateOf(true)
    private var shownData by mutableStateOf(WorldMapFixture.data)
    private var shownCapital by mutableStateOf(true)
    private var contentSet = false

    private fun show(country: Country, zoom: Boolean = true, data: WorldMapData = WorldMapFixture.data, showCapital: Boolean = true) {
        rule.runOnUiThread {
            shownCountry = country
            shownZoom = zoom
            shownData = data
            shownCapital = showCapital
        }
        if (!contentSet) {
            contentSet = true
            rule.setContent {
                WorldFlagsTheme {
                    primary = MaterialTheme.colorScheme.primary.toArgb()
                    sea = MaterialTheme.colorScheme.surfaceContainerLow.toArgb()
                    land = MaterialTheme.colorScheme.surfaceVariant.toArgb()
                    CompositionLocalProvider(LocalWorldMapData provides shownData) {
                        Box(Modifier.size(340.dp, 420.dp)) {
                            shownCountry?.let { WorldMap(it, Modifier.width(300.dp), aspectRatio = aspect, zoom = shownZoom, showCapital = shownCapital) }
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

    /** The share of the pixels in a box given in pixels that are [argb], give or take antialiasing. */
    private fun Bitmap.sharePx(argb: Int, left: Int, top: Int, right: Int, bottom: Int): Float =
        share(argb, left.toFloat() / width, top.toFloat() / height, right.toFloat() / width, bottom.toFloat() / height)

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
    fun aPointMicrostate_getsAMarkerInThePrimaryColour_evenWithTheCapitalHidden() {
        // The quiz hides the capital on the Map question; the Vatican-sized PP would be a speck without a marker.
        show(mapped("PP"), showCapital = false)

        val map = mapPixels()
        val density = rule.activity.resources.displayMetrics.density
        val cx = map.width / 2
        val cy = map.height / 2
        // PP's outline is 0.1° of the 6° viewport: 2.5dp each side of the centre. The marker is 5dp: pixels between
        // 3.5dp and 4.5dp from the centre are primary because of the marker alone.
        val near = (3.5f * density).toInt()
        val far = (4.5f * density).toInt()
        val half = density.toInt()
        assertTrue("east of the centre", map.sharePx(primary, cx + near, cy - half, cx + far, cy + half) > 0.9f)
        assertTrue("west of the centre", map.sharePx(primary, cx - far, cy - half, cx - near, cy + half) > 0.9f)
        // Past the marker and its ring is sea again.
        val past = (8f * density).toInt()
        assertTrue("past the marker", map.sharePx(sea, cx + past, cy - half, cx + past + half, cy + half) > 0.9f)
    }

    @Test
    fun aHostsHole_leavesItsEnclaveInTheLandColour_itsOverlappingRingsDoNot_andTheEnclaveIsHighlightedOnItsOwn() {
        val viewport = mapViewport(WorldMapFixture.data["HH"]!!, zoom = true, aspect = aspect)
        show(mapped("HH"))
        var map = mapPixels()
        // EE fills HH's hole, 26..34 × -34..-26; HH's own band runs from 20 to 26 on the west.
        val hole = listOf(viewport.fx(28f), viewport.fy(-28f), viewport.fx(32f), viewport.fy(-32f))
        assertTrue("the enclave is land", map.share(land, hole[0], hole[1], hole[2], hole[3]) > 0.95f)
        assertTrue("the enclave is not the host's", map.share(primary, hole[0], hole[1], hole[2], hole[3]) < 0.01f)
        assertTrue("the host round it", map.share(primary, viewport.fx(21f), viewport.fy(-28f), viewport.fx(25f), viewport.fy(-32f)) > 0.95f)
        // HH's two rings overlap at 20..24 × -38..-22: that is the host's too, not a second hole (an even-odd fill of
        // rings and holes together would leave it out).
        assertTrue("where its rings overlap", map.share(primary, viewport.fx(20.5f), viewport.fy(-24f), viewport.fx(23.5f), viewport.fy(-36f)) > 0.95f)

        show(mapped("EE"))
        map = mapPixels()
        val own = mapViewport(WorldMapFixture.data["EE"]!!, zoom = true, aspect = aspect)
        assertTrue("the enclave highlighted", map.share(primary, own.fx(28f), own.fy(-28f), own.fx(32f), own.fy(-32f)) > 0.95f)
    }

    /** Pixels a degree in a [raster]. */
    private val px = 4

    /** [path], in the map's degree space (y = −latitude), filled without antialiasing from [west], [north] on. */
    private fun raster(path: Path, west: Float, north: Float, widthDeg: Int, heightDeg: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(widthDeg * px, heightDeg * px, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply {
            scale(px.toFloat(), px.toFloat())
            translate(-west, north)
            drawPath(path.asAndroidPath(), Paint().apply { color = android.graphics.Color.BLACK })
        }
        return bitmap
    }

    /** Whether the point at [lon], [lat] is filled in a [raster] drawn from [west], [north]. */
    private fun Bitmap.filledAt(lon: Float, lat: Float, west: Float, north: Float): Boolean =
        getPixel(((lon - west) * px).toInt(), ((north - lat) * px).toInt()) ushr 24 != 0

    @Test
    fun whenThePathOpFails_theHostsFillStillLeavesItsHoleOut_andFillsWhereItsRingsOverlap() {
        // Skia's path op can fail on some geometry; the paths are built during composition, so that must not throw.
        val fallback = MapPaths.build(WorldMapFixture.data) { _, _ -> null }.byCode.getValue("HH")
        val difference = MapPaths.build(WorldMapFixture.data).byCode.getValue("HH")

        // HH's rings, 20..40 and 16..24 × -40..-20, are stored anticlockwise, and so is its hole at 26..34 × -34..-26.
        val (west, north) = 10f to -15f
        val drawn = raster(fallback.fill, west, north, 35, 30)
        assertTrue("the hole is left out", !drawn.filledAt(30f, -30f, west, north))
        assertTrue("the host round it", drawn.filledAt(37f, -30f, west, north))
        assertTrue("where its two rings overlap", drawn.filledAt(22f, -30f, west, north))
        assertTrue("its second ring alone", drawn.filledAt(17f, -30f, west, north))
        assertTrue("past it", !drawn.filledAt(12f, -30f, west, north) && !drawn.filledAt(42f, -30f, west, north))
        assertTrue("the same pixels as the difference", drawn.sameAs(raster(difference.fill, west, north, 35, 30)))
    }

    @Test
    fun whenThePathOpFails_theBundledSouthAfrica_stillLeavesLesothoOut() {
        // The asset's rings run clockwise, the fixture's anticlockwise: the fallback winds them by their area either way.
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertTrue("world.json must be bundled", WorldMapData.isBundled(context))
        val za = MapPaths.build(WorldMapData.load(context)) { _, _ -> null }.byCode.getValue("ZA")

        val (west, north) = 15f to -20f
        val drawn = raster(za.fill, west, north, 20, 20)
        assertTrue("Lesotho is not South Africa", !drawn.filledAt(28.3f, -29.5f, west, north))
        assertTrue("the Karoo is", drawn.filledAt(23f, -32f, west, north))
        assertTrue("the Free State, north of Lesotho, is", drawn.filledAt(27f, -28f, west, north))
    }

    @Test
    fun theBackgroundLand_isLandNotSea_andNoCountry() {
        // The fixture's background ring, -170..-150 × 30..50, on the whole-world map: land colour, never highlighted.
        show(mapped("AA"), zoom = false)
        val map = mapPixels()
        val viewport = mapViewport(WorldMapFixture.data["AA"]!!, zoom = false, aspect = aspect)
        val (l, t, r, b) = listOf(viewport.fx(-167f), viewport.fy(47f), viewport.fx(-153f), viewport.fy(33f))
        assertTrue("land", map.share(land, l, t, r, b) > 0.95f)
        assertTrue("not sea", map.share(sea, l, t, r, b) < 0.01f)
        assertTrue("not a country", WorldMapFixture.data.countries.values.none { it.bbox.west <= -167f && it.bbox.east >= -153f && it.bbox.north >= 47f })
    }

    @Test
    fun theMapIsAnImageDescribedByTheCountryAndItsRegion() {
        show(france.copy(code = "AA"))

        rule.onNodeWithTag("world_map")
            .assertContentDescriptionEquals("Map: France, in Europe · Western Europe")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Image))
    }

    @Test
    fun theDescription_saysTheAmericasInASentence() {
        show(france.copy(code = "AA", continent = "Americas", subregion = "South America"))

        rule.onNodeWithTag("world_map").assertContentDescriptionEquals("Map: France, in the Americas · South America")
    }

    @Test
    @Config(qualifiers = "fr-w360dp-h640dp-xxhdpi")
    fun theDescription_namesTheContinentInTheLanguageOfTheScreens() {
        // The continent is the one the cards and chips show; the subregion, like the name, is the catalog's English.
        show(france.copy(code = "AA", continent = "Africa", subregion = "Western Africa"))

        rule.onNodeWithTag("world_map").assertContentDescriptionEquals("Carte\u00a0: France, en Afrique · Western Africa")
    }

    @Test
    @Config(qualifiers = "fr-w360dp-h640dp-xxhdpi")
    fun theFrenchDescription_putsTheAmericasInTheSingularAfterEn() {
        show(france.copy(code = "AA", continent = "Americas", subregion = "South America"))

        rule.onNodeWithTag("world_map").assertContentDescriptionEquals("Carte\u00a0: France, en Amérique · South America")
    }

    @Test
    @Config(qualifiers = "ar-w360dp-h640dp-xxhdpi")
    fun theArabicDescription_putsTheAmericasInTheGenitiveAfterIn() {
        show(france.copy(code = "AA", continent = "Americas", subregion = "South America"))

        rule.onNodeWithTag("world_map").assertContentDescriptionEquals("خريطة: France، في الأمريكتين · South America")
    }

    @Test
    @Config(qualifiers = "de-w360dp-h640dp-xxhdpi")
    fun theGermanDescription_givesAntarcticaItsArticleAfterIn() {
        // Antarctica's subregion repeats its continent, so the region is the continent alone.
        show(france.copy(code = "AA", name = "Antarctica", continent = "Antarctica", subregion = "Antarctica"))

        rule.onNodeWithTag("world_map").assertContentDescriptionEquals("Karte: Antarctica, in der Antarktis")
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
        // A failure, not a skip: without the asset in the build the app would ship with empty maps.
        assertTrue("world.json must be bundled", WorldMapData.isBundled(context))
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

    @Test
    fun theBundledAsset_leavesLesothoOutOfSouthAfrica_andDrawsGreenlandAsLand() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertTrue("world.json must be bundled", WorldMapData.isBundled(context))
        val data = WorldMapData.load(context)

        // South Africa highlighted: Lesotho, its enclave, stays land; the Karoo round it is South Africa's colour.
        val za = mapViewport(data["ZA"]!!, zoom = true, aspect = aspect)
        show(mapped("ZA"), data = data)
        var map = mapPixels()
        val lesotho = listOf(za.fx(28.0f), za.fy(-29.3f), za.fx(28.6f), za.fy(-29.8f))
        assertTrue("Lesotho is land", map.share(land, lesotho[0], lesotho[1], lesotho[2], lesotho[3]) > 0.9f)
        assertTrue("Lesotho is not South Africa", map.share(primary, lesotho[0], lesotho[1], lesotho[2], lesotho[3]) < 0.02f)
        assertTrue("the Karoo is", map.share(primary, za.fx(22.5f), za.fy(-31.5f), za.fx(23.5f), za.fy(-32.5f)) > 0.9f)

        // The whole world: Greenland, which is no catalog country, is land and not sea.
        val world = mapViewport(data["BR"]!!, zoom = false, aspect = aspect)
        show(mapped("BR"), zoom = false, data = data)
        map = mapPixels()
        val greenland = listOf(world.fx(-45f), world.fy(75f), world.fx(-38f), world.fy(70f))
        assertTrue("Greenland is land", map.share(land, greenland[0], greenland[1], greenland[2], greenland[3]) > 0.9f)
        assertTrue("Greenland is not sea", map.share(sea, greenland[0], greenland[1], greenland[2], greenland[3]) < 0.02f)
    }

    @Test
    fun theBundledAsset_highlightsCrimeaWithUkraine_theGolanWithSyria_andStopsMoroccoAt27Degrees40() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertTrue("world.json must be bundled", WorldMapData.isBundled(context))
        val data = WorldMapData.load(context)

        val ua = mapViewport(data["UA"]!!, zoom = true, aspect = aspect)
        show(mapped("UA"), data = data)
        var map = mapPixels()
        assertTrue("Simferopol is Ukraine's colour", map.share(primary, ua.fx(33.9f), ua.fy(45.1f), ua.fx(34.5f), ua.fy(44.8f)) > 0.9f)

        val ru = mapViewport(data["RU"]!!, zoom = true, aspect = aspect)
        show(mapped("RU"), data = data)
        map = mapPixels()
        // Russia's frame is some 240° wide, so a degree is under four pixels: the box is most of Crimea, too small to
        // tell land from coast stroke, but it would be mostly Russia's colour if Crimea were drawn in Russia.
        val crimea = listOf(ru.fx(33.2f), ru.fy(45.6f), ru.fx(35.2f), ru.fy(44.9f))
        assertTrue("Crimea is not Russia's colour", map.share(primary, crimea[0], crimea[1], crimea[2], crimea[3]) < 0.05f)

        val sy = mapViewport(data["SY"]!!, zoom = true, aspect = aspect)
        show(mapped("SY"), data = data)
        map = mapPixels()
        assertTrue("Katzrin is Syria's colour", map.share(primary, sy.fx(35.66f), sy.fy(33.1f), sy.fx(35.78f), sy.fy(32.9f)) > 0.9f)
        assertTrue("Tiberias is not", map.share(primary, sy.fx(35.45f), sy.fy(32.85f), sy.fx(35.53f), sy.fy(32.75f)) < 0.02f)

        val ma = mapViewport(data["MA"]!!, zoom = true, aspect = aspect)
        show(mapped("MA"), data = data)
        map = mapPixels()
        val laayoune = listOf(ma.fx(-12.8f), ma.fy(27.4f), ma.fx(-12.0f), ma.fy(26.9f))
        assertTrue("Laayoune's land is not Morocco's colour", map.share(primary, laayoune[0], laayoune[1], laayoune[2], laayoune[3]) < 0.02f)
        assertTrue("but it is land", map.share(land, laayoune[0], laayoune[1], laayoune[2], laayoune[3]) > 0.9f)
        assertTrue("Morocco north of the line is", map.share(primary, ma.fx(-6.5f), ma.fy(33.5f), ma.fx(-5.5f), ma.fy(33.0f)) > 0.9f)
    }
}
