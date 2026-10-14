package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.Country
import com.example.data.model.LonLat
import com.example.data.model.MapCountry
import com.example.data.model.WorldMapData

/**
 * The outlines a [WorldMap] draws. Null (the default) means the bundled asset, read once off the main thread; a
 * test provides a small fixture here so it needs neither the asset nor a background read.
 */
val LocalWorldMapData = staticCompositionLocalOf<WorldMapData?> { null }

/**
 * The world's outlines for the maps on this screen: the fixture from [LocalWorldMapData] when there is one, else
 * the shared copy of the asset. Null while the asset is still being read; it arrives on a later frame.
 */
@Composable
fun rememberWorldMapData(): WorldMapData? {
    val injected = LocalWorldMapData.current
    val context = LocalContext.current
    val loaded by produceState(initialValue = WorldMapData.peek(), context, injected) {
        if (injected == null && value == null) value = WorldMapData.loadShared(context)
    }
    return injected ?: loaded
}

/** The part of the globe a map box shows, in degrees, as [mapViewport] computes it. */
data class MapViewport(val west: Float, val south: Float, val east: Float, val north: Float) {
    val width: Float get() = east - west
    val height: Float get() = north - south

    /**
     * The same centre, widened or heightened to the box's [aspect] (width over height) so a degree is as long across
     * as it is down. With [anchorSouth] the box grows upward only, keeping [south] where it is.
     */
    fun fitTo(aspect: Float, anchorSouth: Boolean = false): MapViewport {
        if (width / height < aspect) {
            val w = height * aspect
            val cx = (west + east) / 2
            return MapViewport(cx - w / 2, south, cx + w / 2, north)
        }
        val h = width / aspect
        if (anchorSouth) return MapViewport(west, south, east, south + h)
        val cy = (south + north) / 2
        return MapViewport(west, cy - h / 2, east, cy + h / 2)
    }
}

/** The whole world as the maps show it: Antarctica, below 60° south, is cut off. */
val WorldBand = MapViewport(-180f, -60f, 180f, 85f)

/** A country whose frame lies below 55° south: Antarctica. It gets the polar band instead of the cut-off world. */
private val MapCountry.isPolar: Boolean get() = frame.north <= -55f

/**
 * What a map box of the given [aspect] (width over height) shows for [country]. Zoomed: its [MapCountry.frame] (the
 * main landmass, not the far-flung territories) padded by a quarter on each side, at least 12° wide and 8° tall, centred; a [MapCountry.point] microstate gets 6° × 4° around its
 * capital; Antarctica the polar band, -180..180 × -90..-55. Not zoomed: [WorldBand]. Each is then widened or
 * heightened to the aspect, so the box's edges may run past the poles (sea) or past 180° (the world is drawn again
 * there, shifted by 360°).
 */
fun mapViewport(country: MapCountry, zoom: Boolean, aspect: Float): MapViewport {
    if (!zoom) return WorldBand.fitTo(aspect)
    if (country.isPolar) return MapViewport(-180f, -90f, 180f, -55f).fitTo(aspect, anchorSouth = true)
    if (country.point) {
        val at = country.capital ?: country.label
        return MapViewport(at.lon - 3f, at.lat - 2f, at.lon + 3f, at.lat + 2f).fitTo(aspect)
    }
    val box = country.frame
    val w = maxOf(box.width * 1.5f, 12f)
    val h = maxOf(box.height * 1.5f, 8f)
    val cx = (box.west + box.east) / 2
    val cy = (box.south + box.north) / 2
    return MapViewport(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2).fitTo(aspect)
}

/** The longitude shifts (−360, 0, +360) at which a copy of the world falls inside the viewport. */
private fun MapViewport.worldOffsets(): List<Float> =
    listOf(-360f, 0f, 360f).filter { off -> -180f + off < east && 180f + off > west }

/**
 * Every country's rings as Compose paths in degree space (x = longitude, y = −latitude), built once per data set and
 * drawn through a canvas transform, so a new viewport costs no new geometry. [land] holds all rings in one path for
 * the two draw calls that paint the land; [byCode] the one country that is highlighted.
 */
