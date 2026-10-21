package com.example.data.model

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

/** A point on the globe, in degrees. */
data class LonLat(val lon: Float, val lat: Float)

/**
 * A box on the globe, in degrees. For a country whose rings straddle the antimeridian (Russia, Fiji, Kiribati, the
 * United States, New Zealand) the box is measured in a frame where the negative longitudes are shifted by +360, so
 * [east] exceeds 180 and the box stays in one piece (Fiji is about 176.9..181.8).
 */
data class Bbox(val west: Float, val south: Float, val east: Float, val north: Float) {
    val width: Float get() = east - west
    val height: Float get() = north - south
    val crossesAntimeridian: Boolean get() = east > 180f
}

/** One country's outlines, as the generated asset carries them. */
data class MapCountry(
    val code: String,
    /**
     * The outer rings, each flattened to lon0, lat0, lon1, lat1, …, in -180..180, and not closed (a ring that
     * repeated its first point at the end had it dropped). A ring with fewer than three points is left out.
     */
    val polys: List<FloatArray>,
    /**
     * Rings cut out of [polys], flattened the same way: the enclaves inside the country (Lesotho in South Africa, San
     * Marino and the Vatican in Italy), each the very ring the enclave is drawn with. Filled with the even-odd rule,
     * so the enclave keeps its own colour when this country is highlighted. Empty for most countries.
     */
    val holes: List<FloatArray>,
    /** The box of every ring, far-flung territories included. */
    val bbox: Bbox,
    /**
     * The box of the main landmass and the islands near it, without far-flung territories (France without French
     * Guiana, the United States without Alaska and Hawaii): what a zoomed map frames. The same as [bbox] when the
     * asset carries no frame.
     */
    val frame: Bbox,
    /** Where a name would sit: inside the biggest ring. */
    val label: LonLat,
    val capital: LonLat?,
    /** A microstate with no outline of its own at this scale, drawn as a tiny square around its capital. */
    val point: Boolean
)

/**
 * The outlines of every country, from the asset `world.json` (Natural Earth, 50m). About 197 countries and some
 * tens of thousands of points, so it is read once per process ([loadShared]) and off the main thread.
 *
 * [background] is the rest of the land, the places that are not in the catalog (Greenland, Western Sahara, Puerto
 * Rico, Hong Kong…), as bare rings: drawn in the land colour so they are not sea, but no country, so they are never
 * highlighted, looked up or counted.
 */
class WorldMapData(val countries: Map<String, MapCountry>, val background: List<FloatArray> = emptyList()) {

    operator fun get(code: String): MapCountry? = countries[code]

    companion object {
        const val ASSET = "world.json"

        @Volatile
        private var shared: WorldMapData? = null
        private val loading = Mutex()

        /** The data already read by [loadShared], or null before the first read finished. */
        fun peek(): WorldMapData? = shared

        /**
         * The process-wide copy, read from the asset on [Dispatchers.IO] the first time and kept. Callers from the
         * UI get it at once once it is in. An app without the asset gets an empty map, not a crash.
         */
        suspend fun loadShared(context: Context): WorldMapData =
            shared ?: loading.withLock {
                shared ?: withContext(Dispatchers.IO) { load(context.applicationContext) }.also { shared = it }
            }

        /** Whether the asset ships in this build: tests against the real data skip without it. */
        fun isBundled(context: Context): Boolean =
            try {
                context.assets.open(ASSET).close()
                true
            } catch (_: IOException) {
                false
            }

        /** Reads and parses the asset on the calling thread; empty when the asset is missing. */
        fun load(context: Context): WorldMapData =
            try {
                context.assets.open(ASSET).bufferedReader().use { parse(it.readText()) }
            } catch (_: IOException) {
                WorldMapData(emptyMap())
            }

        /**
         * Parses the asset format: `{"format":2,"countries":{"FR":{"polys":[[[lon,lat],…],…],"holes":[[[lon,lat],…],…],
         * "bbox":[w,s,e,n],"frame":[w,s,e,n],"label":[lon,lat],"capital":[lon,lat] or null,"point":false},…},
         * "background":[[[lon,lat],…],…]}`. `holes`, `frame` and `background` are optional (format 1 had none of them).
         */
        fun parse(json: String): WorldMapData {
            val root = JSONObject(json)
            val countries = root.getJSONObject("countries")
            val map = LinkedHashMap<String, MapCountry>(countries.length() * 2)
            for (code in countries.keys()) {
                val entry = countries.getJSONObject(code)
                val bbox = bbox(entry.getJSONArray("bbox"))
                map[code] = MapCountry(
                    code = code,
                    polys = rings(entry.getJSONArray("polys")),
                    holes = entry.optJSONArray("holes")?.let(::rings).orEmpty(),
                    bbox = bbox,
                    frame = entry.optJSONArray("frame")?.let(::bbox) ?: bbox,
                    label = lonLat(entry.getJSONArray("label")),
                    // optJSONArray is null for a JSON null and for a missing key alike.
                    capital = entry.optJSONArray("capital")?.let(::lonLat),
                    point = entry.optBoolean("point", false)
                )
            }
            return WorldMapData(map, root.optJSONArray("background")?.let(::rings).orEmpty())
        }

        /** Each ring of the list flattened ([ring]); the ones without area left out. */
        private fun rings(list: JSONArray): List<FloatArray> {
            val out = ArrayList<FloatArray>(list.length())
            for (i in 0 until list.length()) {
                ring(list.getJSONArray(i))?.let(out::add)
            }
            return out
        }

        private fun lonLat(pair: JSONArray) = LonLat(pair.getDouble(0).toFloat(), pair.getDouble(1).toFloat())

        private fun bbox(box: JSONArray) =
            Bbox(box.getDouble(0).toFloat(), box.getDouble(1).toFloat(), box.getDouble(2).toFloat(), box.getDouble(3).toFloat())

        /** The ring flattened, its closing point dropped when it repeats the first; null when it has no area. */
        private fun ring(points: JSONArray): FloatArray? {
            var n = points.length()
            if (n >= 2) {
                val first = points.getJSONArray(0)
                val last = points.getJSONArray(n - 1)
                if (first.getDouble(0) == last.getDouble(0) && first.getDouble(1) == last.getDouble(1)) n--
            }
            if (n < 3) return null
            val out = FloatArray(n * 2)
            for (i in 0 until n) {
                val p = points.getJSONArray(i)
                out[2 * i] = p.getDouble(0).toFloat()
                out[2 * i + 1] = p.getDouble(1).toFloat()
            }
            return out
        }
    }
}
