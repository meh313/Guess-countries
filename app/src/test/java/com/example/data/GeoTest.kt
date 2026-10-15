package com.example.data

import com.example.data.model.Geo
import com.example.quiz.allCountries
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * The world map asset that `tools/gen-worldmap.py` writes from Natural Earth, and `Geo.kt`, its bounding boxes.
 * Read straight from the source tree (the working directory of the unit tests is the module), as the migration
 * test reads the schemas; Robolectric only for the real org.json.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class GeoTest {

    private val file = File("src/main/assets/world.json")
    private val root: JSONObject by lazy { JSONObject(file.readText()) }
    private val countries: JSONObject by lazy { root.getJSONObject("countries") }
    private val catalog: List<String> = allCountries.map { it.code }

    /** [east] exceeds 180 for a country spanning the antimeridian: its points west of 0 are read shifted by +360. */
    private data class Box(val west: Double, val south: Double, val east: Double, val north: Double) {
        fun contains(lon: Double, lat: Double, slack: Double = 0.0): Boolean {
            val x = if (east > 180 && lon < 0) lon + 360 else lon
            return x >= west - slack && x <= east + slack && lat >= south - slack && lat <= north + slack
        }
    }

    private fun box(code: String, key: String = "bbox"): Box = countries.getJSONObject(code).getJSONArray(key).let {
        Box(it.getDouble(0), it.getDouble(1), it.getDouble(2), it.getDouble(3))
    }

    private fun frame(code: String): Box = box(code, "frame")

    private fun rings(code: String): List<List<Pair<Double, Double>>> {
        val polys = countries.getJSONObject(code).getJSONArray("polys")
        return List(polys.length()) { i ->
            val ring: JSONArray = polys.getJSONArray(i)
            List(ring.length()) { j -> ring.getJSONArray(j).let { it.getDouble(0) to it.getDouble(1) } }
        }
    }

    private fun point(code: String, key: String): Pair<Double, Double>? {
        val country = countries.getJSONObject(code)
        if (country.isNull(key)) return null
        return country.getJSONArray(key).let { it.getDouble(0) to it.getDouble(1) }
    }

    private val codesInAsset: Set<String> get() = countries.keys().asSequence().toSet()

    @Test
    fun assetIsFormat1_fromNaturalEarth_andUnder700KB() {
        assertEquals(1, root.getInt("format"))
        assertTrue(root.getString("source").startsWith("Natural Earth"))
        assertTrue("${file.length()} bytes", file.length() < 700 * 1024)
    }

    @Test
    fun everyCatalogCodeHasAPlaceAndPolygons_andNothingElseIsThere() {
        assertEquals(catalog.toSet(), codesInAsset)
        assertEquals(catalog.toSet(), Geo.byCode.keys)
        catalog.forEach { code ->
            assertNotNull("$code has no Place", Geo.of(code))
            assertEquals(code, Geo.of(code)!!.code)
            val rings = rings(code)
            assertTrue("$code has no polygon", rings.isNotEmpty())
            rings.forEach { ring ->
                assertTrue("$code has a ring of ${ring.size} points", ring.size >= 4)
                ring.forEach { (lon, lat) ->
                    assertTrue("$code: $lon", lon >= -180.0 && lon <= 180.0)
                    assertTrue("$code: $lat", lat >= -90.0 && lat <= 90.0)
                }
            }
        }
        assertNull(Geo.of("ZZ"))
    }

    @Test
    fun everyPolygonPointLiesInsideItsBbox() {
        catalog.forEach { code ->
            val box = box(code)
            rings(code).forEach { ring ->
                ring.forEach { (lon, lat) -> assertTrue("$code: ($lon, $lat) outside $box", box.contains(lon, lat)) }
            }
        }
    }

    @Test
    fun labelAndCapitalLieInsideTheBbox_kingstownJustOffItsRoundedBox() {
        catalog.forEach { code ->
            val box = box(code)
            val label = point(code, "label")!!
            assertTrue("$code label $label outside $box", box.contains(label.first, label.second))
            val capital = point(code, "capital")
            if (code == "AQ") {
                assertNull(capital)
                return@forEach
            }
            assertNotNull("$code has no capital", capital)
            // Coastal capitals can fall a hundredth of a degree outside the rounded outline; only Kingstown does.
            assertTrue("$code capital $capital outside $box", box.contains(capital!!.first, capital.second, slack = 0.3))
            if (code != "VC") assertTrue("$code capital $capital outside $box", box.contains(capital.first, capital.second))
        }
    }

    @Test
    fun pointSizedCountriesAreTheMicrostates_asSquaresAroundTheCapital() {
        val points = catalog.filter { countries.getJSONObject(it).getBoolean("point") }.toSet()
        assertEquals(setOf("MC", "MH", "MV", "NR", "SM", "TV", "VA"), points)
        assertEquals(points, Geo.byCode.values.filter { it.point }.map { it.code }.toSet())
        points.forEach { code ->
            val rings = rings(code)
            assertEquals("$code should be one square", 1, rings.size)
            assertEquals("$code should be one square", 4, rings[0].size)
            val box = box(code)
            assertEquals(code, 0.08, box.east - box.west, 0.011)
            assertEquals(code, 0.08, box.north - box.south, 0.011)
            val label = point(code, "label")!!
            assertEquals(code, label, point(code, "capital"))
            assertEquals(code, (box.west + box.east) / 2, label.first, 0.011)
        }
    }

    @Test
    fun russiaAndFijiCrossTheAntimeridian_noBoxIsInsideOut() {
        assertTrue(box("RU").east > 180)
        assertTrue(box("FJ").east > 180)
        assertEquals(setOf("FJ", "KI", "NZ", "RU", "US"), catalog.filter { box(it).east > 180 }.toSet())
        catalog.forEach { code ->
            val box = box(code)
            assertTrue("$code: $box", box.east > box.west && box.north > box.south)
            assertTrue("$code: $box", box.west >= -180.0 && box.west <= 180.0 && box.east <= 360.0)
            assertTrue("$code: $box", box.south >= -90.0 && box.north <= 90.0)
        }
        // Antarctica rings the pole: the whole band, not a shifted frame.
        assertEquals(Box(-180.0, -90.0, 180.0, box("AQ").north), box("AQ"))
        assertFalse(box("AQ").north > -60.0)
    }

    @Test
    fun frameLiesInsideTheBbox_andHoldsTheLabelAndTheCapital() {
        catalog.forEach { code ->
            val box = box(code)
            val frame = frame(code)
            assertTrue("$code: $frame", frame.east > frame.west && frame.north >= frame.south)
            // A frame widened to a capital just off the rounded box (Kingstown) may overshoot it by a hundredth.
            val slack = 0.02
            assertTrue("$code: $frame", frame.south >= box.south - slack && frame.north <= box.north + slack)
            if (frame.east > 180) {
                assertTrue("$code: $frame outside $box", box.east > 180 && frame.west >= box.west - slack && frame.east <= box.east + slack)
            } else if (box.east > 180) {
                // The rings kept all lie west of the antimeridian; the frame is back in -180..180 (the 48 states).
                assertTrue("$code: $frame", frame.west >= -180 && frame.east <= 180)
            } else {
                assertTrue("$code: $frame outside $box", frame.west >= box.west - slack && frame.east <= box.east + slack)
            }
            val label = point(code, "label")!!
            assertTrue("$code label $label outside $frame", frame.contains(label.first, label.second))
            point(code, "capital")?.let { (lon, lat) ->
                assertTrue("$code capital ($lon, $lat) outside $frame", frame.contains(lon, lat, slack = 0.3))
            }
        }
        countries.getJSONObject("AQ").let { assertEquals(it.getJSONArray("bbox").toString(), it.getJSONArray("frame").toString()) }
    }

    @Test
    fun frameLeavesOutTheFarTerritories() {
        // Metropolitan France without French Guiana or Réunion; the 48 states without Alaska or Hawaii; Chile without
        // Easter Island; Australia without Heard, Christmas or Cocos; Spain without the Canaries; Portugal without the
        // Azores; Ecuador without the Galápagos; Norway without Svalbard. The point squares frame themselves.
        val fr = frame("FR")
        assertTrue("$fr", fr.west >= -6 && fr.south >= 41 && fr.east <= 10 && fr.north <= 52)
        assertTrue("${frame("US")}", frame("US").east < 0 && frame("US").west > -130 && frame("US").south > 24)
        assertTrue("${frame("CL")}", frame("CL").west > -80)
        assertTrue("${frame("AU")}", frame("AU").west > 110 && frame("AU").south > -45)
        assertTrue("${frame("ES")}", frame("ES").west > -10 && frame("ES").south > 35)
        assertTrue("${frame("PT")}", frame("PT").west > -10)
        assertTrue("${frame("EC")}", frame("EC").west > -82)
        assertTrue("${frame("NO")}", frame("NO").north < 72)
        assertTrue("${frame("NL")}", frame("NL").west > 3)
        // Whole where the islands chain: Indonesia, the Bahamas, Japan; and Malaysia keeps both halves.
        assertEquals(box("ID"), frame("ID"))
        assertEquals(box("BS"), frame("BS"))
        assertEquals(box("JP"), frame("JP"))
        assertEquals(box("MY"), frame("MY"))
        assertEquals(box("VA"), frame("VA"))
        catalog.forEach { code ->
            val place = Geo.of(code)!!
            assertEquals(code, frame(code), Box(place.frameWest, place.frameSouth, place.frameEast, place.frameNorth))
        }
    }

    @Test
    fun theAssetsNeighboursAreWhatNeighboursKtSays() {
        for (code in catalog) {
            val listed = countries.getJSONObject(code).getJSONArray("neighbours")
            val fromAsset = List(listed.length()) { listed.getString(it) }
            assertEquals(code, fromAsset, com.example.data.model.Neighbours.of(code))
        }
    }

    @Test
    fun geoKtCarriesTheSameNumbersAsTheAsset() {
        catalog.forEach { code ->
            val place = Geo.of(code)!!
            val box = box(code)
            assertEquals(code, box, Box(place.west, place.south, place.east, place.north))
            assertEquals(code, point(code, "label"), place.labelLon to place.labelLat)
            val capital = point(code, "capital")
            assertEquals(code, capital?.first, place.capitalLon)
            assertEquals(code, capital?.second, place.capitalLat)
            assertEquals(code, countries.getJSONObject(code).getBoolean("point"), place.point)
        }
        assertEquals(catalog.sorted(), Geo.byCode.keys.toList())
    }
}
