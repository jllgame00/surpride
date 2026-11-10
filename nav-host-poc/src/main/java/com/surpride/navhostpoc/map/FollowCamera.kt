package com.surpride.navhostpoc.map

import com.surpride.navhostpoc.guidance.*
import com.surpride.navhostpoc.tracking.*
import kotlin.math.*

data class MapPoint(val x: Double, val y: Double)

/** Slippy-map coordinates; latitude is north, longitude is east. No Android dependency. */
object WebMercator {
    private const val MAX_LATITUDE = 85.0511287798066
    fun project(point: RouteCoordinate, zoom: Double): MapPoint {
        val size = 256.0 * 2.0.pow(zoom)
        val latitude = Math.toRadians(point.latitude.coerceIn(-MAX_LATITUDE, MAX_LATITUDE))
        return MapPoint((point.longitude + 180.0) / 360.0 * size,
            ((1.0 - ln(tan(latitude) + 1.0 / cos(latitude)) / PI) / 2.0 * size).coerceIn(0.0, size))
    }
    fun unproject(point: MapPoint, zoom: Double): RouteCoordinate {
        val size = 256.0 * 2.0.pow(zoom)
        val longitude = ((point.x / size * 360.0) % 360.0 + 360.0) % 360.0 - 180.0
        return RouteCoordinate(longitude, Math.toDegrees(atan(sinh(PI * (1.0 - 2.0 * point.y / size)))))
    }
    fun metersPerPixel(latitude: Double, zoom: Double) = 156543.03392804097 * cos(Math.toRadians(latitude.coerceIn(-MAX_LATITUDE, MAX_LATITUDE))) / 2.0.pow(zoom)
}

data class TileKey(val zoom: Int, val x: Int, val y: Int) {
    init { require(zoom in 0..19 && x in 0 until (1 shl zoom) && y in 0 until (1 shl zoom)) }
    override fun toString() = "TileKey(REDACTED)"
}
data class VisibleTile(val key: TileKey, val unwrappedX: Int)
enum class HeadingSource { DEVICE_BEARING, ROUTE_ESTIMATE, LAST_HEADING, NORTH_UP }

class CameraPose(val position: RouteCoordinate, val headingDegrees: Double, val zoom: Double,
    val width: Int, val height: Int, val forwardMeters: Double, val forwardPath: List<RouteCoordinate>,
    val headingSource: HeadingSource = HeadingSource.NORTH_UP, val directionDegrees: Double? = null) {
    val anchorX = width * 0.5
    val anchorY = height * 0.68
    private val origin = WebMercator.project(position, zoom)
    private val radians = Math.toRadians(headingDegrees)
    private val c = cos(radians)
    private val s = sin(radians)
    val tileZoom = floor(zoom).toInt().coerceIn(0, 19)
    val tileScale = 2.0.pow(zoom - tileZoom)

    fun screen(point: RouteCoordinate): MapPoint {
        val p = WebMercator.project(point, zoom)
        val size = 256.0 * 2.0.pow(zoom)
        var dx = p.x - origin.x
        if (dx > size / 2) dx -= size
        if (dx < -size / 2) dx += size
        return screenOffset(dx, p.y - origin.y)
    }
    private fun screenOffset(dx: Double, dy: Double) = MapPoint(anchorX + dx * c + dy * s, anchorY - dx * s + dy * c)
    private fun worldAtScreen(x: Double, y: Double): MapPoint {
        val dx = x - anchorX; val dy = y - anchorY
        return MapPoint((origin.x + dx * c - dy * s) / tileScale, (origin.y + dx * s + dy * c) / tileScale)
    }
    fun tileCorners(tile: VisibleTile): List<MapPoint> {
        val left = tile.unwrappedX * 256.0 * tileScale - origin.x
        val top = tile.key.y * 256.0 * tileScale - origin.y
        val size = 256.0 * tileScale
        return listOf(screenOffset(left, top), screenOffset(left + size, top), screenOffset(left + size, top + size), screenOffset(left, top + size))
    }
    /** Separating-axis test prevents downloads for unused corners of a rotated bounding box. */
    fun intersectsTile(tile: VisibleTile): Boolean {
        val corners = tileCorners(tile)
        val viewport = listOf(MapPoint(0.0, 0.0), MapPoint(width.toDouble(), 0.0), MapPoint(width.toDouble(), height.toDouble()), MapPoint(0.0, height.toDouble()))
        return listOf(MapPoint(1.0, 0.0), MapPoint(0.0, 1.0), MapPoint(c, -s), MapPoint(s, c)).all { axis ->
            val a = corners.map { it.x * axis.x + it.y * axis.y }
            val b = viewport.map { it.x * axis.x + it.y * axis.y }
            a.max() > b.min() + 0.001 && b.max() > a.min() + 0.001
        }
    }
    fun visibleTiles(): List<VisibleTile> {
        val corners = listOf(worldAtScreen(0.0, 0.0), worldAtScreen(width.toDouble(), 0.0),
            worldAtScreen(width.toDouble(), height.toDouble()), worldAtScreen(0.0, height.toDouble()))
        val count = 1 shl tileZoom
        val result = mutableListOf<VisibleTile>()
        for (x in floor(corners.minOf { it.x } / 256).toInt()..floor(corners.maxOf { it.x } / 256).toInt()) {
            for (y in max(0, floor(corners.minOf { it.y } / 256).toInt())..min(count - 1, floor(corners.maxOf { it.y } / 256).toInt())) {
                val tile = VisibleTile(TileKey(tileZoom, ((x % count) + count) % count, y), x)
                if (intersectsTile(tile)) result.add(tile)
            }
        }
        return result
    }
    override fun toString() = "CameraPose(position=REDACTED, forwardPoints=${forwardPath.size})"
}

