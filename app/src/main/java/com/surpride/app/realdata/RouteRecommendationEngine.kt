package com.surpride.app.realdata

import com.surpride.app.experience.core.*

/** API time is a constraint, never a scenic score. No inferred range, route doubling or DEMO fallback. */
class RouteRecommendationEngine(private val places:List<RealPlace>,private val routes:RouteProvider) {
    fun recommend(origin:GeoPoint,p:DrivePreferences,c:RecommendationContext,stayMinutes:Int,bufferMinutes:Int,seed:Long):RoutePlan {
        val budget=budgetMinutes(p,c.nowMillis)?.takeIf {it>0}
            ?:return RoutePlan.Failure(setOf(RouteError.TIME_EXCEEDED))
        if(stayMinutes !in 0..10080 || bufferMinutes !in 0..1440 || p.randomness !in 0..100)
            return RoutePlan.Failure(setOf(RouteError.INVALID_RESPONSE))
        val errors=mutableSetOf<RouteError>()
        val plans=mutableListOf<RoutePlan.Success>()
        for(place in places) {
            if(place.id in c.recentIds || !place.categories.containsAll(p.categories)) continue
            if(place.point==origin) continue
            // No verified companion/family/pet access data yet; do not imply that all companions fit.
            if(p.companion!=Companion.SOLO) continue
            val out=routes.route(origin,place.point)
            if(out is RouteReply.Failure) {errors.add(out.error);continue}
            val outbound=(out as RouteReply.Success).leg
            val back=if(p.returnToStart) routes.route(place.point,origin) else null
            if(back is RouteReply.Failure) {errors.add(back.error);continue}
            val inbound=(back as? RouteReply.Success)?.leg
            val legs=listOfNotNull(outbound,inbound)
            // Request context precedes receipt. Allow at most a minute of request/clock skew.
            if(legs.any {c.nowMillis-it.receivedAtMillis !in -60_000..300_000} || legs.any {it.origin!=outbound.origin}) {
                errors.add(RouteError.STALE_DATA);continue
            }
            val seconds=legs.sumOf {it.seconds}+(stayMinutes.toLong()+bufferMinutes)*60
            if(seconds>budget.toLong()*60) {errors.add(RouteError.TIME_EXCEEDED);continue}
            // Reuse existing preference scoring. Exact-second eligibility was decided above;
            // do not apply a second, per-leg rounded budget during scoring.
            val estimate=JourneyEstimate(((outbound.seconds+59)/60).toInt(),stayMinutes,
                inbound?.let {((it.seconds+59)/60).toInt()}?:0,bufferMinutes,
                if(outbound.origin==RouteOrigin.LIVE_API) EstimateOrigin.VERIFIED_ROUTE else EstimateOrigin.DEMO,
                if(outbound.origin==RouteOrigin.LIVE_API) legs.sumOf {it.meters}/1000.0 else null)
            val candidate=DestinationCandidate(place.id,place.name,"출처 확인 장소 · 시설/차량 진입 미확인",place.moods,
                place.categories,setOf(Companion.SOLO),mapOf(p.scope to estimate),sample=false)
            val scoringPreferences=p.copy(availableMinutes=Int.MAX_VALUE,returnByEpochMillis=null)
            val scored=DestinationEngine(listOf(candidate)).recommend(scoringPreferences,c,seed)
            if(scored is RecommendationResult.Success) plans.add(RoutePlan.Success(place,outbound,inbound,seconds,scored))
            else errors.add(RouteError.NO_CANDIDATE)
        }
        return plans.sortedWith(compareByDescending<RoutePlan.Success>{it.scoring.score.total}.thenBy {it.place.id}).firstOrNull()
            ?:RoutePlan.Failure(errors.ifEmpty {setOf(RouteError.NO_CANDIDATE)})
    }
}
