package com.surpride.navhostpoc

import com.surpride.navhostpoc.guidance.RouteDataSource
import org.junit.Assert.*
import org.junit.Test

class PublicReplayRouteTest {
    private val now=1_800_000_000_000L
    private fun payload()="""{
      "schema_version":1,"source":"KAKAO_DIRECTIONS","measured_at_epoch_ms":$now,
      "development_public_replay":true,"candidate_id":"seoul-night-13","private_destination_label":"public test destination",
      "origin":{"longitude":126.976787992565,"latitude":37.5716228232824},
      "destination":{"longitude":126.970850893942,"latitude":37.517820588578},
      "distance_meters":200,"duration_seconds":100,
      "guidance":{"segments":[{"road_name":"public road","distance_meters":200,"duration_seconds":100,"traffic_state":4,
      "geometry":[[126.976787992565,37.5716228232824],[126.977,37.571],[126.978,37.570]]}],
      "maneuvers":[{"type":100,"offset_meters":0,"road_index":0},{"type":101,"offset_meters":200,"road_index":0}]}}
    """
    @Test fun acceptsOnlyFreshOptedInLivePublicRoute() {
        assertEquals(RouteDataSource.KAKAO_DIRECTIONS,PublicReplayRoute.parse(payload(),now).source)
        for(body in listOf(payload().replace("KAKAO_DIRECTIONS","MOCK"),payload().replace("true","false"),
            payload().replace("126.970850893942","127.123"),payload().replace("126.976787992565","127.234"),"{}")) {
            assertThrows(IllegalArgumentException::class.java) {PublicReplayRoute.parse(body,now)}
        }
        assertThrows(IllegalArgumentException::class.java) {PublicReplayRoute.parse(payload(),now+3_600_001)}
    }
}