internal class MapPaths(val land: Path, val byCode: Map<String, Path>) {
    companion object {
        @Volatile
        private var cached: Pair<WorldMapData, MapPaths>? = null

        /** The paths for [data], from the one-entry cache or freshly built. The data is a singleton in the app, so one entry is enough. */
        fun of(data: WorldMapData): MapPaths = synchronized(this) {
            cached?.takeIf { it.first === data }?.second ?: build(data).also { cached = data to it }
        }

        private fun build(data: WorldMapData): MapPaths {
            val land = Path()
            val byCode = HashMap<String, Path>(data.countries.size * 2)
            data.countries.values.forEach { country ->
                val own = Path()
                country.polys.forEach { ring ->
                    addRing(own, ring)
                    addRing(land, ring)
                }
                byCode[country.code] = own
            }
            return MapPaths(land, byCode)
        }

        private fun addRing(path: Path, ring: FloatArray) {
            path.moveTo(ring[0], -ring[1])
            var i = 2
            while (i < ring.size) {
                path.lineTo(ring[i], -ring[i + 1])
                i += 2
            }
            path.close()
        }
    }
}

/**
 * Where a country is: an equirectangular map with every country's outline in a muted land colour on a sea
 * background, [country] filled in the primary colour and its capital marked, in a rounded box of [aspectRatio].
 *
 * [zoom] frames the country ([mapViewport]); off, the whole world is shown. A zoomed box narrower than 60° of
 * longitude carries a small world inset in its bottom-end corner with the framed area marked, so a small country is
 * placed on Earth as well as seen. A country that straddles the antimeridian is drawn whole: the world is painted
 * again shifted by 360°. While the asset loads the box is plain sea; a country the asset does not know composes
 * nothing at all. TalkBack hears "Map: France, in Europe · Western Europe", or [contentDescription] where the name
 * must not be said (the Map → Country question, whose answer it is).
 */
@Composable
fun WorldMap(
    country: Country,
    modifier: Modifier = Modifier,
    aspectRatio: Float = 16f / 10f,
    zoom: Boolean = true,
    showCapital: Boolean = true,
    testTag: String = "world_map",
    contentDescription: String? = null
) {
    val data = rememberWorldMapData()
    val place = data?.get(country.code)
    if (data != null && place == null) return

    val paths = remember(data) { data?.let { MapPaths.of(it) } }
    val scheme = MaterialTheme.colorScheme
    val sea = scheme.surfaceContainerLow
    val land = scheme.surfaceVariant
    val coast = scheme.outline.copy(alpha = 0.35f)
    val highlight = scheme.primary
    val capitalDot = scheme.tertiary
    val ring = scheme.surface
    val insetSea = scheme.surface
    val insetFrame = scheme.outline
    val description = contentDescription ?: stringResource(R.string.map_description, country.name, country.regionLabel)
    val shape = RoundedCornerShape(12.dp)

    Canvas(
        modifier = modifier
            .aspectRatio(aspectRatio)
            .clip(shape)
            .background(sea)
            .border(1.dp, Color.Black.copy(alpha = 0.12f), shape)
            .testTag(testTag)
            .semantics {
                role = Role.Image
                // The parameter of the same name shadows the semantics property here: say which one is meant.
                this.contentDescription = description
            }
    ) {
        if (place == null || paths == null) return@Canvas
        val box = Rect(Offset.Zero, size)
        val viewport = mapViewport(place, zoom, size.width / size.height)
        // The cut-off world hides Antarctica unless Antarctica is the country being shown.
        val band = if (!zoom && !place.isPolar) WorldBand else null
        drawWorld(paths, place.code, viewport, box, band, land, coast, highlight, 0.75.dp.toPx(), 1.5.dp.toPx())
        if (showCapital) {
            place.capital?.let { capital -> drawDot(capital, viewport, box, capitalDot, ring, 2.dp.toPx(), 1.dp.toPx()) }
        }
        if (zoom && viewport.width < 60f) {
            drawInset(paths, place, viewport, insetSea, land, coast, highlight, insetFrame, capitalDot, ring)
        }
    }
}

/**
 * Paints the land into [area]: all rings in [land] with a [coast] stroke, then [highlightCode]'s rings in
 * [highlight] with a stroke of the same colour (so an atoll too small to fill still shows, and the seam where a
 * country's rings meet at the antimeridian does not). [band] clips what is drawn to that part of the globe.
 */
