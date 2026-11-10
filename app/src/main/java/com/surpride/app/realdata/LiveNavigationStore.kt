package com.surpride.app.realdata

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Stores public review fields and one opaque pending token, never route/GPS/provider data. */
object LiveNavigationStore {
    private const val PREFS="live_navigation_emulator"
    private val lock=Any()
    fun read(context:Context):LiveNavigationJournal=synchronized(lock) {load(context)}
    fun prepare(context:Context,pending:PendingLiveNavigation):Boolean=synchronized(lock) {
        persist(context,LiveNavigationJournal(pending,load(context).visits))
    }
    fun cancel(context:Context,nonce:String) = synchronized(lock) {
        val journal=load(context)
        if(journal.pending?.nonce==nonce) persist(context,LiveNavigationJournal(null,journal.visits)) else true
    }
    fun complete(context:Context,nonce:String?,candidateId:String?,source:String?,revealed:Boolean,
        stopped:Boolean,rating:Int,now:Long,arrivalConfirmed:Boolean?=null):Boolean=synchronized(lock) {
        val journal=load(context)
        journal.complete(nonce,candidateId,source,revealed,stopped,rating,now,arrivalConfirmed) && persist(context,journal)
    }
    private fun load(context:Context):LiveNavigationJournal=try {
        val json=JSONObject(context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getString("journal","{}")?:"{}")
        val pending=try {json.optJSONObject("pending")?.let {
            PendingLiveNavigation(it.getString("nonce"),it.getString("candidate_id"),it.getLong("prepared_at_epoch_ms"))
        }} catch(_:Exception) {null}
        val completed=try {json.optJSONObject("completed")?.let {
            val token=it.getString("nonce");val candidate=it.getString("candidate_id")
            require(token.matches(Regex("[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}")))
            require(SeoulPilotCatalog.places.any {place->place.id==candidate})
            PendingLiveNavigation(token,candidate,it.getLong("prepared_at_epoch_ms"))
        }} catch(_:Exception) {null} // A damaged receipt cannot hide the independent visit history.
        val visits=json.optJSONArray("visits")?:JSONArray()
        LiveNavigationJournal(pending,(0 until visits.length()).mapNotNull {i->try {
            val visit=visits.getJSONObject(i)
            require(visit.get("rating") is Number && visit.get("rating").toString().matches(Regex("[1-5]")))
            require(visit.get("completed_at_epoch_ms") is Number &&
                visit.get("completed_at_epoch_ms").toString().matches(Regex("[0-9]+")))
            LiveNavigationVisit(visit.getString("candidate_id"),visit.getInt("rating"),visit.getLong("completed_at_epoch_ms"),
                visit.opt("arrival_confirmed") as? Boolean)
        } catch(_:Exception) {null}}.filter {it.completedAtMillis>0 && SeoulPilotCatalog.places.any {place->place.id==it.candidateId}},completed)
    } catch(_:Exception) {LiveNavigationJournal()}
    private fun persist(context:Context,journal:LiveNavigationJournal):Boolean {
        val json=JSONObject()
        journal.pending?.let {json.put("pending",JSONObject().put("nonce",it.nonce).put("candidate_id",it.candidateId)
            .put("prepared_at_epoch_ms",it.preparedAtMillis))}
        journal.completed?.takeIf {System.currentTimeMillis()-it.preparedAtMillis in 0..PreparedRouteContract.TTL_MILLIS}?.let {
            json.put("completed",JSONObject().put("nonce",it.nonce).put("candidate_id",it.candidateId).put("prepared_at_epoch_ms",it.preparedAtMillis))
        }
        json.put("visits",JSONArray().apply {journal.visits.forEach {visit->
            put(JSONObject().put("candidate_id",visit.candidateId).put("rating",visit.rating)
                .put("completed_at_epoch_ms",visit.completedAtMillis).apply {
                    visit.arrivalConfirmed?.let {put("arrival_confirmed",it)}
                })
        }})
        val prefs=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
        val previous=prefs.getString("journal",null)
        if(prefs.edit().putString("journal",json.toString()).commit()) return true
        // A failed SharedPreferences commit may have changed its in-process map.
        prefs.edit().putString("journal",previous).commit()
        return false
    }
}
