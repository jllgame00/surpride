package com.surpride.navhostpoc

import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

internal enum class ReturnPhase { NONE, REQUESTED, READY, COMPLETED }

/** Explicit user outcomes only; never GPS confidence, arrival candidates, focus or voice state. */
internal data class SessionOutcome(val revealed:Boolean=false,val arrivedAt:Long?=null,val returnPhase:ReturnPhase=ReturnPhase.NONE) {
    override fun toString()="SessionOutcome(REDACTED)"
}

internal object SessionOutcomeCodec {
    private fun canonical(value:Any?):String=when(value) {
        is JSONObject->value.keys().asSequence().toList().sorted().joinToString(",","{","}") {JSONObject.quote(it)+":"+canonical(value.get(it))}
        is JSONArray->(0 until value.length()).joinToString(",","[","]") {canonical(value.get(it))}
        is String->JSONObject.quote(value)
        else->value.toString()
    }
    private fun hash(value:JSONObject)=MessageDigest.getInstance("SHA-256").digest(canonical(value).toByteArray(Charsets.UTF_8))
        .joinToString("") {"%02x".format(it)}
    private fun packetHash(json:JSONObject)=hash(JSONObject(json.toString()).apply {remove("terminal_state")})
    private fun unsigned(state:JSONObject)=JSONObject(state.toString()).apply {remove("checksum")}
    private fun number(json:JSONObject,key:String):Long {
        val value=json.get(key);require(value is Number && value.toString().matches(Regex("[0-9]+")))
        return value.toString().toLong()
    }
    fun read(json:JSONObject,now:Long,expiresAt:Long):SessionOutcome {
        if(!json.has("terminal_state")) return SessionOutcome()
        val state=json.getJSONObject("terminal_state")
        require(state.keys().asSequence().toSet()==setOf("version","packet_hash","updated_at_epoch_ms","expires_at_epoch_ms","revealed","arrived_at_epoch_ms","return_phase","checksum"))
        require(number(state,"version")==1L && state.getString("packet_hash")==packetHash(json) && state.getString("checksum")==hash(unsigned(state)))
        val updated=number(state,"updated_at_epoch_ms");val expires=number(state,"expires_at_epoch_ms")
        require(updated>0 && updated<=now+60_000 && expires>updated && now<expires && expires<=expiresAt && expires-updated<=3_600_000)
        require(state.get("revealed") is Boolean)
        val revealed=state.getBoolean("revealed")
        val arrived=if(state.isNull("arrived_at_epoch_ms")) null else number(state,"arrived_at_epoch_ms").also {require(it in 1..updated && revealed)}
        val phase=ReturnPhase.valueOf(state.getString("return_phase"))
        val returning=json.optBoolean("returning",false)
        require(phase!=ReturnPhase.REQUESTED || (!returning && arrived!=null))
        require(phase !in listOf(ReturnPhase.READY,ReturnPhase.COMPLETED) || returning)
        require(phase!=ReturnPhase.COMPLETED || arrived!=null)
        return SessionOutcome(revealed,arrived,phase)
    }
    fun attach(json:JSONObject,outcome:SessionOutcome,now:Long,expiresAt:Long) {
        val deadline=minOf(expiresAt,json.optJSONObject("terminal_state")?.getLong("expires_at_epoch_ms")?:expiresAt)
        json.remove("terminal_state")
        val state=JSONObject().put("version",1).put("packet_hash",packetHash(json))
            .put("updated_at_epoch_ms",now).put("expires_at_epoch_ms",deadline).put("revealed",outcome.revealed)
            .put("arrived_at_epoch_ms",outcome.arrivedAt?:JSONObject.NULL).put("return_phase",outcome.returnPhase.name)
        state.put("checksum",hash(state));json.put("terminal_state",state)
    }
}
