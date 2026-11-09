package com.surpride.navhostpoc.ownership

import androidx.car.app.model.DateTimeWithZone
import androidx.car.app.model.Distance
import androidx.car.app.navigation.model.*
import java.util.TimeZone

/** No coordinates: Destination1.7.0 carries name/address/image, not CarLocation. */
data class DestinationIdentity(val name:String,val address:String?) {
    init {require(name.isNotBlank());require(name.length<=200);require(address==null || address.length<=300)}
    override fun toString()="DestinationIdentity(REDACTED)"
}
data class NextTurnMetadata(val cue:String,val road:String,val maneuverType:Int,val distanceMeters:Double,
    val seconds:Long,val atEpochMillis:Long)

/** Builds local payloads only; deliberately has no NavigationManager, focus, network or location access. */
object PrivacyTripFactory {
    fun hidden(turn:NextTurnMetadata,privateIdentity:DestinationIdentity):Trip {
        val tokens=listOfNotNull(privateIdentity.name,privateIdentity.address?.takeIf {it.isNotBlank()})
        require(tokens.none {turn.cue.contains(it,ignoreCase=true) || turn.road.contains(it,ignoreCase=true)}) {
            "Private identity in safety text: reject payload rather than altering instructions"
        }
        return base(turn).build()
    }
    fun revealed(turn:NextTurnMetadata,identity:DestinationIdentity,userRequested:Boolean,
        destinationDistanceMeters:Double,destinationSeconds:Long):Trip {
        require(userRequested) {"Explicit destination disclosure required"}
        val builder=Destination.Builder().setName(identity.name)
        identity.address?.takeIf {it.isNotBlank()}?.let(builder::setAddress)
        return base(turn).addDestination(builder.build(),estimate(destinationDistanceMeters,destinationSeconds,turn.atEpochMillis)).build()
    }
    private fun base(turn:NextTurnMetadata):Trip.Builder {
        require(turn.cue.isNotBlank() && turn.cue.length<=200 && turn.road.length<=200)
        require((turn.cue+turn.road).none {it.code<32})
        val step=Step.Builder(turn.cue).setRoad(turn.road).setManeuver(Maneuver.Builder(turn.maneuverType).build()).build()
        return Trip.Builder().setCurrentRoad(turn.road).addStep(step,estimate(turn.distanceMeters,turn.seconds,turn.atEpochMillis))
    }
    private fun estimate(distance:Double,seconds:Long,at:Long):TravelEstimate {
        require(distance.isFinite() && distance>=0 && seconds in 0..604800 && at>0)
        return TravelEstimate.Builder(Distance.create(distance,Distance.UNIT_METERS),
            DateTimeWithZone.create(Math.addExact(at,Math.multiplyExact(seconds,1000)),TimeZone.getTimeZone("UTC")))
            .setRemainingTimeSeconds(seconds).build()
    }
}
