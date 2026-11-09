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
        stopped:Boolean,rating:Int,now:Long):Boolean=synchronized(lock) {
        val journal=load(context)
        journal.complete(nonce,candidateId,source,revealed,stopped,rating,now) && persist(context,journal)
    }
    private fun load(context:Context):LiveNavigationJournal=try {
        val json=JSONObject(context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getString("journal","{}")?:"{}")
        val pending=json.optJSONObject("pending")?.let {
            PendingLiveNavigation(it.getString("nonce"),it.getString("candidate_id"),it.getLong("prepared_at_epoch_ms"))
        }
        val visits=json.optJSONArray("visits")?:JSONArray()
        LiveNavigationJournal(pending,(0 until visits.length()).map {i->
            val visit=visits.getJSONObject(i)
            LiveNavigationVisit(visit.getString("candidate_id"),visit.getInt("rating"),visit.getLong("completed_at_epoch_ms"))
        }.filter {it.rating in 1..5 && it.completedAtMillis>0 && SeoulPilotCatalog.places.any {place->place.id==it.candidateId}})
    } catch(_:Exception) {LiveNavigationJournal()}
    private fun persist(context:Context,journal:LiveNavigationJournal):Boolean {
        val json=JSONObject()
        journal.pending?.let {json.put("pending",JSONObject().put("nonce",it.nonce).put("candidate_id",it.candidateId)
            .put("prepared_at_epoch_ms",it.preparedAtMillis))}
        json.put("visits",JSONArray().apply {journal.visits.forEach {visit->
            put(JSONObject().put("candidate_id",visit.candidateId).put("rating",visit.rating)
                .put("completed_at_epoch_ms",visit.completedAtMillis))
        }})
        return context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().putString("journal",json.toString()).commit()
    }
}
