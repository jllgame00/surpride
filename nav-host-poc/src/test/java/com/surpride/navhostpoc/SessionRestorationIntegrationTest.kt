package com.surpride.navhostpoc

import android.os.Build
import android.os.SystemClock
import androidx.car.app.testing.TestCarContext
import androidx.car.app.testing.ScreenController
import androidx.car.app.ScreenManager
import androidx.car.app.OnDoneCallback
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.lifecycle.Lifecycle
import androidx.car.app.testing.navigation.TestNavigationManager
import com.surpride.navhostpoc.guidance.*
import com.surpride.navhostpoc.tracking.*
import com.surpride.navhostpoc.voice.AndroidNavigationVoice
import org.junit.Assert.*
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSystemClock
import org.robolectric.util.ReflectionHelpers
import java.io.File
import java.time.Duration

/** Real Screen reconstruction from private disk; no stored GPS, ownership or speech authorization. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],shadows=[FaultAtomicFileShadow::class])
class SessionRestorationIntegrationTest {
    private val context=TestCarContext.createCarContext(RuntimeEnvironment.getApplication())
    private val file=File(context.cacheDir,"route-guidance.json")
    private val manager=context.getCarService(TestNavigationManager::class.java)
    private val now=System.currentTimeMillis()
    private fun <T> field(screen:Any,key:String):T=ReflectionHelpers.getField(screen,key)
    private fun call(screen:Any,key:String)=ReflectionHelpers.callInstanceMethod<Any?>(screen,key)
    private fun seed(returning:Boolean=false) {
        ReflectionHelpers.setStaticField(Build::class.java,"MODEL","Connect on x86_64 emulator")
        ReflectionHelpers.setStaticField(Build::class.java,"PRODUCT","crp_avd")
        file.writeText(GuidanceAccuracyFixture.payload(now)
            .put("session_nonce","00000000-0000-4000-8000-000000000001").put("candidate_id","synthetic")
            .put("prepared_at_epoch_ms",now).put("expires_at_epoch_ms",now+3_600_000)
            .put("returning",returning).toString())
    }
    private fun screen()=ActiveMysteryScreen(context,ActiveRoutePacket.load(context)).also {
        ReflectionHelpers.setField(it,"foreground",true);ReflectionHelpers.setField(it,"requiresDo",false)
    }
    private fun assertInactive(screen:ActiveMysteryScreen) {
        assertFalse(field<Boolean>(screen,"pendingStart"));assertNull(field<PositionFix?>(screen,"fix"))
        assertFalse(field<Boolean>(screen,"subscribed"));assertFalse(field<ArrivalGate>(screen,"arrival").candidate)
        assertFalse(field<AndroidNavigationVoice>(screen,"voice").state.owned)
        assertEquals(0,field<AndroidNavigationVoice>(screen,"voice").state.requested)
        assertEquals(0,manager.navigationStartedCount);assertTrue(manager.tripsSent.isEmpty())
    }
    private fun confirm(screen:ActiveMysteryScreen) {
        val matcher=field<RouteMatcher>(screen,"matcher");val arrival=field<ArrivalGate>(screen,"arrival")
        repeat(3) {
            ShadowSystemClock.advanceBy(Duration.ofSeconds(1))
            val sample=PositionFix(GuidanceAccuracyFixture.point(100.0,-895.0),1.0,SystemClock.elapsedRealtime(),now,0.0,null,null,PositionSource.FAKE,false)
            val state=matcher.accept(sample,sample.elapsedMillis)
            arrival.observe(sample,state,5.0,true,sample.elapsedMillis)
        }
        assertTrue(arrival.candidate);call(screen,"confirmArrival")
    }
    @After fun clearFault() {FaultAtomicFileShadow.fault="NONE"}
    @Test fun explicitDisclosureSurvivesRealScreenReconstructionWithoutAutoGuidance() {
        seed();val first=screen();call(first,"reveal")
        val restored=screen();assertTrue(field<Boolean>(restored,"revealed"));assertInactive(restored)
    }
    @Test fun confirmedArrivalSurvivesReconstructionWithoutRestoringACandidate() {
        seed();val first=screen();confirm(first)
        val restored=screen();assertTrue(field<Boolean>(restored,"revealed"))
        assertNotNull(field<Long?>(restored,"arrivedAt"));assertInactive(restored)
    }
    @Test fun completedReturnSurvivesReconstructionWithoutAnotherReturnStart() {
        seed(true);val first=screen();confirm(first)
        val restored=screen();assertTrue(field<Boolean>(restored,"returnCompleted"));assertInactive(restored)
    }
    @Test fun failedDisclosureSaveDoesNotPublishOrRestoreDisclosure() {
        seed();val first=screen();FaultAtomicFileShadow.fault="PARTIAL";call(first,"reveal")
        assertFalse(field<Boolean>(first,"revealed"))
        FaultAtomicFileShadow.fault="NONE";val restored=screen()
        assertFalse(field<Boolean>(restored,"revealed"));assertInactive(restored)
    }
    @Test fun hiddenAndCandidateOnlySessionsRemainHiddenAfterReconstruction() {
        seed();val first=screen()
        val matcher=field<RouteMatcher>(first,"matcher");val gate=field<ArrivalGate>(first,"arrival")
        repeat(3) {
            ShadowSystemClock.advanceBy(Duration.ofSeconds(1))
            val fix=PositionFix(GuidanceAccuracyFixture.point(100.0,-895.0),1.0,SystemClock.elapsedRealtime(),now,0.0,null,null,PositionSource.FAKE,false)
            gate.observe(fix,matcher.accept(fix,fix.elapsedMillis),5.0,true,fix.elapsedMillis)
        }
        assertTrue(gate.candidate)
        val restored=screen();assertFalse(field<Boolean>(restored,"revealed"));assertNull(field<Long?>(restored,"arrivedAt"));assertInactive(restored)
    }
    @Test fun actualScreenLifecycleStopAndRecreateNeverStartsNavigationOrSpeech() {
        seed();val first=screen()
        val controller=ScreenController(first).moveToState(Lifecycle.State.STARTED)
        ReflectionHelpers.setField(first,"requiresDo",false)
        call(first,"reveal");confirm(first)
        controller.moveToState(Lifecycle.State.CREATED).moveToState(Lifecycle.State.DESTROYED)
        val restored=screen();val next=ScreenController(restored).moveToState(Lifecycle.State.STARTED)
        assertTrue(field<Boolean>(restored,"revealed"));assertNotNull(field<Long?>(restored,"arrivedAt"))
        assertInactive(restored)
        next.moveToState(Lifecycle.State.CREATED).moveToState(Lifecycle.State.DESTROYED)
        assertInactive(restored)
    }
    @Test fun failedArrivalSaveCannotTurnIntoConfirmedArrivalAfterRecreation() {
        seed();val first=screen();FaultAtomicFileShadow.fault="FINISH";confirm(first)
        FaultAtomicFileShadow.fault="NONE"
        assertNull(field<Long?>(first,"arrivedAt"));assertFalse(field<Boolean>(first,"revealed"))
        val restored=screen();assertNull(field<Long?>(restored,"arrivedAt"));assertInactive(restored)
    }
    @Test fun returnPreparationIntentSurvivesButRequestAndGpsAuthorityDoNot() {
        seed();val first=screen();confirm(first);call(first,"prepareReturn")
        val restored=screen()
        assertEquals(ReturnPhase.REQUESTED,field<ActiveRoutePacket>(restored,"packet").outcome.returnPhase)
        assertNull(field<String?>(restored,"pendingPreparation"));assertInactive(restored)
    }
    @Test fun oldScreenCannotDiscloseOrSendResultsAfterNonceChanges() {
        seed();val stale=screen()
        val next=org.json.JSONObject(file.readText()).put("session_nonce","00000000-0000-4000-8000-000000000002")
        file.writeText(next.toString());val bytes=file.readBytes()
        call(stale,"reveal");call(stale,"returnResult")
        assertFalse(field<Boolean>(stale,"revealed"));assertArrayEquals(bytes,file.readBytes())
        assertNull(org.robolectric.Shadows.shadowOf(RuntimeEnvironment.getApplication()).nextStartedActivity)
        assertInactive(screen())
    }
    @Test fun invalidRestoredPacketCannotProduceTbtAndShowsReprepareStatus() {
        seed();file.writeText(org.json.JSONObject(file.readText()).put("terminal_state",org.json.JSONObject()).toString())
        val restored=screen();assertNull(field<Any?>(restored,"route"));assertInactive(restored)
        assertTrue(field<String>(restored,"result").startsWith("SESSION_RESTORE_UNAVAILABLE"))
        assertEquals("저장된 경로를 복원할 수 없어요 · SURPRIDE에서 다시 준비해 주세요",
            NavigationFooter.present(NavigationFooterInput(result=field(restored,"result"))).first())
        assertEquals("경로 정보를 저장하지 못했어요 · 다시 준비해 주세요",
            NavigationFooter.present(NavigationFooterInput(arrived=true,revealed=true,result="SESSION_CACHE_UNAVAILABLE")).first())
    }
    @Test fun mockPacketCannotBeRelabeledAsAnApiReviewCallback() {
        seed();file.writeText(org.json.JSONObject(file.readText()).put("source","MOCK").toString())
        val fixture=screen();call(fixture,"reveal");call(fixture,"returnResult")
        assertNull(org.robolectric.Shadows.shadowOf(RuntimeEnvironment.getApplication()).nextStartedActivity)
    }
    @Test fun repeatedLifecyclePreparationDoesNotDuplicateGpsAndStopRejectsQueuedFix() {
        seed();val first=screen();val provider=FakeLocationProvider()
        val session=field<PositionSession>(first,"positions")
        ReflectionHelpers.setField(session,"provider",provider)
        val lifecycle=ScreenController(first).moveToState(Lifecycle.State.STARTED)
        ReflectionHelpers.setField(first,"requiresDo",false)
        call(first,"startPosition");call(first,"startPosition")
        assertEquals(1,provider.subscriberCount)
        val queued=provider.captureCallback()!!
        lifecycle.moveToState(Lifecycle.State.CREATED)
        queued(PositionEvent.Fix(PositionFix(GuidanceAccuracyFixture.point(50.0),1.0,SystemClock.elapsedRealtime(),now,0.0,null,null,PositionSource.FAKE,false)))
        assertEquals(0,provider.subscriberCount)
        assertNull(field<PositionFix?>(first,"fix"))
        assertEquals(0,manager.navigationStartedCount)
        lifecycle.moveToState(Lifecycle.State.DESTROYED)
        assertInactive(screen())
    }
    private fun stacked():Pair<ActiveMysteryScreen,ScreenController> {
        context.lifecycleOwner.registry.currentState=Lifecycle.State.RESUMED
        val first=screen();val controller=ScreenController(first).moveToState(Lifecycle.State.RESUMED)
        ReflectionHelpers.setField(first,"requiresDo",false)
        return first to controller
    }
    private fun pick(parent:ActiveMysteryScreen,label:String) {
        call(parent,"controls")
        val child=context.getCarService(ScreenManager::class.java).top
        ReflectionHelpers.setField(child,"allowed",true) // Fresh parked observation at the child boundary.
        val row=(child.onGetTemplate() as ListTemplate).singleList!!.items.filterIsInstance<Row>().single {it.title.toString()==label}
        row.onClickDelegate!!.sendClick(object:OnDoneCallback {})
        assertSame(parent,context.getCarService(ScreenManager::class.java).top)
        assertNull(field<Boolean?>(parent,"requiresDo")) // Parent UX subscription is not yet confirmed.
    }
    private fun parkedTick(screen:ActiveMysteryScreen) {
        ReflectionHelpers.setField(screen,"requiresDo",false)
        field<Runnable>(screen,"tick").run()
    }
    @Test fun childDisclosureWaitsForFreshParentUxThenCommitsExactlyOnce() {
        seed();val (parent,controller)=stacked()
        pick(parent,"목적지 즉시 공개");assertFalse(field<Boolean>(parent,"revealed"))
        parkedTick(parent)
        assertTrue(field<Boolean>(parent,"revealed"));assertTrue(ActiveRoutePacket.load(context)!!.outcome.revealed)
        val committed=file.readBytes();parkedTick(parent);assertArrayEquals(committed,file.readBytes())
        assertEquals(0,manager.navigationStartedCount)
        controller.moveToState(Lifecycle.State.DESTROYED)
    }
    @Test fun childReturnSelectionSurvivesPopButDoesNotStartNavigation() {
        seed();val (parent,controller)=stacked();confirm(parent)
        pick(parent,"출발지 복귀 경로 준비 · 자동 시작 없음")
        assertEquals(ReturnPhase.NONE,ActiveRoutePacket.load(context)!!.outcome.returnPhase)
        parkedTick(parent)
        assertEquals(ReturnPhase.REQUESTED,ActiveRoutePacket.load(context)!!.outcome.returnPhase)
        assertEquals(0,manager.navigationStartedCount)
        controller.moveToState(Lifecycle.State.DESTROYED)
    }
    @Test fun expiredControlSelectionCannotDiscloseAfterDelayedUx() {
        seed();val (parent,controller)=stacked();pick(parent,"목적지 즉시 공개")
        ShadowSystemClock.advanceBy(Duration.ofSeconds(16));parkedTick(parent)
        assertFalse(field<Boolean>(parent,"revealed"));assertFalse(ActiveRoutePacket.load(context)!!.outcome.revealed)
        controller.moveToState(Lifecycle.State.DESTROYED)
    }
    @Test fun oldControlSelectionCannotCrossNonceOrLifecycleStop() {
        seed();val (parent,controller)=stacked();pick(parent,"목적지 즉시 공개")
        val changed=org.json.JSONObject(file.readText()).put("session_nonce","00000000-0000-4000-8000-000000000002")
        file.writeText(changed.toString());parkedTick(parent)
        assertFalse(field<Boolean>(parent,"revealed"));assertFalse(ActiveRoutePacket.load(context)!!.outcome.revealed)
        controller.moveToState(Lifecycle.State.DESTROYED)
        assertInactive(screen())
    }
    @Test fun lifecycleStopDiscardsPendingControlEvenWhenThePacketDoesNotChange() {
        seed();val (parent,controller)=stacked();pick(parent,"목적지 즉시 공개")
        controller.moveToState(Lifecycle.State.CREATED).moveToState(Lifecycle.State.RESUMED)
        parkedTick(parent)
        assertFalse(field<Boolean>(parent,"revealed"));assertFalse(ActiveRoutePacket.load(context)!!.outcome.revealed)
        controller.moveToState(Lifecycle.State.DESTROYED)
    }
}