/** Stateful smoothing around a pure camera policy. Never frames the destination or a route overview. */
class FollowCamera {
    private var previous: CameraPose? = null
    private var previousTime: Long? = null
    fun update(route: GuidanceRoute?, fix: PositionFix?, match: MatchState?, width: Int, height: Int): CameraPose? {
        if (fix == null || width <= 0 || height <= 0) return null
        val old = previous
        val dt = previousTime?.let { (fix.elapsedMillis - it).coerceIn(0, 3000).toDouble() } ?: 3000.0
        val speed = fix.speedMetersPerSecond?.takeIf { it.isFinite() && it >= 0.0 } ?: 0.0
        val onRoute = route != null && match?.status == MatchStatus.ON_ROUTE && match.geometryAlongMeters != null
        val along = if (onRoute) match!!.geometryAlongMeters!!.coerceIn(0.0, route!!.geometry.lengthMeters) else 0.0
        val reliable = speed >= 2 && fix.bearingDegrees?.let { it.isFinite() && it in 0.0..360.0 } == true &&
            fix.bearingAccuracyDegrees?.let { it.isFinite() && it in 0.0..45.0 } == true
        val headingSource = when {
            reliable -> HeadingSource.DEVICE_BEARING
            onRoute -> HeadingSource.ROUTE_ESTIMATE
            old != null -> HeadingSource.LAST_HEADING
            else -> HeadingSource.NORTH_UP
        }
        val targetHeading = when (headingSource) {
            HeadingSource.DEVICE_BEARING -> fix.bearingDegrees!! % 360.0
            HeadingSource.ROUTE_ESTIMATE -> bearing(route!!.geometry.atFraction(along / route.geometry.lengthMeters),
                route.geometry.atFraction(min(along + 25.0, route.geometry.lengthMeters) / route.geometry.lengthMeters))
            HeadingSource.LAST_HEADING -> old!!.headingDegrees
            HeadingSource.NORTH_UP -> 0.0
        }
        val heading = if (old == null) targetHeading else {
            val difference = (targetHeading - old.headingDegrees + 540) % 360 - 180
            (old.headingDegrees + difference * (1 - exp(-dt / 700.0)) + 360) % 360
        }
        var horizon = (240.0 + speed * 22.0).coerceIn(240.0, 1200.0)
        val path = mutableListOf<RouteCoordinate>()
        if (onRoute) {
            val geometry = route!!.geometry
            val next = match!!.nextManeuver?.takeIf { it.type != 100 && it.type != 101 && it.type != 1000 }
            val nextAlong = next?.let(route::geometryOffset)
            if (nextAlong != null && nextAlong - along in 0.0..1500.0) horizon = max(horizon, nextAlong - along + 70.0)
            val remaining = (geometry.lengthMeters - along).coerceAtLeast(0.0)
            // A small adaptive tail suppresses terminal expression without suppressing a nearby safe turn.
            var tail = (remaining * 0.15).coerceIn(8.0, 60.0)
            if (nextAlong != null && nextAlong > along) tail = min(tail, max(5.1, geometry.lengthMeters - nextAlong - 8.0))
            val end = min(along + horizon, geometry.lengthMeters - tail)
            if (end > along) {
                path.add(geometry.atFraction(along / geometry.lengthMeters))
                var sample = along + 15.0
                while (sample < end) { path.add(geometry.atFraction(sample / geometry.lengthMeters)); sample += 15.0 }
                path.add(geometry.atFraction(end / geometry.lengthMeters))
            }
        }
        val headingRadians = Math.toRadians(heading)
        var mpp = max(0.45, horizon / max(100.0, height * 0.58))
        // Fit only the local forward window, including turn bends, within a padded visible rectangle.
        for (point in path) {
            val a = WebMercator.project(fix.coordinate, 16.0); val b = WebMercator.project(point, 16.0)
            val scale = WebMercator.metersPerPixel(fix.coordinate.latitude, 16.0)
            val east = (b.x - a.x) * scale; val south = (b.y - a.y) * scale
            val x = east * cos(headingRadians) + south * sin(headingRadians)
            val y = -east * sin(headingRadians) + south * cos(headingRadians)
            mpp = max(mpp, abs(x) / max(20.0, width * 0.5 - 40))
            mpp = max(mpp, abs(y) / max(20.0, if (y < 0) height * 0.68 - 40 else height * 0.32 - 40))
        }
        val targetZoom = log2(WebMercator.metersPerPixel(fix.coordinate.latitude, 0.0) / mpp).coerceIn(13.0, 18.5)
        val zoom = if (old == null || targetZoom < old.zoom) targetZoom else old.zoom + (targetZoom - old.zoom) * (1 - exp(-dt / 1100.0))
        val direction = targetHeading.takeIf { headingSource == HeadingSource.DEVICE_BEARING || headingSource == HeadingSource.ROUTE_ESTIMATE }
        val pose = CameraPose(fix.coordinate, heading, zoom, width, height, horizon, path, headingSource, direction)
        previous = pose; previousTime = fix.elapsedMillis
        return pose
    }
    private fun bearing(a: RouteCoordinate, b: RouteCoordinate): Double {
        val east = Math.toRadians(b.longitude - a.longitude) * cos(Math.toRadians(a.latitude))
        val north = Math.toRadians(b.latitude - a.latitude)
        return (Math.toDegrees(atan2(east, north)) + 360) % 360
    }
}
