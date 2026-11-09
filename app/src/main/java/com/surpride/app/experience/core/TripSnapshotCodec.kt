package com.surpride.app.experience.core

import java.io.*

/** Versioned bounded format: no Java object deserialization, destination payloads or GPS coordinates. */
object TripSnapshotCodec {
    private const val MAGIC = 0x53555232
    private const val EXCLUSIONS_EXTENSION = 0x45584331
    fun encode(s: MysteryTripState): ByteArray = ByteArrayOutputStream().use { bytes ->
        DataOutputStream(bytes).use { out ->
            out.writeInt(MAGIC); out.writeInt(DemoCatalog.VERSION)
            out.writeUTF(s.phase.name); out.writeUTF(s.panel.name); out.writeInt(s.setupStep)
            val p=s.preferences
            out.writeUTF(p.mood.name); out.writeUTF(p.scope.name)
            out.writeInt(p.categories.size); p.categories.sortedBy {it.name}.forEach {out.writeUTF(it.name)}
            out.writeUTF(p.companion.name); out.writeInt(p.availableMinutes ?: -1)
            out.writeLong(p.returnByEpochMillis ?: -1); out.writeBoolean(p.returnToStart); out.writeInt(p.randomness)
            out.writeLong(s.seed); out.writeUTF(s.tripId); out.writeInt(s.progress); out.writeBoolean(s.arrived)
            out.writeBoolean(s.hintVisible); out.writeInt(s.rating ?: -1)
            out.writeBoolean(s.error!=null); s.error?.let(out::writeUTF)
            out.writeBoolean(s.plan!=null)
            s.plan?.let { plan ->
                out.writeUTF(plan.candidate.id)
                val e=plan.estimate
                out.writeInt(e.outboundMinutes); out.writeInt(e.stayMinutes); out.writeInt(e.returnMinutes); out.writeInt(e.bufferMinutes)
                out.writeUTF(e.origin.name); out.writeDouble(e.verifiedDistanceKm ?: -1.0); out.writeInt(plan.totalMinutes)
                val score=plan.score
                listOf(score.mood,score.place,score.companion,score.season,score.weather,score.variation).forEach(out::writeInt)
                out.writeInt(plan.warnings.size); plan.warnings.sortedBy {it.name}.forEach {out.writeUTF(it.name)}
            }
            out.writeInt(s.history.size)
            s.history.forEach { v -> out.writeUTF(v.tripId); out.writeUTF(v.candidateId); out.writeUTF(v.mood.name); out.writeInt(v.rating); out.writeLong(v.completedAtMillis); out.writeBoolean(v.simulatedArrival) }
            // Preserve the shipped v1 bytes when there are no exclusions.
            if (p.excludedCategories.isNotEmpty()) {
                out.writeInt(EXCLUSIONS_EXTENSION)
                out.writeInt(p.excludedCategories.size)
                p.excludedCategories.sortedBy { it.name }.forEach { out.writeUTF(it.name) }
            }
        }
        bytes.toByteArray()
    }
    fun decode(bytes: ByteArray, catalog: List<DestinationCandidate>): MysteryTripState {
        require(bytes.size <= 256_000)
        return DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            require(input.readInt()==MAGIC && input.readInt()==DemoCatalog.VERSION)
            val phase=TripPhase.valueOf(input.readUTF()); val panel=ExperiencePanel.valueOf(input.readUTF())
            val setup=input.readInt().also {require(it in 0..2)}
            val mood=Mood.valueOf(input.readUTF()); val scope=TravelScope.valueOf(input.readUTF())
            val categories=List(input.count(PlaceCategory.entries.size)) {PlaceCategory.valueOf(input.readUTF())}.toSet()
            val companion=Companion.valueOf(input.readUTF()); val minutes=input.readInt().let {if(it==-1) null else it.also {require(it in 1..10080)}}
            val deadline=input.readLong().let {if(it==-1L) null else it.also {require(it>0)}}
            val returns=input.readBoolean(); val randomness=input.readInt().also {require(it in 0..100)}
            val prefs=DrivePreferences(mood,scope,categories,companion,minutes,deadline,returns,randomness)
            val seed=input.readLong(); val tripId=input.readUTF(); val progress=input.readInt().also {require(it in 0..3)}
            val arrived=input.readBoolean(); val hint=input.readBoolean(); val rating=input.readInt().let {if(it==-1) null else it.also {require(it in 1..5)}}
            val error=if(input.readBoolean()) input.readUTF() else null
            val plan=if(input.readBoolean()) {
                val id=input.readUTF(); val candidate=catalog.single {it.id==id}
                val e=JourneyEstimate(input.readInt(),input.readInt(),input.readInt(),input.readInt(),EstimateOrigin.valueOf(input.readUTF()),input.readDouble().let {if(it<0) null else it})
                require(e.valid())
                val total=input.readInt(); require(total==e.total(returns) && total>0)
                val score=RecommendationScore(input.readInt(),input.readInt(),input.readInt(),input.readInt(),input.readInt(),input.readInt())
                val warnings=List(input.count(PlanWarning.entries.size)){PlanWarning.valueOf(input.readUTF())}.toSet()
                RecommendationResult.Success(candidate,e,total,score,warnings)
            } else null
            val history=List(input.count(30)) {
                val id=input.readUTF(); val place=input.readUTF(); require(catalog.any {it.id==place})
                val m=Mood.valueOf(input.readUTF()); val r=input.readInt().also {require(it in 1..5)}
                DemoVisit(id,place,m,r,input.readLong(),input.readBoolean())
            }
            val excludedCategories = if (input.available() == 0) emptySet() else {
                require(input.readInt() == EXCLUSIONS_EXTENSION)
                val names = List(input.count(PlaceCategory.entries.size)) { PlaceCategory.valueOf(input.readUTF()) }
                require(names.distinct().size == names.size)
                names.toSet()
            }
            require(input.available()==0)
            if(phase !in setOf(TripPhase.SETUP,TripPhase.PLANNING)) require(plan!=null)
            MysteryTripState(phase,panel,prefs.copy(excludedCategories=excludedCategories),setup,plan,error,seed,tripId,progress,arrived,hint,rating,history)
        }
    }
    private fun DataInputStream.count(max: Int) = readInt().also {require(it in 0..max)}
}
