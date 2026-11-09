package com.surpride.navhostpoc.map

import com.surpride.navhostpoc.guidance.*
import com.surpride.navhostpoc.tracking.*
import org.junit.Assert.*
import org.junit.Test

class FollowCameraTest {
    private fun p(east: Double, north: Double = 0.0) = RouteCoordinate(east / 111195.0, north / 111195.0)
    private fun route(points: List<RouteCoordinate> = listOf(p(0.0), p(3000.0)), turn: Long = 400) =
        GuidanceRoute(RouteDataSource.MOCK, RouteGeometry(points), emptyList(),
            listOf(Maneuver(2, turn, 0), Maneuver(101, 3000, 0)), 3000, 300, points.last())
    private fun fix(east: Double = 0.0, speed: Double? = 10.0, bearing: Double? = 90.0, time: Long = 1000) =
        PositionFix(p(east), 5.0, time, 0, speed, bearing, 10.0, PositionSource.FAKE, false)
    private fun match(along: Double = 0.0, turn: Long = 400) = MatchState(MatchStatus.ON_ROUTE, "MATCHED", PositionSource.FAKE,
        RouteProgress(along, 3000 - along, turn - along, 0, 300), Maneuver(2, turn, 0), geometryAlongMeters = along)

    @Test fun mercatorUsesLongitudeEastLatitudeNorthAndClampsThePoles() {
        assertEquals(128.0, WebMercator.project(RouteCoordinate(0.0, 0.0), 0.0).x, 0.00001)
        assertEquals(128.0, WebMercator.project(RouteCoordinate(0.0, 0.0), 0.0).y, 0.00001)
        assertEquals(384.0, WebMercator.project(RouteCoordinate(90.0, 0.0), 1.0).x, 0.00001)
        assertEquals(0.0, WebMercator.project(RouteCoordinate(0.0, 90.0), 2.0).y, 0.001)
        val original = RouteCoordinate(127.1, 37.4)
        val roundTrip = WebMercator.unproject(WebMercator.project(original, 15.0), 15.0)
        assertEquals(original.latitude, roundTrip.latitude, 1e-8)
        assertEquals(original.longitude, roundTrip.longitude, 1e-8)
    }

    @Test fun followsCurrentFixAndRotatesReliableTravelHeadingToTop() {
        val pose = FollowCamera().update(route(), fix(), match(), 1200, 700)!!
        val current = pose.screen(fix().coordinate)
        val ahead = pose.screen(p(100.0))
        assertEquals(600.0, current.x, 0.01)
        assertEquals(476.0, current.y, 0.01)
        assertEquals(current.x, ahead.x, 0.01)
        assertTrue(ahead.y < current.y)
        assertNull(FollowCamera().update(route(), null, match(), 1200, 700))
    }

    @Test fun headingSmoothsAcrossNorthAndIgnoresStationaryBearingNoise() {
        val camera = FollowCamera()
        camera.update(null, fix(bearing = 350.0), null, 1200, 700)
        val moved = camera.update(null, fix(bearing = 10.0, time = 1500), null, 1200, 700)!!
        assertTrue(moved.headingDegrees > 350.0 || moved.headingDegrees < 10.0)
        val stopped = camera.update(null, fix(speed = 0.0, bearing = 180.0, time = 2000), null, 1200, 700)!!
        assertEquals(moved.headingDegrees, stopped.headingDegrees, 0.001)
    }

    @Test fun stationaryRouteAcquisitionReplacesUnknownNorthUpWithEstimatedRoadHeading() {
        val camera = FollowCamera()
        val unknown = camera.update(route(), fix(speed = null, bearing = null),
            MatchState(MatchStatus.UNKNOWN, "STALE", PositionSource.FAKE), 1200, 700)!!
        assertEquals(0.0, unknown.headingDegrees, 0.001)
        val acquired = camera.update(route(), fix(speed = null, bearing = null, time = 2000), match(), 1200, 700)!!
        assertTrue(acquired.headingDegrees > 10.0)
        assertTrue(acquired.screen(p(100.0)).y < acquired.anchorY - 10)
        assertEquals(HeadingSource.ROUTE_ESTIMATE, acquired.headingSource)
        assertEquals(90.0, acquired.directionDegrees!!, 0.01)
    }

    @Test fun fasterTravelShowsMoreForwardRoadAndSafeNextTurnFits() {
        val slow = FollowCamera().update(route(), fix(speed = 2.0), match(), 1200, 700)!!
        val fast = FollowCamera().update(route(), fix(speed = 30.0), match(), 1200, 700)!!
        assertTrue(fast.forwardMeters > slow.forwardMeters)
        val turn = fast.screen(p(400.0))
        assertTrue(turn.x in 30.0..1170.0)
        assertTrue(turn.y in 30.0..670.0)
        assertTrue(fast.zoom < 19.0)
    }

    @Test fun routeWindowOmitsTerminalCoordinateAndKeepsNearEndTurnAndCurrentMarker() {
        val r = route()
        val pose = FollowCamera().update(r, fix(east = 2700.0), match(2700.0), 2560, 1440)!!
        assertTrue(pose.forwardPath.all { meters(it, r.geometry.points.last()) > 5.0 })
        assertTrue(pose.forwardPath.size < 120)
        assertEquals(1280.0, pose.screen(fix(east = 2700.0).coordinate).x, 0.01)
        val end = FollowCamera().update(route(turn = 2975), fix(east = 2950.0), match(2950.0, 2975), 2560, 1440)!!
        assertTrue(end.forwardPath.isNotEmpty())
        val nearTurn = end.screen(p(2975.0))
        assertTrue(nearTurn.x in 30.0..2530.0 && nearTurn.y in 30.0..1410.0)
        assertTrue(end.forwardPath.all { meters(it, r.geometry.points.last()) > 5.0 })
        assertEquals(979.2, end.screen(fix(east = 2950.0).coordinate).y, 0.01)
        assertFalse(end.toString().contains("latitude"))
    }

    @Test fun offRouteHasCurrentPositionWithoutInventedForwardGuidance() {
        val pose = FollowCamera().update(route(), fix(), MatchState(MatchStatus.OFF_ROUTE, "OFF", PositionSource.FAKE), 800, 480)!!
        assertTrue(pose.forwardPath.isEmpty())
        assertEquals(400.0, pose.screen(fix().coordinate).x, 0.01)
    }

    @Test fun tileSelectionRequestsOnlyTilesIntersectingTheRotatedViewport() {
        val pose = FollowCamera().update(null, fix(bearing = 45.0), null, 800, 480)!!
        val tiles = pose.visibleTiles()
        assertTrue(tiles.isNotEmpty())
        assertTrue(tiles.size < 50)
        tiles.forEach { assertTrue(pose.intersectsTile(it)) }
        assertEquals(tiles.size, tiles.distinct().size)
    }
}
