package com.surpride.app.realdata

import com.surpride.app.experience.core.*
import com.surpride.app.realdata.experience.*
import org.junit.Assert.*
import org.junit.Test

class RecommendationCorrectnessTest {
    private val now=1_800_000_000_000L
    private val origin=GeoPoint(0.0,0.0)
    private val places=SeoulPilotCatalog.places.take(2)
    private val path=listOf(origin,GeoPoint(0.001,0.0),GeoPoint(0.002,0.0))
    private fun preferences()=DrivePreferences(availableMinutes=60,returnToStart=false,randomness=0)
    private class Provider(val reply:(Int,GeoPoint,RouteOptions)->RouteReply):RouteProvider {
        var calls=0
        override fun route(origin:GeoPoint,destination:GeoPoint)=route(origin,destination,RouteOptions())
        override fun route(origin:GeoPoint,destination:GeoPoint,options:RouteOptions)=reply(++calls,origin,options)
    }
    private fun leg(seconds:Long,options:RouteOptions,at:Long=now,geometry:List<GeoPoint> = path,
        meters:Long=10000,source:RouteOrigin=RouteOrigin.LIVE_API)=
        RouteLeg(seconds,meters,geometry,source,at,options,"payload-${options.priority}-$seconds-$at")
    private fun run(provider:RouteProvider,integrated:Boolean=true,p:DrivePreferences=preferences(),
        candidates:List<RealPlace> = places.take(1),clock:()->Long={now},stay:Int=0,buffer:Int=0):RoutePlan =
        if(integrated) DriveExperienceEngine(candidates,provider,clock=clock).recommend(origin,p,RecommendationContext(now),stay,buffer,1)
        else RouteRecommendationEngine(candidates,provider,clock).recommend(origin,p,RecommendationContext(now),stay,buffer,1)

