package com.surpride.navhostpoc

import org.junit.Assert.*
import org.junit.Test

class RoutePacketFreshnessTest {
    @Test fun expiredOrFutureRouteCannotBeActivatedEvenIfGpsIsFresh() {
        assertTrue(ActiveRoutePacket.validAt(100_000,3_700_000,100_100))
        assertFalse(ActiveRoutePacket.validAt(100_000,3_700_000,3_700_001))
        assertFalse(ActiveRoutePacket.validAt(100_000,150_000,160_000))
        assertFalse(ActiveRoutePacket.validAt(100_000,3_700_000,10_000))
    }
}