private fun DrawScope.drawWorld(
    paths: MapPaths,
    highlightCode: String,
    viewport: MapViewport,
    area: Rect,
    band: MapViewport?,
    land: Color,
    coast: Color,
    highlight: Color,
    coastWidthPx: Float,
    highlightWidthPx: Float
) {
    val sx = area.width / viewport.width
    val sy = area.height / viewport.height
    val own = paths.byCode[highlightCode]
    clipRect(area.left, area.top, area.right, area.bottom) {
        viewport.worldOffsets().forEach { off ->
            // Path units are degrees (y down = south): a translate and a scale put them in the box.
            withTransform({
                translate(area.left + (off - viewport.west) * sx, area.top + viewport.north * sy)
                scale(sx, sy, Offset.Zero)
            }) {
                val paint: DrawScope.() -> Unit = {
                    drawPath(paths.land, land)
                    drawPath(paths.land, coast, style = Stroke(width = coastWidthPx / sx))
                    if (own != null) {
                        drawPath(own, highlight)
                        drawPath(own, highlight, style = Stroke(width = highlightWidthPx / sx, join = StrokeJoin.Round))
                    }
                }
                if (band != null) clipRect(band.west, -band.north, band.east, -band.south) { paint() } else paint()
            }
        }
    }
}

/** Where a point of the globe falls in [area] under [viewport], for each copy of the world the viewport shows. */
private fun project(point: LonLat, viewport: MapViewport, area: Rect): List<Offset> =
    viewport.worldOffsets().map { off ->
        Offset(
            area.left + (point.lon + off - viewport.west) / viewport.width * area.width,
            area.top + (viewport.north - point.lat) / viewport.height * area.height
        )
    }.filter { it.x >= area.left && it.x <= area.right && it.y >= area.top && it.y <= area.bottom }

/** A dot of [radiusPx] in [colour] with a ring of [ringPx] in [ringColour] around it, where [point] falls in [area]. */
private fun DrawScope.drawDot(point: LonLat, viewport: MapViewport, area: Rect, colour: Color, ringColour: Color, radiusPx: Float, ringPx: Float) {
    project(point, viewport, area).forEach { at ->
        drawCircle(ringColour, radiusPx + ringPx, at)
        drawCircle(colour, radiusPx, at)
    }
}

/**
 * The world inset in the bottom-end corner: three tenths of the width, the cut-off world with the country in the
 * highlight colour under a marker dot (its outline is a speck at this scale), and a rectangle around what the big
 * map frames.
 */
private fun DrawScope.drawInset(
    paths: MapPaths,
    place: MapCountry,
    framed: MapViewport,
    sea: Color,
    land: Color,
    coast: Color,
    highlight: Color,
    frame: Color,
    marker: Color,
    ring: Color
) {
    val margin = 6.dp.toPx()
    val width = size.width * 0.3f
    val height = width * WorldBand.height / WorldBand.width
    val left = if (layoutDirection == LayoutDirection.Rtl) margin else size.width - margin - width
    val top = size.height - margin - height
    val area = Rect(Offset(left, top), Size(width, height))
    drawRect(sea, area.topLeft, area.size)
    drawWorld(paths, place.code, WorldBand, area, WorldBand, land, coast, highlight, 0.5.dp.toPx(), 0.5.dp.toPx())
    drawDot(place.label, WorldBand, area, highlight, ring, 2.dp.toPx(), 1.dp.toPx())
    // The framed area, clamped to the band and never smaller than a visible square; a frame past 180° wraps round.
    val minSide = 4.dp.toPx()
    val south = framed.south.coerceIn(WorldBand.south, WorldBand.north)
    val north = framed.north.coerceIn(WorldBand.south, WorldBand.north)
    clipRect(area.left, area.top, area.right, area.bottom) {
        listOf(0f, -360f, 360f).forEach { off ->
            val x1 = area.left + (framed.west + off + 180f) / 360f * area.width
            val x2 = area.left + (framed.east + off + 180f) / 360f * area.width
            if (x2 < area.left || x1 > area.right) return@forEach
            val y1 = area.top + (WorldBand.north - north) / WorldBand.height * area.height
            val y2 = area.top + (WorldBand.north - south) / WorldBand.height * area.height
            val w = maxOf(x2 - x1, minSide)
            val h = maxOf(y2 - y1, minSide)
            drawRect(marker, Offset((x1 + x2 - w) / 2, (y1 + y2 - h) / 2), Size(w, h), style = Stroke(width = 1.5.dp.toPx()))
        }
    }
    drawRect(frame, area.topLeft, area.size, style = Stroke(width = 1.dp.toPx()))
}
