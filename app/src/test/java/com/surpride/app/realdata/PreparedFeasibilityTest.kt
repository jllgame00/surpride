package com.surpride.app.realdata

import com.surpride.app.experience.core.*
import org.junit.Assert.*
import org.junit.Test

class PreparedFeasibilityTest {
    @Test fun cachedSelectionRechecksExactDeadlineAndBothLegsBeforeHandoff() {
        val now=1_800_000_000_000L
        val place=SeoulPilotCatalog.places[2]
        val p=DrivePreferences(availableMinutes=60,returnByEpochMillis=now+3_060_000)
        val provider=RouteProvider {_,_->RouteReply.Success(RouteLeg(600,1000,listOf(GeoPoint(126.97,37.57),place.point),RouteOrigin.LIVE_API,now))}
        val plan=RouteRecommendationEngine(listOf(place),provider,{now}).recommend(SeoulPilotCatalog.developmentOrigin,p,RecommendationContext(now),20,10,1) as RoutePlan.Success
        assertEquals(3000L,plan.totalSeconds)
        assertTrue(plan.feasibleForHandoff(p,now))
        assertFalse(plan.feasibleForHandoff(p,now+61_000))
        assertFalse(plan.feasibleForHandoff(p.copy(returnByEpochMillis=null),now+300_001))
        assertFalse(plan.feasibleForHandoff(p.copy(availableMinutes=49),now))
    }
}
