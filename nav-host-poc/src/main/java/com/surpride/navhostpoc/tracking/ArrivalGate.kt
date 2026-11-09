package com.surpride.navhostpoc.tracking

/** Development candidate, never a parking/vehicle-entry certification. Explicit parked confirmation required. */
class ArrivalGate {
    var candidate=false;private set
    var arrived=false;private set
    private var count=0
    private var first:Long?=null
    private var last:Long?=null
    fun observe(fix:PositionFix,match:MatchState,endpointDistanceMeters:Double,parked:Boolean?,now:Long) {
        if(arrived) return
        val fresh=now-fix.elapsedMillis in 0..5000 && (last==null || fix.elapsedMillis>last!!)
        val qualified=fresh && parked==true && fix.accuracyMeters.isFinite() && fix.accuracyMeters in 0.1..25.0 &&
            match.status==MatchStatus.ON_ROUTE && match.progress?.remainingMeters?.let {it<=30.0}==true &&
            endpointDistanceMeters.isFinite() && endpointDistanceMeters<=maxOf(15.0,2*fix.accuracyMeters) &&
            (fix.speedMetersPerSecond==null || fix.speedMetersPerSecond.let {it.isFinite() && it in 0.0..1.0})
        if(!qualified) {invalidate();return}
        if(last!=null && fix.elapsedMillis-last!!>5000) invalidate()
        if(first==null) first=fix.elapsedMillis
        last=fix.elapsedMillis;count++
        candidate=count>=3 && fix.elapsedMillis-first!!>=2000
    }
    fun confirm(userRequested:Boolean,parked:Boolean?,now:Long):Boolean {
        if(last==null || now-last!! !in 0..5000) invalidate()
        if(!userRequested || parked!=true || !candidate) return false
        arrived=true;return true
    }
    fun invalidate() {candidate=false;count=0;first=null;last=null}
    fun reset() {arrived=false;invalidate()}
}