    @Test fun auditSameGeometrySelectsSixHundredSecondsAndItsExactPayload() {
        val responses=mutableMapOf<RoutePriority,RouteLeg>()
        val provider=Provider {_,_,options->RouteReply.Success(leg(when(options.priority) {
            RoutePriority.RECOMMEND->1200;RoutePriority.TIME->600;RoutePriority.DISTANCE->900
        },options).also {responses[options.priority]=it})}
        val plan=run(provider) as RoutePlan.Success
        assertEquals(600L,plan.totalSeconds);assertSame(responses[RoutePriority.TIME],plan.outbound)
        assertEquals(RoutePriority.TIME,plan.experience!!.selectedOptions.priority)
        assertEquals("payload-TIME-600-$now",plan.outbound.guidancePayload)
        assertEquals(2,plan.experience!!.comparisons.count {it.status==ComparisonStatus.DUPLICATE})
        assertNull(plan.presentation(false).destination);assertEquals(3,provider.calls)
    }
    @Test fun allPriorityResponsePermutationsSelectTheSameBestMeasuredDuration() {
        for(values in listOf(listOf(1200L,600L,900L),listOf(900L,600L,1200L),listOf(600L,1200L,900L),
            listOf(600L,900L,1200L),listOf(900L,1200L,600L),listOf(1200L,900L,600L))) {
            val provider=Provider {_,_,options->RouteReply.Success(leg(values[options.priority.ordinal],options))}
            assertEquals(600L,(run(provider) as RoutePlan.Success).outbound.seconds)
        }
    }
    @Test fun representativeUsesPolicyScoreRatherThanAlwaysTimePriority() {
        val provider=Provider {_,_,options->RouteReply.Success(leg(if(options.priority==RoutePriority.TIME) 600 else 900,
            options,meters=if(options.priority==RoutePriority.TIME) 100000 else 1000))}
        val plan=run(provider,p=preferences().copy(mood=Mood.HEALING)) as RoutePlan.Success
        assertEquals(RoutePriority.RECOMMEND,plan.experience!!.selectedOptions.priority)
        assertEquals(900L,plan.outbound.seconds)
    }
    @Test fun staleFastestIsRejectedBeforeGeometryGrouping() {
        val provider=Provider {_,_,options->RouteReply.Success(leg(if(options.priority==RoutePriority.TIME) 600 else 900,
            options,at=if(options.priority==RoutePriority.TIME) now-300001 else now))}
        val plan=run(provider) as RoutePlan.Success
        assertEquals(900L,plan.outbound.seconds)
        assertTrue(plan.experience!!.comparisons.any {it.status==ComparisonStatus.STALE_DATA})
    }
    @Test fun finalFreshnessRecheckCanKeepAnotherRepresentativeOfTheSameGeometry() {
        var current=now
        val provider=Provider {_,_,options->RouteReply.Success(leg(if(options.priority==RoutePriority.RECOMMEND) 600 else 900,
            options,at=if(options.priority==RoutePriority.RECOMMEND) now-299999 else now))}
        var checks=0
        val clock={if(++checks>=5) now+2 else current}
        val plan=run(provider,clock=clock) as RoutePlan.Success
        assertEquals(900L,plan.outbound.seconds)
    }
    @Test fun exactScoreTieUsesStablePriorityAndCandidateTieBreak() {
        fun evaluate(candidates:List<RealPlace>):RoutePlan.Success {
            val provider=Provider {_,_,options->RouteReply.Success(leg(600,options))}
            return run(provider,candidates=candidates) as RoutePlan.Success
        }
        val tied=places.map {it.copy(moods=emptyMap())}
        val first=evaluate(tied);val reversed=evaluate(tied.reversed())
        assertEquals(tied.minOf {it.id},first.place.id)
        assertEquals(first.place.id,reversed.place.id)
        assertEquals(RoutePriority.RECOMMEND,first.experience!!.selectedOptions.priority)
    }
    @Test fun differentGeometryStillCompetesByObservedPolicy() {
        val provider=Provider {_,_,options->RouteReply.Success(leg(if(options.priority==RoutePriority.TIME) 600 else 900,
            options,geometry=if(options.priority==RoutePriority.TIME) path.reversed() else path))}
        val plan=run(provider) as RoutePlan.Success
        assertEquals(600L,plan.outbound.seconds)
        assertEquals(2,plan.experience!!.comparisons.count {it.status==ComparisonStatus.ELIGIBLE})
    }
    @Test fun invalidOptionsCannotDisplaceAValidDuplicate() {
        val provider=Provider {_,_,options->RouteReply.Success(leg(if(options.priority==RoutePriority.TIME) 1 else 900,
            if(options.priority==RoutePriority.TIME) RouteOptions() else options))}
        assertEquals(900L,(run(provider) as RoutePlan.Success).outbound.seconds)
    }
    @Test fun fatalFirstFailureStopsBothModesAtOneRequest() {
        for(integrated in listOf(false,true)) for(error in listOf(RouteError.AUTH,RouteError.RATE_LIMIT,RouteError.NOT_CONFIGURED)) {
            val provider=Provider {_,_,_->RouteReply.Failure(error)}
            val result=run(provider,integrated,candidates=places) as RoutePlan.Failure
            assertTrue(error in result.errors);assertEquals(1,provider.calls)
        }
    }
    @Test fun fatalReverseFailureStopsBothModesWithoutTryingAnotherPlace() {
        for(integrated in listOf(false,true)) {
            val provider=Provider {_,a,options->if(a!=origin) RouteReply.Failure(RouteError.AUTH) else RouteReply.Success(leg(600,options))}
            val result=run(provider,integrated,preferences().copy(returnToStart=true),places) as RoutePlan.Failure
            assertTrue(RouteError.AUTH in result.errors);assertEquals(if(integrated) 4 else 2,provider.calls)
        }
    }
    @Test fun cancellationBeforeFirstRequestMakesZeroCallsInBothModes() {
        for(integrated in listOf(false,true)) try {
            val provider=Provider {_,_,options->RouteReply.Success(leg(600,options))}
            Thread.currentThread().interrupt()
            assertTrue(run(provider,integrated,candidates=places) is RoutePlan.Failure)
            assertEquals(0,provider.calls);assertTrue(Thread.currentThread().isInterrupted)
        } finally {Thread.interrupted()}
    }
    @Test fun cancellationDuringSuccessDoesNotIssueReturnOrNextCandidate() {
        for(integrated in listOf(false,true)) try {
            val provider=Provider {_,_,options->Thread.currentThread().interrupt();RouteReply.Success(leg(600,options))}
            assertTrue(run(provider,integrated,preferences().copy(returnToStart=true),places) is RoutePlan.Failure)
            assertEquals(1,provider.calls);assertTrue(Thread.currentThread().isInterrupted)
        } finally {Thread.interrupted()}
    }
    @Test fun ordinaryNetworkAndNoRouteFailuresPermitBoundedFurtherExploration() {
        for(integrated in listOf(false,true)) for(error in listOf(RouteError.NETWORK,RouteError.NO_ROUTE)) {
            val provider=Provider {n,_,options->if(n==1) RouteReply.Failure(error) else RouteReply.Success(leg(600,options))}
            assertTrue(run(provider,integrated,candidates=places) is RoutePlan.Success)
            assertTrue(provider.calls in 2..6)
        }
    }
    @Test fun bothModesKeepExactSecondDeadlineInsteadOfRoundingDownToMinutes() {
        for(integrated in listOf(false,true)) {
            val provider=Provider {_,_,options->RouteReply.Success(leg(61,options))}
            assertTrue(run(provider,integrated,preferences().copy(returnByEpochMillis=now+61_000)) is RoutePlan.Success)
            assertTrue(run(provider,integrated,preferences().copy(returnByEpochMillis=now+60_999)) is RoutePlan.Failure)
        }
    }
    @Test fun exactRoundTripStayBufferBoundaryIsShared() {
        for(integrated in listOf(false,true)) {
            val provider=Provider {_,a,options->RouteReply.Success(leg(if(a==origin) 599 else 601,options))}
            val plan=run(provider,integrated,preferences().copy(availableMinutes=50,returnToStart=true),stay=20,buffer=10) as RoutePlan.Success
            assertEquals(3000L,plan.totalSeconds)
        }
    }
    @Test fun mixedOutboundReturnProvenanceNeverProducesSuccess() {
        for(integrated in listOf(false,true)) {
            val provider=Provider {_,a,options->RouteReply.Success(leg(600,options,source=if(a==origin) RouteOrigin.LIVE_API else RouteOrigin.MOCK))}
            assertTrue(run(provider,integrated,preferences().copy(returnToStart=true)) is RoutePlan.Failure)
        }
    }
    @Test fun identicalGeometryWithDifferentSourcesKeepsSelectedLegAndPayloadTogether() {
        val provider=Provider {_,_,options->RouteReply.Success(leg(if(options.priority==RoutePriority.TIME) 600 else 1200,
            options,source=if(options.priority==RoutePriority.RECOMMEND) RouteOrigin.MOCK else RouteOrigin.LIVE_API))}
        val plan=run(provider) as RoutePlan.Success
        assertEquals(RouteOrigin.LIVE_API,plan.routeOrigin);assertEquals(RoutePriority.TIME,plan.outbound.options.priority)
        assertEquals("payload-TIME-600-$now",plan.outbound.guidancePayload)
    }
    @Test fun interruptionExceptionCannotBecomeAnOrdinaryContinueInEitherMode() {
        for(integrated in listOf(false,true)) try {
            val provider=Provider {_,_,_->throw InterruptedException()}
            assertTrue(run(provider,integrated,candidates=places) is RoutePlan.Failure)
            assertEquals(1,provider.calls);assertTrue(Thread.currentThread().isInterrupted)
        } finally {Thread.interrupted()}
    }
    @Test fun catalogRequestCountsStayBoundedAndUnverifiedSceneryIsNeverRequested() {
        for(integrated in listOf(false,true)) {
            val provider=Provider {_,_,options->assertTrue(options.waypoints.isEmpty());RouteReply.Success(leg(600,options))}
            val plan=run(provider,integrated,preferences().copy(returnToStart=true),SeoulPilotCatalog.places) as RoutePlan.Success
            assertEquals(if(integrated) 8 else 10,provider.calls)
            if(integrated) assertEquals(provider.calls,plan.experience!!.requestCount)
        }
        assertTrue(PendingScenicCatalog.routingCandidates.isEmpty())
    }
    @Test fun deadlineElapsedDuringRequestsCannotReturnAnOldPlan() {
        for(integrated in listOf(false,true)) {
            var current=now
            val provider=Provider {_,_,options->current+=10_000;RouteReply.Success(leg(60,options,at=current))}
            assertTrue(run(provider,integrated,preferences().copy(returnByEpochMillis=now+60_000),places,{current}) is RoutePlan.Failure)
        }
    }
}
