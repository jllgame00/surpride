package com.surpride.app.moodinput

import com.surpride.app.experience.core.*

/** Internal Activity Result payload: preferences only, never conversations, places or route ownership. */
object PreferenceInputContract {
    const val EXTRA="com.surpride.preferences.v1"
    fun encode(p:DrivePreferences):ByteArray=TripSnapshotCodec.encode(MysteryTripState(preferences=p))
    fun decode(bytes:ByteArray?):DrivePreferences?=try {
        require(bytes!=null && bytes.size<=4096)
        val state=TripSnapshotCodec.decode(bytes,DemoCatalog.candidates)
        require(state.phase==TripPhase.SETUP && state.plan==null && state.history.isEmpty() && state.tripId.isEmpty())
        val p=state.preferences
        require(p.categories.intersect(p.excludedCategories).isEmpty())
        p
    } catch(_:Exception) {null}
}
