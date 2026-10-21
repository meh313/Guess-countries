package com.example.data

import com.example.data.model.Bbox
import com.example.data.model.LonLat
import com.example.data.model.WorldMapData
import com.example.support.WorldMapFixture
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The parser of `world.json`, against the fixture in [WorldMapFixture]. */
/** Reads the asset format through org.json, which only Robolectric provides in a unit test. */
@RunWith(RobolectricTestRunner::class)
class WorldMapDataTest {

    private val data = WorldMapFixture.data

    @Test
    fun parse_readsEveryCountry_itsBoxLabelCapitalAndFlag() {
        assertEquals(setOf("AA", "BB", "XX", "PP", "FF", "SS", "AQ", "HH", "EE"), data.countries.keys)

        val aa = data["AA"]!!
        assertEquals(Bbox(-30f, -10f, 30f, 10f), aa.bbox)
        assertEquals(LonLat(0f, 0f), aa.label)
        assertEquals(LonLat(10f, 5f), aa.capital)
        assertFalse(aa.point)
        assertFalse(aa.bbox.crossesAntimeridian)

        assertNull("a JSON null capital", data["BB"]!!.capital)
        assertTrue(data["PP"]!!.point)
        assertNull(data["ZZ"])
    }

    @Test
    fun parse_flattensTheRings_andDropsTheClosingPoint() {
        // AA's ring repeats its first point; BB's does not. Both come out as four corners.
        assertEquals(1, data["AA"]!!.polys.size)
        assertArrayEquals(floatArrayOf(-30f, -10f, 30f, -10f, 30f, 10f, -30f, 10f), data["AA"]!!.polys[0], 0f)
        assertArrayEquals(floatArrayOf(60f, 20f, 80f, 20f, 80f, 40f, 60f, 40f), data["BB"]!!.polys[0], 0f)
    }

    @Test
    fun aCountryAcrossTheAntimeridian_keepsItsRingsInRange_andItsBoxInOnePiece() {
        val xx = data["XX"]!!

        assertEquals(2, xx.polys.size)
        xx.polys.forEach { ring -> ring.filterIndexed { i, _ -> i % 2 == 0 }.forEach { lon -> assertTrue("$lon", lon in -180f..180f) } }
        assertEquals(Bbox(170f, -10f, 190f, 10f), xx.bbox)
        assertTrue(xx.bbox.crossesAntimeridian)
        assertEquals(20f, xx.bbox.width, 0f)
    }

    @Test
    fun theFrame_isReadWhenPresent_andFallsBackToTheBox() {
        val ff = data["FF"]!!
        assertEquals(Bbox(-54f, 2f, 8f, 51f), ff.bbox)
        assertEquals("the frame leaves the far-flung territory out", Bbox(-5f, 42f, 8f, 51f), ff.frame)

        val aa = data["AA"]!!
        assertEquals("no frame in the asset: the box", aa.bbox, aa.frame)
    }

    @Test
    fun parse_dropsARingWithoutArea() {
        val parsed = WorldMapData.parse(
            """{"format":1,"countries":{"LL":{"polys":[[[0,0],[1,1]],[[0,0],[2,0],[2,2]]],"bbox":[0,0,2,2],"label":[1,1],"capital":null,"point":false}}}"""
        )

        assertEquals(1, parsed["LL"]!!.polys.size)
        assertArrayEquals(floatArrayOf(0f, 0f, 2f, 0f, 2f, 2f), parsed["LL"]!!.polys[0], 0f)
    }

    @Test
    fun theHoles_areReadAsRings_theEnclavesOwn() {
        val hh = data["HH"]!!
        assertEquals(1, hh.holes.size)
        assertArrayEquals("the hole is the enclave's ring", data["EE"]!!.polys[0], hh.holes[0], 0f)
        assertTrue("an empty list", data["EE"]!!.holes.isEmpty())
        assertTrue("no key: no holes", data["AA"]!!.holes.isEmpty())
    }

    @Test
    fun theBackgroundLand_isReadAsBareRings_noCountry() {
        assertEquals(1, data.background.size)
        assertArrayEquals(floatArrayOf(-170f, 30f, -150f, 30f, -150f, 50f, -170f, 50f), data.background[0], 0f)
        // A format 1 file has none.
        val old = WorldMapData.parse(
            """{"format":1,"countries":{"LL":{"polys":[[[0,0],[2,0],[2,2]]],"bbox":[0,0,2,2],"label":[1,1],"capital":null,"point":false}}}"""
        )
        assertTrue(old.background.isEmpty())
        assertTrue(old["LL"]!!.holes.isEmpty())
    }
}
