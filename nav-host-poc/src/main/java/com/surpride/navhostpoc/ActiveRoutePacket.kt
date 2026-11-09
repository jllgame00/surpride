package com.surpride.navhostpoc

import android.content.Intent
import androidx.car.app.CarContext
import com.surpride.navhostpoc.guidance.GuidanceParser
import com.surpride.navhostpoc.guidance.GuidanceRoute
import com.surpride.navhostpoc.guidance.RouteRequestOptions
import com.surpride.navhostpoc.guidance.WaypointProgress
import com.surpride.navhostpoc.ownership.DestinationIdentity
import org.json.JSONObject
import java.io.File
import com.surpride.navhostpoc.guidance.RouteCoordinate
import com.surpride.navhostpoc.routing.ProxyRouteSettings
import com.surpride.navhostpoc.routing.RouteRefreshResult
import com.surpride.navhostpoc.returntrip.ReturnBudget
import com.surpride.navhostpoc.returntrip.ReturnEstimate

/** Private ephemeral handoff. Never serialize this object into logs or public Intent extras. */
internal class ActiveRoutePacket(val route:GuidanceRoute,val identity:DestinationIdentity,val reason:String,
    val candidateId:String?,val nonce:String?,private val measuredAt:Long,private val expiresAt:Long,
    private val envelope:JSONObject) {
    val requestOptions=RouteRequestOptions.parse(if(envelope.has("request_options")) envelope.getJSONObject("request_options") else null)
    init {WaypointProgress(route,requestOptions.waypoints.size)}
    private fun point(key:String):RouteCoordinate?=try {envelope.optJSONObject(key)?.let {RouteCoordinate(it.getDouble("longitude"),it.getDouble("latitude"))}} catch(_:Exception) {null}
    val destination get()=checkNotNull(point("destination"))
    val outboundDestination get()=point("trip_destination")?:destination
    val tripOrigin get()=point("trip_origin")?:point("origin")
    val returning get()=envelope.optBoolean("returning",false)
    val startedAt get()=envelope.optLong("trip_started_at_epoch_ms",0).takeIf {it>0}
    val proxySettings get()=ProxyRouteSettings.fromJson(envelope.optJSONObject("proxy_session"))
    val returnToStart:Boolean? get()=if(envelope.has("return_to_start")) envelope.optBoolean("return_to_start") else null
    fun budget(at:Long)=ReturnBudget(at,envelope.optInt("available_minutes",0).takeIf {it>0},
        envelope.optLong("return_by_epoch_ms",0).takeIf {it>0},returnToStart,
        envelope.optLong("stay_seconds",0).coerceIn(0,604800),envelope.optLong("safety_buffer_seconds",600).coerceIn(0,86400))
    fun initialReturnEstimate():ReturnEstimate? {
        val seconds=envelope.optLong("initial_return_duration_seconds",0);val at=envelope.optLong("initial_return_measured_at_epoch_ms",0)
        return if(seconds>0 && at>0) ReturnEstimate(seconds,at) else null
    }
    fun rememberStart(context:CarContext,at:Long) {envelope.put("trip_started_at_epoch_ms",at);File(context.cacheDir,"route-guidance.json").writeText(envelope.toString())}
    fun replace(context:CarContext,value:RouteRefreshResult.Success,isReturn:Boolean,at:Long?,
        remainingWaypointCount:Int=requestOptions.waypoints.size):ActiveRoutePacket {
        require(remainingWaypointCount in 0..requestOptions.waypoints.size)
        // A two-point refresh cannot silently skip a waypoint. Caller must first prove all passed.
        require(isReturn || remainingWaypointCount==0) {"WAYPOINT_REFRESH_NOT_SUPPORTED"}
        val next=JSONObject(value.payload)
        for(key in listOf("candidate_id","session_nonce","private_reason","trip_origin","return_to_start","available_minutes","return_by_epoch_ms",
            "stay_seconds","safety_buffer_seconds","initial_return_duration_seconds","initial_return_measured_at_epoch_ms","proxy_session","development_public_replay"))
            if(envelope.has(key)) next.put(key,envelope.get(key))
        // Legacy packets predate trip_origin. Capture their original origin before a reroute
        // response replaces origin with the current position; never redefine the return target.
        if(!next.has("trip_origin")) envelope.optJSONObject("origin")?.let {next.put("trip_origin",it)}
        next.put("trip_destination",envelope.optJSONObject("trip_destination")?:envelope.getJSONObject("destination"))
        next.put("outbound_private_label",identity.name).put("returning",isReturn)
        at?.let {next.put("trip_started_at_epoch_ms",it)}
        next.put("expires_at_epoch_ms",value.expiresAtEpochMillis)
        val saved=parse(next.toString())
        File(context.cacheDir,"route-guidance.json").writeText(next.toString())
        return saved
    }
    fun isFresh(now:Long)=validAt(measuredAt,expiresAt,now)
    companion object {
        internal fun validAt(measuredAt:Long,expiresAt:Long,now:Long)=measuredAt>0 &&
            now-measuredAt in -60_000..3_600_000 && now<expiresAt
        fun load(context:CarContext,intent:Intent?=null):ActiveRoutePacket?=try {
            val file=File(context.cacheDir,"route-guidance.json")
            val uri=intent?.data
            val raw=if(uri!=null) {
                require(uri.scheme=="content" && uri.authority=="com.surpride.app.navigationfiles")
                val bytes=context.contentResolver.openInputStream(uri)!!.use {it.readBytesBounded()}
                String(bytes,Charsets.UTF_8)
            } else {require(file.length() in 1..2_000_000);file.readText()}
            val json=JSONObject(raw)
            if(uri!=null) {
                val now=System.currentTimeMillis()
                require(json.getLong("prepared_at_epoch_ms") in (now-3_600_000)..(now+60_000))
                require(json.getLong("expires_at_epoch_ms")>now)
                require(json.getString("session_nonce").matches(Regex("[a-fA-F0-9-]{36}")))
            }
            val packet=parse(raw)
            if(uri!=null) file.writeText(raw)
            packet
        } catch(_:Exception) {null}
        private fun parse(raw:String):ActiveRoutePacket {
            val json=JSONObject(raw)
            val route=GuidanceParser.parse(raw,System.currentTimeMillis())
            return ActiveRoutePacket(route,DestinationIdentity(json.optString("outbound_private_label",json.getString("private_destination_label")),null),
                json.optString("private_reason","실제 자동차 경로 · 개발용 공개 장소 검증"),
                json.optString("candidate_id").takeIf {it.isNotBlank()},json.optString("session_nonce").takeIf {it.isNotBlank()},
                json.getLong("measured_at_epoch_ms"),minOf(json.optLong("expires_at_epoch_ms",Long.MAX_VALUE),json.getLong("measured_at_epoch_ms")+3_600_000),json)
        }
        private fun java.io.InputStream.readBytesBounded():ByteArray {
            val out=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192)
            while(true) {val n=read(buffer);if(n<0) break;require(out.size()+n<=2_000_000);out.write(buffer,0,n)}
            return out.toByteArray()
        }
    }
}
