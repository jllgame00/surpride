package com.surpride.navhostpoc.ownership

import android.os.Parcel
import androidx.car.app.model.*
import androidx.car.app.navigation.model.*
import androidx.car.app.serialization.Bundleable
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.TimeZone

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34])
class TripPrivacyContractTest {
    private val identity=DestinationIdentity("테스트 전용 목적지", "테스트 전용 주소")
    private val turn=NextTurnMetadata("우회전", "검증도로",Maneuver.TYPE_TURN_NORMAL_RIGHT,300.0,60,1_800_000_000_000)
    private fun roundTrip(trip:Trip):Trip {
        val parcel=Parcel.obtain()
        try {Bundleable.create(trip).writeToParcel(parcel,0);parcel.setDataPosition(0);return Bundleable.CREATOR.createFromParcel(parcel).get() as Trip}
        finally {parcel.recycle()}
    }
    @Test fun hiddenTripHasStepsAndStepEtaButNoDestinationOrFinalCoordinatesAfterSerialization() {
        val trip=roundTrip(PrivacyTripFactory.hidden(turn,identity))
        assertTrue(trip.destinations.isEmpty());assertTrue(trip.destinationTravelEstimates.isEmpty())
        assertEquals(1,trip.steps.size);assertEquals(1,trip.stepTravelEstimates.size)
        assertEquals("우회전",trip.steps[0].cue!!.toString());assertEquals(60L,trip.stepTravelEstimates[0].remainingTimeSeconds)
        for(type in listOf(Trip::class.java,Destination::class.java,Step::class.java,Maneuver::class.java,TravelEstimate::class.java))
            assertFalse(type.declaredFields.any {it.name.contains("location",true)||it.name.contains("latitude",true)||it.name.contains("longitude",true)})
        assertFalse(trip.toString().contains(identity.name));assertFalse(identity.toString().contains(identity.name))
    }
    @Test fun emptyDestinationIsInvalidButEntireDestinationListCanBeOmitted() {
        assertThrows(IllegalStateException::class.java) {Destination.Builder().build()}
        assertThrows(IllegalStateException::class.java) {Destination.Builder().setName("").setAddress("").build()}
        assertTrue(Trip.Builder().build().destinations.isEmpty())
        assertNotNull(Destination.Builder().setAddress(identity.address!!).build())
    }
    @Test fun identityCannotLeakViaRoadOrCueAndRevealRequiresExplicitRequest() {
        assertThrows(IllegalArgumentException::class.java) {PrivacyTripFactory.hidden(turn.copy(road=identity.name),identity)}
        assertThrows(IllegalArgumentException::class.java) {PrivacyTripFactory.hidden(turn.copy(cue=identity.address!!),identity)}
        assertThrows(IllegalArgumentException::class.java) {PrivacyTripFactory.revealed(turn,identity,false,900.0,180)}
        val revealed=roundTrip(PrivacyTripFactory.revealed(turn,identity,true,900.0,180))
        assertEquals(identity.name,revealed.destinations.single().name!!.toString())
        assertEquals(identity.address,revealed.destinations.single().address!!.toString())
        assertEquals(1,revealed.destinationTravelEstimates.size)
        assertTrue(PrivacyTripFactory.hidden(turn,identity).destinations.isEmpty())
    }
    @Test fun invalidTimeDistanceAndManeuverAreRejectedInsteadOfInvented() {
        assertThrows(IllegalArgumentException::class.java) {PrivacyTripFactory.hidden(turn.copy(distanceMeters=Double.NaN),identity)}
        assertThrows(IllegalArgumentException::class.java) {PrivacyTripFactory.hidden(turn.copy(seconds=-1),identity)}
        assertThrows(IllegalArgumentException::class.java) {Maneuver.Builder(-999).build()}
        val time=DateTimeWithZone.create(turn.atEpochMillis,TimeZone.getTimeZone("UTC"))
        assertThrows(IllegalArgumentException::class.java) {TravelEstimate.Builder(Distance.create(1.0,Distance.UNIT_METERS),time).setRemainingTimeSeconds(-2)}
        assertThrows(IllegalArgumentException::class.java) {Trip.Builder().setLoading(true).addStep(Step.Builder("직진").build(),TravelEstimate.Builder(Distance.create(1.0,Distance.UNIT_METERS),time).build()).build()}
    }
}
