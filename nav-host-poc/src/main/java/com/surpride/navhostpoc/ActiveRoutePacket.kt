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
import java.io.IOException
import android.util.AtomicFile
import com.surpride.navhostpoc.guidance.RouteCoordinate
import com.surpride.navhostpoc.routing.ProxyRouteSettings
import com.surpride.navhostpoc.routing.RouteRefreshResult
import com.surpride.navhostpoc.returntrip.ReturnBudget
import com.surpride.navhostpoc.returntrip.ReturnEstimate

/** Private ephemeral handoff. Never serialize this object into logs or public Intent extras. */
internal class ActiveRoutePacket(private var routeData:GuidanceRoute?,val identity:DestinationIdentity,val reason:String,
    val candidateId:String?,val nonce:String?,private val measuredAt:Long,private val expiresAt:Long,
    private var envelope:JSONObject,private var committedRaw:String) {
    var requestOptions=RouteRequestOptions.parse(if(envelope.has("request_options")) envelope.getJSONObject("request_options") else null)
        private set
    val route get()=checkNotNull(routeData) {"SESSION_COMPLETED"}
    val guidanceRoute get()=routeData
    val source get()=com.surpride.navhostpoc.guidance.RouteDataSource.valueOf(envelope.getString("source"))
    init {routeData?.let {WaypointProgress(it,requestOptions.waypoints.size)}}
    private fun point(key:String):RouteCoordinate?=try {envelope.optJSONObject(key)?.let {RouteCoordinate(it.getDouble("longitude"),it.getDouble("latitude"))}} catch(_:Exception) {null}
    val destination get()=checkNotNull(point("destination"))
    val outboundDestination get()=point("trip_destination")?:destination
    val tripOrigin get()=point("trip_origin")?:point("origin")
    val returning get()=envelope.optBoolean("returning",false)
    var outcome=SessionOutcomeCodec.read(envelope,System.currentTimeMillis(),expiresAt)
        private set
    val arrivalConfirmed get()=outcome.arrivedAt!=null && (!returning || outcome.returnPhase==ReturnPhase.COMPLETED)
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
    fun rememberStart(context:CarContext,at:Long)=synchronized(lock) {
        require(at>0 && at<=System.currentTimeMillis()+60_000)
        require(startedAt==null || startedAt==at) {"DEPARTURE_ALREADY_RECORDED"}
        val next=JSONObject(committedRaw).put("trip_started_at_epoch_ms",at)
        update(context,next,outcome)
    }
    fun rememberDisclosure(context:CarContext)=synchronized(lock) {
        update(context,JSONObject(committedRaw),outcome.copy(revealed=true))
    }
    fun rememberArrival(context:CarContext,at:Long)=synchronized(lock) {
        require(at>0 && at<=System.currentTimeMillis())
        update(context,if(returning) receipt() else JSONObject(committedRaw),outcome.copy(revealed=true,arrivedAt=at,
            returnPhase=if(returning) ReturnPhase.COMPLETED else outcome.returnPhase))
    }
    fun rememberReturnRequest(context:CarContext)=synchronized(lock) {
        require(arrivalConfirmed && !returning && tripOrigin!=null)
        update(context,JSONObject(committedRaw),outcome.copy(returnPhase=ReturnPhase.REQUESTED))
    }
    private fun update(context:CarContext,next:JSONObject,state:SessionOutcome) {
        SessionOutcomeCodec.attach(next,state,System.currentTimeMillis(),expiresAt)
        val saved=save(context,next);envelope=saved.envelope;committedRaw=saved.committedRaw;outcome=saved.outcome;routeData=saved.routeData;requestOptions=saved.requestOptions
    }
    private fun receipt()=JSONObject().apply {
        for(key in RECEIPT_KEYS) if(envelope.has(key)) put(key,envelope.get(key))
        put("retired_version",1).put("private_destination_label",identity.name)
    }
    fun cancel(context:CarContext):Boolean=synchronized(lock) {
        val atomic=AtomicFile(File(context.cacheDir,"route-guidance.json"))
        if(read(atomic)!=committedRaw) return@synchronized false
        remove(atomic);routeData=null;envelope=JSONObject();committedRaw="";true
    }
    private fun save(context:CarContext,next:JSONObject):ActiveRoutePacket=synchronized(lock) {
        val raw=next.toString();val saved=parse(raw)
        val atomic=AtomicFile(File(context.cacheDir,"route-guidance.json"))
        require(read(atomic)==committedRaw) {"STALE_PACKET"}
        write(atomic,raw)
        saved
    }
    fun isCurrent(context:CarContext):Boolean=synchronized(lock) {
        try {isFresh(System.currentTimeMillis()) && read(AtomicFile(File(context.cacheDir,"route-guidance.json")))==committedRaw} catch(_:Exception) {false}
    }
    fun replace(context:CarContext,value:RouteRefreshResult.Success,isReturn:Boolean,at:Long?,
        remainingWaypointCount:Int=requestOptions.waypoints.size):ActiveRoutePacket=synchronized(lock) {
        require(value.measuredAtEpochMillis>=measuredAt && outcome.returnPhase!=ReturnPhase.COMPLETED)
        require(isReturn || !arrivalConfirmed)
        require(remainingWaypointCount in 0..requestOptions.waypoints.size)
        // A two-point refresh cannot silently skip a waypoint. Caller must first prove all passed.
        require(isReturn || remainingWaypointCount==0) {"WAYPOINT_REFRESH_NOT_SUPPORTED"}
        val next=JSONObject(value.payload)
        require(next.getLong("measured_at_epoch_ms")==value.measuredAtEpochMillis)
        val target=next.getJSONObject("destination")
        require(RouteCoordinate(target.getDouble("longitude"),target.getDouble("latitude"))==if(isReturn) tripOrigin else destination)
        require(startedAt==null || at==null || at==startedAt)
        for(key in listOf("candidate_id","session_nonce","prepared_at_epoch_ms","private_reason","trip_origin","return_to_start","available_minutes","return_by_epoch_ms",
            "stay_seconds","safety_buffer_seconds","initial_return_duration_seconds","initial_return_measured_at_epoch_ms","proxy_session","development_public_replay"))
            if(envelope.has(key)) next.put(key,envelope.get(key))
        // Legacy packets predate trip_origin. Capture their original origin before a reroute
        // response replaces origin with the current position; never redefine the return target.
        if(!next.has("trip_origin")) envelope.optJSONObject("origin")?.let {next.put("trip_origin",it)}
        next.put("trip_destination",envelope.optJSONObject("trip_destination")?:envelope.getJSONObject("destination"))
        next.put("outbound_private_label",identity.name).put("returning",isReturn)
        (startedAt?:at)?.let {next.put("trip_started_at_epoch_ms",it)}
        next.put("expires_at_epoch_ms",value.expiresAtEpochMillis)
        envelope.optJSONObject("terminal_state")?.let {next.put("terminal_state",JSONObject(it.toString()))}
        SessionOutcomeCodec.attach(next,outcome.copy(returnPhase=if(isReturn) ReturnPhase.READY else outcome.returnPhase),System.currentTimeMillis(),
            minOf(expiresAt,value.expiresAtEpochMillis))
        save(context,next)
    }
    fun isFresh(now:Long)=validAt(measuredAt,expiresAt,now) &&
        (envelope.optJSONObject("terminal_state")?.getLong("expires_at_epoch_ms")?.let {now<it}?:true)
    companion object {
        const val RESUME_ACTION="com.surpride.navhostpoc.RESUME_SESSION"
        private val lock=Any()
        private val RECEIPT_KEYS=setOf("source","candidate_id","session_nonce","measured_at_epoch_ms","prepared_at_epoch_ms",
            "expires_at_epoch_ms","returning","private_destination_label","terminal_state")
        private fun remove(atomic:AtomicFile) {
            atomic.delete()
            if(listOf("", ".bak", ".new").any {File(atomic.baseFile.path+it).exists()}) {
                android.util.Log.w("SurprideRetention","PACKET_DELETE_FAILED")
                throw IOException("PACKET_DELETE_FAILED")
            }
        }
        private fun read(atomic:AtomicFile)=atomic.openRead().use {String(it.readBytesBounded(),Charsets.UTF_8)}
        private fun write(atomic:AtomicFile,raw:String) {
            val bytes=raw.toByteArray(Charsets.UTF_8);require(bytes.size in 1..2_000_000)
            val output=atomic.startWrite()
            try {
                output.write(bytes);output.flush();output.fd.sync()
                atomic.finishWrite(output)
                // AtomicFile may log a failed rename instead of throwing; never publish a false commit.
                if(read(atomic)!=raw) throw IOException("PACKET_COMMIT_FAILED")
            } catch(_:Exception) {atomic.failWrite(output);throw IOException("PACKET_WRITE_FAILED")}
        }
        internal fun validAt(measuredAt:Long,expiresAt:Long,now:Long)=measuredAt>0 &&
            now-measuredAt in -60_000..3_600_000 && now<expiresAt
        fun load(context:CarContext,intent:Intent?=null):ActiveRoutePacket?=synchronized(lock) {try {
            val file=File(context.cacheDir,"route-guidance.json")
            val atomic=AtomicFile(file)
            if(!file.exists() && !File(file.path+".bak").exists() && File(file.path+".new").exists()) remove(atomic)
            if(file.exists() || File(file.path+".bak").exists()) {
                val saved=try {JSONObject(read(atomic))} catch(_:Exception) {null}
                val now=System.currentTimeMillis()
                val deadline=saved?.let {minOf(it.optLong("expires_at_epoch_ms",Long.MAX_VALUE),
                    it.optLong("measured_at_epoch_ms",now)+3_600_000,it.optLong("prepared_at_epoch_ms",now)+3_600_000,
                    it.optJSONObject("terminal_state")?.optLong("expires_at_epoch_ms",Long.MAX_VALUE)?:Long.MAX_VALUE)}?:
                    (file.lastModified()+3_600_000)
                if(now>=deadline) remove(atomic)
            }
            val uri=intent?.data
            val raw=if(uri!=null) {
                require(uri.scheme=="content" && uri.authority=="com.surpride.app.navigationfiles")
                val bytes=context.contentResolver.openInputStream(uri)!!.use {it.readBytesBounded()}
                String(bytes,Charsets.UTF_8)
            } else read(atomic)
            val json=JSONObject(raw)
            if(uri!=null) {
                val now=System.currentTimeMillis()
                require(json.getLong("prepared_at_epoch_ms") in (now-3_600_000)..(now+60_000))
                require(json.getLong("expires_at_epoch_ms")>now)
                require(json.getString("session_nonce").matches(Regex("[a-fA-F0-9-]{36}")))
                require(!json.has("terminal_state")) // A provider handoff cannot assert user outcomes.
            }
            var packet=parse(raw)
            require(packet.isFresh(System.currentTimeMillis()))
            if(intent?.action==RESUME_ACTION) require(uri==null && packet.nonce!=null && packet.candidateId!=null &&
                intent.getStringExtra("session_nonce")==packet.nonce && intent.getStringExtra("candidate_id")==packet.candidateId)
            if(uri!=null) {
                val stored=try {read(atomic)} catch(_:Exception) {null}
                val sameNonce=try {stored!=null && JSONObject(stored).optString("session_nonce")==packet.nonce} catch(_:Exception) {false}
                val existing=try {stored?.let(::parse)} catch(_:Exception) {null}
                require(!sameNonce || existing!=null) // Reimport must not erase damaged/expired outcomes.
                if(existing?.nonce!=null && existing.nonce==packet.nonce) {
                    require(existing.guidanceRoute!=null) // A consumed handoff cannot resurrect a completed route.
                    require(existing.sessionIdentity()==packet.sessionIdentity())
                    packet=existing
                } else write(atomic,raw)
            }
            if(packet.envelope.has("proxy_session") && packet.proxySettings?.expiresAtEpochMillis?.let {it>System.currentTimeMillis()}!=true) {
                val clean=JSONObject(packet.committedRaw).apply {remove("proxy_session")}
                if(clean.has("terminal_state")) SessionOutcomeCodec.attach(clean,packet.outcome,System.currentTimeMillis(),packet.expiresAt)
                return@synchronized packet.save(context,clean)
            }
            packet
        } catch(_:Exception) {null}}
        private fun parse(raw:String):ActiveRoutePacket {
            val json=JSONObject(raw)
            val retired=json.has("retired_version")
            val route=if(retired) null else GuidanceParser.parse(raw,System.currentTimeMillis())
            if(json.has("session_nonce")) require(json.getString("session_nonce").matches(Regex("[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}")))
            if(json.has("candidate_id")) require(json.getString("candidate_id").matches(Regex("[A-Za-z0-9_-]{1,100}")))
            val now=System.currentTimeMillis();val measured=json.getLong("measured_at_epoch_ms")
            val prepared=if(json.has("prepared_at_epoch_ms")) json.getLong("prepared_at_epoch_ms").also {require(it>0 && it<=now+60_000 && now-it<3_600_000)} else measured
            val expires=minOf(json.optLong("expires_at_epoch_ms",Long.MAX_VALUE),measured+3_600_000,prepared+3_600_000)
            require(validAt(measured,expires,now))
            val state=SessionOutcomeCodec.read(json,now,expires)
            if(retired) {
                require(json.getInt("retired_version")==1 && json.keys().asSequence().toSet().all {it in RECEIPT_KEYS || it=="retired_version"})
                require(state.returnPhase==ReturnPhase.COMPLETED && state.revealed && state.arrivedAt!=null)
                com.surpride.navhostpoc.guidance.RouteDataSource.valueOf(json.getString("source"))
            }
            return ActiveRoutePacket(route,DestinationIdentity(json.optString("outbound_private_label",json.getString("private_destination_label")),null),
                json.optString("private_reason","실제 자동차 경로 · 개발용 공개 장소 검증"),
                json.optString("candidate_id").takeIf {it.isNotBlank()},json.optString("session_nonce").takeIf {it.isNotBlank()},
                measured,expires,json,raw)
        }
        private fun java.io.InputStream.readBytesBounded():ByteArray {
            val out=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192)
            while(true) {val n=read(buffer);if(n<0) break;require(out.size()+n<=2_000_000);out.write(buffer,0,n)}
            return out.toByteArray()
        }
    }
    private fun sessionIdentity():List<Any?> = listOf(nonce,candidateId,envelope.optLong("prepared_at_epoch_ms"),outboundDestination,
        tripOrigin,identity.name)
}
