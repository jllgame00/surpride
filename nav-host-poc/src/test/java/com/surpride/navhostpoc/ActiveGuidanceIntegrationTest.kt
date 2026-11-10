package com.surpride.navhostpoc

import android.os.Build
import android.os.SystemClock
import androidx.car.app.navigation.model.NavigationTemplate
import androidx.car.app.navigation.model.RoutingInfo
import androidx.car.app.testing.TestCarContext
import androidx.car.app.testing.navigation.TestNavigationManager
import com.surpride.navhostpoc.guidance.*
import com.surpride.navhostpoc.ownership.*
import com.surpride.navhostpoc.routing.*
import com.surpride.navhostpoc.tracking.*
import com.surpride.navhostpoc.voice.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSystemClock
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter
import java.io.File
import java.time.Duration
import java.util.concurrent.Executor

/** Real Screen GPS/route/Trip/voice wiring. Native UX, focus, speech and HTTP are controlled boundaries.
 * Observed parked state is supplied here; this does not test Car/Binder lifecycle or Host rendering. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34])
class ActiveGuidanceIntegrationTest {
    private fun point(x:Double,y:Double=0.0)=GuidanceAccuracyFixture.point(x,y)
    private fun payload(now:Long)=GuidanceAccuracyFixture.payload(now).toString()
    private class Focus:FocusPort {
        var lose:(()->Unit)?=null
        override fun request(id:Long,granted:()->Unit,lost:()->Unit):Boolean {lose=lost;granted();return true}
        override fun abandon(id:Long)=Unit
    }
    private class Speech:VoiceSpeechPort {
        val texts=mutableListOf<String>()
        override fun start(onReady:(VoiceReadiness)->Unit,events:VoiceUtteranceEvents)=onReady(VoiceReadiness.READY)
        override fun speak(id:String,text:String):Boolean {texts+=text;return true}
        override fun stop()=Unit
        override fun close()=Unit
    }
    private fun <T> field(target:Any,name:String):T=ReflectionHelpers.getField(target,name)
    private fun call(target:Any,name:String,vararg args:ClassParameter<*>)=ReflectionHelpers.callInstanceMethod<Any?>(target,name,*args)
    private inner class Harness:AutoCloseable {
        val now=System.currentTimeMillis()
        val context=TestCarContext.createCarContext(RuntimeEnvironment.getApplication())
        val manager=context.getCarService(TestNavigationManager::class.java)
        val positions=FakeLocationProvider();val speech=Speech();val focus=Focus()
        val screen:ActiveMysteryScreen
        val adapter:NavigationOwnershipAdapter
        init {
            ReflectionHelpers.setStaticField(Build::class.java,"MODEL","Connect on x86_64 emulator")
            ReflectionHelpers.setStaticField(Build::class.java,"PRODUCT","crp_avd")
            File(context.cacheDir,"route-guidance.json").writeText(payload(now))
            screen=ActiveMysteryScreen(context,ActiveRoutePacket.load(context))
            val original=field<Any>(screen,"adapter")
            val stop=field<()->Unit>(field<Any>(original,"navigation"),"stopGuidance")
            adapter=AndroidXNavigationOwnershipAdapter({manager},focus,stop,HostIntegrationGate(true,true,true))
            ReflectionHelpers.setField(screen,"adapter",adapter)
            ReflectionHelpers.setField(field<Any>(screen,"positions"),"provider",positions)
            val voice=field<AndroidNavigationVoice>(screen,"voice")
            ReflectionHelpers.setField(voice,"controller",NavigationVoiceController(speech,object:VoiceFocusPort {
                override fun request(onLoss:()->Unit)=true
                override fun abandon()=Unit
            }))
            voice.start {};voice.setEnabled(true)
            ReflectionHelpers.setField(screen,"voiceOn",true)
            ReflectionHelpers.setField(screen,"foreground",true)
            ReflectionHelpers.setField(screen,"requiresDo",false)
            call(screen,"startPosition");call(screen,"configureReroute")
            assertTrue(adapter.requestActivation(ActivationChecks(true,true,false,true,true),ControlledTestApproval.fromExplicitUserAction()))
        }
        fun fix(x:Double=50.0,y:Double=0.0,time:Long=SystemClock.elapsedRealtime())=
            PositionEvent.Fix(PositionFix(point(x,y),1.0,time,now,0.0,null,null,PositionSource.FAKE,false))
        fun emit(x:Double=50.0,y:Double=0.0) {ShadowSystemClock.advanceBy(Duration.ofSeconds(1));positions.emit(fix(x,y))}
        fun replace():Boolean {
            val body=payload(now).replace("\"A\"","\"New A\"")
            val reply=RouteRefreshResult.Success(body,GuidanceParser.parse(body,now),now,now+3_600_000)
            return call(screen,"installRoute",ClassParameter.from(RouteRefreshResult.Success::class.java,reply),
                ClassParameter.from(Boolean::class.javaPrimitiveType!!,false)) as Boolean
        }
        fun refreshWith(body:String,status:Int=200) {
            val old=field<Any>(screen,"reroute")
            val ready=field<(RouteRefreshResult.Success)->Unit>(old,"onRouteReady")
            val changed=field<(RerouteState)->Unit>(old,"onStateChanged")
            val direct=Executor {it.run()}
            val client=ProxyRouteRefreshClient(ProxyRouteSettings("http://127.0.0.1:8765","x".repeat(32),now+60_000),
                {now},direct,direct,RouteHttpTransport {_,_,_,_->RouteHttpResponse(status,body)})
            val coordinator=RerouteCoordinator(client,point(100.0,-900.0),"Private",SystemClock::elapsedRealtime,ready,changed)
            ReflectionHelpers.setField(screen,"reroute",coordinator)
        }
        override fun close() {adapter.release();field<AndroidNavigationVoice>(screen,"voice").close();call(screen,"stopPosition")}
    }
    @Test fun gpsUpdatesTheRealScreenTripRoadTurnDistanceAndVoiceTogether() {
        Harness().use {h->
            h.emit()
            val trip=h.manager.tripsSent.last()
            assertTrue(trip.destinations.isEmpty())
            assertEquals("A",trip.currentRoad!!.toString())
            assertEquals(250.0,trip.stepTravelEstimates.single().remainingDistance!!.displayDistance,0.1)
            assertEquals(listOf("약 250미터 앞에서 우회전하세요."),h.speech.texts)
            assertTrue((h.screen.onGetTemplate() as NavigationTemplate).navigationInfo is RoutingInfo)
        }
    }
    @Test fun olderGpsAfterRouteReplacementCannotInvalidateOrRepublishTheNewRoute() {
        Harness().use {h->
            h.emit();val old=h.fix(time=SystemClock.elapsedRealtime()-500)
            assertTrue(h.replace())
            val count=h.manager.tripsSent.size
            h.positions.emit(old)
            assertEquals(MatchStatus.ON_ROUTE,field<RouteMatcher>(h.screen,"matcher").state().status)
            assertEquals(count,h.manager.tripsSent.size)
        }
    }
    @Test fun stopRejectsAlreadyQueuedGpsAndLaterVoiceRequests() {
        Harness().use {h->
            h.emit();val queued=h.positions.captureCallback()!!
            h.adapter.stop();val trips=h.manager.tripsSent.size;val speech=h.speech.texts.size
            queued(h.fix(90.0))
            assertEquals(0,h.positions.subscriberCount)
            assertEquals(trips,h.manager.tripsSent.size);assertEquals(speech,h.speech.texts.size)
            assertFalse(field<AndroidNavigationVoice>(h.screen,"voice").state.owned)
        }
    }
    @Test fun invalidFutureGpsCannotPreventRecoveryOnTheNextNormalScreenFix() {
        for(time in listOf(SystemClock.elapsedRealtime()+60_000,Long.MAX_VALUE)) Harness().use {h->
            h.emit()
            h.positions.emit(h.fix(time=time))
            assertEquals(MatchStatus.UNKNOWN,field<RouteMatcher>(h.screen,"matcher").state().status)
            h.emit()
            assertEquals(MatchStatus.ON_ROUTE,field<RouteMatcher>(h.screen,"matcher").state().status)
            assertEquals("A",h.manager.tripsSent.last().currentRoad!!.toString())
        }
    }
    @Test fun ownershipLossStopsTheScreenPositionAndVoicePipeline() {
        Harness().use {h->
            h.emit();h.focus.lose!!()
            val count=h.speech.texts.size;h.positions.emit(h.fix(90.0))
            assertEquals(OwnershipState.STOPPED,h.adapter.state)
            assertEquals(0,h.positions.subscriberCount);assertEquals(count,h.speech.texts.size)
            assertEquals(1,h.manager.navigationEndedCount)
        }
    }
    @Test fun ownershipLossAndNonceReplacementCannotResumeOldCallbacksOnRecreation() {
        Harness().use {h->
            h.emit();val queued=h.positions.captureCallback()!!;h.focus.lose!!()
            val newBody=JSONObject(payload(h.now)).put("session_nonce","00000000-0000-4000-8000-000000000002").toString()
            File(h.context.cacheDir,"route-guidance.json").writeText(newBody)
            val restored=ActiveMysteryScreen(h.context,ActiveRoutePacket.load(h.context))
            val starts=h.manager.navigationStartedCount;val trips=h.manager.tripsSent.size;val spoken=h.speech.texts.size
            queued(h.fix(90.0))
            assertEquals(starts,h.manager.navigationStartedCount);assertEquals(trips,h.manager.tripsSent.size)
            assertEquals(spoken,h.speech.texts.size);assertEquals(0,h.positions.subscriberCount)
            assertFalse(field<Boolean>(restored,"pendingStart"));assertFalse(field<Boolean>(restored,"subscribed"))
            assertNull(field<PositionFix?>(restored,"fix"));assertFalse(field<AndroidNavigationVoice>(restored,"voice").state.owned)
        }
    }
    @Test fun malformedRerouteGeometryEndsGuidanceWithoutPublishingAReplacementTurn() {
        Harness().use {h->
            h.emit()
            val bad=JSONObject(payload(h.now))
            bad.getJSONObject("guidance").getJSONArray("segments").getJSONObject(1)
                .getJSONArray("geometry").getJSONArray(0).put(0,point(500.0).longitude)
            h.refreshWith(bad.toString())
            repeat(3) {h.emit(50.0,200.0)}
            assertEquals(OwnershipState.STOPPED,h.adapter.state)
            assertTrue(h.manager.tripsSent.last().isLoading)
            assertEquals(0,h.positions.subscriberCount)
        }
    }
    @Test fun failedRerouteSafelyEndsTheActualScreenPipeline() {
        Harness().use {h->
            h.emit();h.refreshWith("{\"error\":\"NO_ROUTE\"}",422)
            repeat(3) {h.emit(50.0,200.0)}
            assertEquals(OwnershipState.STOPPED,h.adapter.state)
            assertEquals(1,h.manager.navigationEndedCount)
            assertEquals(0,h.positions.subscriberCount)
            assertFalse(field<AndroidNavigationVoice>(h.screen,"voice").state.owned)
        }
    }
}
