package com.surpride.navhostpoc.guidance

import org.junit.Assert.*
import org.junit.Test

class GuidanceTest {
    private val now=1_800_000_000_000L
    private fun payload(source:String="KAKAO_DIRECTIONS")="""{
      "schema_version":1,"source":"$source","measured_at_epoch_ms":$now,"private_destination_label":"비밀명소",
      "destination":{"longitude":127.002,"latitude":37.0},"distance_meters":200,"duration_seconds":100,
      "guidance":{"segments":[
        {"road_name":"첫 도로","distance_meters":100,"duration_seconds":50,"traffic_state":4,"geometry":[[127.0,37.0],[127.001,37.0]]},
        {"road_name":"둘째 도로","distance_meters":100,"duration_seconds":50,"traffic_state":2,"geometry":[[127.001,37.0],[127.002,37.0]]}],
        "maneuvers":[{"type":100,"offset_meters":0,"road_index":0},{"type":2,"offset_meters":100,"road_index":1},{"type":101,"offset_meters":200,"road_index":1}]}}
    """
    private fun route()=GuidanceParser.parse(payload(),now)
    @Test fun parsesActualContractWithoutPromotingMockOrPrintingDestination() {
        val r=route();assertEquals(RouteDataSource.KAKAO_DIRECTIONS,r.source);assertEquals(3,r.geometry.points.size)
        assertEquals(RouteDataSource.MOCK,GuidanceParser.parse(payload("MOCK"),now).source)
        assertFalse(r.toString().contains("127."));assertFalse(r.toString().contains("비밀명소"))
    }
    @Test fun geographicDistanceAndInterpolationFollowLongitudeLatitude() {
        val g=route().geometry
        assertTrue(g.lengthMeters in 170.0..185.0)
        assertEquals(127.001,g.atFraction(.5).longitude,.000001)
        assertThrows(IllegalArgumentException::class.java){RouteCoordinate(37.0,127.0)}
    }
    @Test fun nextTurnDistanceAndRoadChangeWithSimulatedMovement() {
        val sim=GuidanceSimulator(route(),10.0);sim.start();sim.advance(5.0)
        assertEquals(50.0,sim.state().progress.remainingToManeuverMeters,0.01)
        sim.advance(5.1)
        assertEquals(1,sim.state().progress.segmentIndex)
        assertEquals("둘째 도로",sim.presentation().road)
        assertEquals(101,sim.state().nextManeuver?.type)
    }
    @Test fun pauseRestartCancelAndArrivalAreExplicitAndDeterministic() {
        val sim=GuidanceSimulator(route(),10.0)
        sim.advance(9.0);assertEquals(0.0,sim.state().progress.traveledMeters,0.0)
        sim.start();sim.advance(5.0);sim.pause();sim.advance(5.0)
        assertEquals(50.0,sim.state().progress.traveledMeters,0.0)
        sim.start();sim.advance(100.0);assertEquals(GuidanceStatus.ARRIVED,sim.state().status)
        sim.restart();assertEquals(GuidanceStatus.READY,sim.state().status)
        sim.start();sim.stop();sim.advance(100.0);assertEquals(GuidanceStatus.STOPPED,sim.state().status)
    }
    @Test fun publicProjectionNeverContainsDestinationAndOmitsFinalRouteArea() {
        val sim=GuidanceSimulator(route(),10.0);sim.start()
        assertFalse(sim.presentation().toString().contains("비밀명소"))
        assertFalse(sim.presentation().toString().contains("127.002"))
        sim.advance(19.0)
        assertTrue(sim.presentation().localPath.isEmpty())
    }
    @Test fun malformedMissingOutOfOrderAndSensitiveRoadNamesFailClosed() {
        listOf("{}",payload().replace("[127.0,37.0]","[127.0]"),payload().replace("\"offset_meters\":100","\"offset_meters\":201"),
            payload().replace("둘째 도로","비밀명소 진입"),payload().replace("\"road_index\":1","\"road_index\":9"),
            payload().replace("KAKAO_DIRECTIONS","UNKNOWN"),payload().replace("\"distance_meters\":200","\"distance_meters\":-1")).forEach {
            assertThrows(IllegalArgumentException::class.java){GuidanceParser.parse(it,now)}
        }
    }
    @Test fun staleRouteAndUnsupportedDirectionsAreNeverSilentlyStraight() {
        assertThrows(IllegalArgumentException::class.java){GuidanceParser.parse(payload(),now+3_600_001)}
        val r=GuidanceParser.parse(payload().replace("\"type\":2","\"type\":987"),now)
        assertTrue(GuidanceSimulator(r).presentation().instruction.contains("미지원"))
    }
    @Test fun trafficIncidentIsVisibleAndSimulationIsAlwaysMarked() {
        val r=GuidanceParser.parse(payload().replace("\"traffic_state\":4","\"traffic_state\":6"),now)
        val view=GuidanceSimulator(r).presentation()
        assertTrue(view.warning.contains("통행"));assertTrue(view.simulatedPosition)
    }
    @Test fun replaySameTicksProducesSameProgressAndArrivalHidesRoadLabel() {
        val a=GuidanceSimulator(route());val b=GuidanceSimulator(route())
        a.start();b.start();listOf(1.0,2.0,3.0).forEach {a.advance(it);b.advance(it)}
        assertEquals(a.presentation(),b.presentation())
        a.advance(1000.0);assertEquals("",a.presentation().road)
    }
    @Test fun geometryPrivacyBoundaryUsesActualGeometryMetersEvenWhenRoadDistanceDisagrees() {
        val data=payload().replace("\"distance_meters\":100,","\"distance_meters\":500,")
            .replace("\"distance_meters\":200,","\"distance_meters\":1000,")
            .replace("\"offset_meters\":200","\"offset_meters\":1000").replace("\"offset_meters\":100,","\"offset_meters\":500,")
        val sim=GuidanceSimulator(GuidanceParser.parse(data,now))
        assertTrue(sim.presentation().localPath.isEmpty()) // geometric route is only 178m long
    }
    @Test fun cancelClearsPublicTurnAndEta() {
        val sim=GuidanceSimulator(route());sim.start();sim.advance(1.0);sim.stop()
        assertNull(sim.state().nextManeuver)
        assertEquals(0L,sim.presentation().remainingSeconds)
        assertEquals(0.0,sim.presentation().nextDistanceMeters,0.0)
    }
}
