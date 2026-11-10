package com.surpride.app.realdata

import org.json.JSONObject

sealed interface PreparedRouteReply {
    class Success(val packet:String,val preparedAtMillis:Long):PreparedRouteReply {
        override fun toString()="PreparedRouteReply.Success(REDACTED)"
    }
    data class Failure(val error:RouteError):PreparedRouteReply
}

/** Private trip snapshot. Departure time is captured later, at the user's actual start. */
class PreparedTripMetadata(val tripOrigin:GeoPoint,val returnToStart:Boolean,val availableMinutes:Int?,
    val returnByEpochMillis:Long?,val staySeconds:Long,val safetyBufferSeconds:Long,
    val initialReturnDurationSeconds:Long?,val initialReturnMeasuredAtMillis:Long?) {
    override fun toString()="PreparedTripMetadata(REDACTED)"
}

/** Private transport envelope. This never starts guidance or upgrades a MOCK response to LIVE. */
object PreparedRouteContract {
    const val TTL_MILLIS=3_600_000L
    const val MAX_BYTES=2_000_000
    fun prepare(response:ProxyHttpResponse,origin:GeoPoint,destination:GeoPoint,candidateId:String,
        privateLabel:String,privateReason:String,nonce:String,now:Long,trip:PreparedTripMetadata?=null,
        proxySession:ProxySettings?=null,expectedOptions:RouteOptions?=null):PreparedRouteReply {
        val parsed=ProxyRouteParser.parse(response.status,response.body,origin,destination,now,expectedOptions)
        if(parsed is RouteReply.Failure) return PreparedRouteReply.Failure(parsed.error)
        if((parsed as RouteReply.Success).leg.origin!=RouteOrigin.LIVE_API)
            return PreparedRouteReply.Failure(RouteError.INVALID_RESPONSE)
        return try {
            require(candidateId.matches(Regex("[A-Za-z0-9_-]{1,100}")))
            require(nonce.matches(Regex("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")))
            require(privateLabel.isNotBlank() && privateLabel.length<=200)
            require(privateReason.isNotBlank() && privateReason.length<=1000)
            require(now>0 && now<=Long.MAX_VALUE-TTL_MILLIS)
            val data=JSONObject(response.body)
            val guidance=data.getJSONObject("guidance")
            require(guidance.getJSONArray("segments").length() in 1..10000)
            require(guidance.getJSONArray("maneuvers").length() in 2..10000)
            val packet=JSONObject()
            listOf("schema_version","source","origin","destination","duration_seconds","distance_meters",
                "geometry","measured_at_epoch_ms","guidance").forEach {packet.put(it,data.get(it))}
            if(expectedOptions!=null) packet.put("request_options",data.getJSONObject("request_options"))
            packet.put("private_destination_label",privateLabel).put("private_reason",privateReason)
                .put("candidate_id",candidateId).put("session_nonce",nonce)
                .put("prepared_at_epoch_ms",now).put("expires_at_epoch_ms",now+TTL_MILLIS)
            trip?.let {
                require(it.tripOrigin==origin)
                require(it.availableMinutes==null || it.availableMinutes>0)
                require(it.returnByEpochMillis==null || it.returnByEpochMillis>0)
                require(it.staySeconds in 0..604800 && it.safetyBufferSeconds in 0..86400)
                require((it.initialReturnDurationSeconds==null)==(it.initialReturnMeasuredAtMillis==null))
                it.initialReturnDurationSeconds?.let {seconds->require(seconds in 1..604800)}
                it.initialReturnMeasuredAtMillis?.let {at->require(at>0 && at<=now+60_000)}
                packet.put("trip_origin",JSONObject().put("longitude",it.tripOrigin.longitude).put("latitude",it.tripOrigin.latitude))
                    .put("return_to_start",it.returnToStart).put("stay_seconds",it.staySeconds)
                    .put("safety_buffer_seconds",it.safetyBufferSeconds)
                it.availableMinutes?.let {minutes->packet.put("available_minutes",minutes)}
                it.returnByEpochMillis?.let {deadline->packet.put("return_by_epoch_ms",deadline)}
                it.initialReturnDurationSeconds?.let {seconds->packet.put("initial_return_duration_seconds",seconds)}
                it.initialReturnMeasuredAtMillis?.let {at->packet.put("initial_return_measured_at_epoch_ms",at)}
            }
            proxySession?.let {
                require(it.expiresAtMillis>now)
                packet.put("proxy_session",JSONObject().put("base_url",it.baseUrl).put("token",it.token)
                    .put("expires_at_ms",it.expiresAtMillis))
            }
            val body=packet.toString()
            require(body.toByteArray(Charsets.UTF_8).size<=MAX_BYTES)
            PreparedRouteReply.Success(body,now)
        } catch(_:Exception) {PreparedRouteReply.Failure(RouteError.INVALID_RESPONSE)}
    }
}

data class PendingLiveNavigation(val nonce:String,val candidateId:String,val preparedAtMillis:Long) {
    override fun toString()="PendingLiveNavigation(REDACTED)"
}
data class LiveNavigationVisit(val candidateId:String,val rating:Int,val completedAtMillis:Long)

object LiveNavigationResultPolicy {
    const val SOURCE="DEVICE_API_EMULATOR"
    fun accepts(pending:PendingLiveNavigation?,nonce:String?,candidateId:String?,source:String?,
        revealed:Boolean,stopped:Boolean,now:Long):Boolean =
        pending!=null && nonce==pending.nonce && candidateId==pending.candidateId && source==SOURCE &&
            revealed && stopped && pending.preparedAtMillis>0 && now>=pending.preparedAtMillis &&
            now-pending.preparedAtMillis<=PreparedRouteContract.TTL_MILLIS
}

/** Separate from the DEMO controller. Only manual completion can consume a pending session. */
class LiveNavigationJournal(pending:PendingLiveNavigation?=null,visits:List<LiveNavigationVisit> = emptyList(),completed:PendingLiveNavigation?=null) {
    var pending:PendingLiveNavigation?=pending
        private set
    var visits:List<LiveNavigationVisit> = visits.takeLast(30)
        private set
    var completed:PendingLiveNavigation?=completed
        private set
    fun hasCompleted(nonce:String?,candidateId:String?,source:String?,revealed:Boolean,stopped:Boolean,now:Long):Boolean =
        pending==null && LiveNavigationResultPolicy.accepts(completed,nonce,candidateId,source,revealed,stopped,now) &&
            visits.lastOrNull()?.let {it.candidateId==candidateId && it.rating in 1..5 &&
                it.completedAtMillis in completed!!.preparedAtMillis..now}==true
    fun complete(nonce:String?,candidateId:String?,source:String?,revealed:Boolean,stopped:Boolean,
        rating:Int,now:Long):Boolean {
        if(rating !in 1..5 || !LiveNavigationResultPolicy.accepts(pending,nonce,candidateId,source,revealed,stopped,now)) return false
        visits=(visits+LiveNavigationVisit(pending!!.candidateId,rating,now)).takeLast(30)
        completed=pending
        pending=null
        return true
    }
}
