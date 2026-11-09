package com.surpride.navhostpoc.voice

import org.junit.Assert.*
import org.junit.Test

class NavigationVoiceControllerTest {
    private class Speech:VoiceSpeechPort {
        override var networkRequired:Boolean?=null
        var ready:((VoiceReadiness)->Unit)?=null
        var events:VoiceUtteranceEvents?=null
        val utterances=mutableListOf<Pair<String,String>>()
        var stops=0;var closes=0;var accepted=true;var failStop=false
        override fun start(onReady:(VoiceReadiness)->Unit,events:VoiceUtteranceEvents) {ready=onReady;this.events=events}
        override fun speak(id:String,text:String):Boolean {utterances+=id to text;return accepted}
        override fun stop() {stops++;if(failStop) error("test stop failure")}
        override fun close() {closes++}
    }
    private class Focus:VoiceFocusPort {
        var allowed=true;var requests=0;var abandons=0
        val losses=mutableListOf<()->Unit>()
        override fun request(onLoss:()->Unit):Boolean {requests++;losses+=onLoss;return allowed}
        override fun abandon() {abandons++}
    }
    private class Harness {
        val speech=Speech();val focus=Focus();val voice=NavigationVoiceController(speech,focus)
        init {voice.start {};speech.ready!!(VoiceReadiness.READY)}
        fun activate() {voice.setEnabled(true);voice.setOwnership(true)}
        fun progress(generation:Long=1,index:Int=1,type:Int=2,distance:Double=500.0)=voice.onProgress(generation,index,type,distance)
    }

    @Test fun defaultOffAndMissingOwnershipNeverRequestAudioFocus() {
        val h=Harness();h.progress();assertFalse(h.voice.state.enabled)
        h.voice.setEnabled(true);h.progress()
        assertEquals(0,h.focus.requests);assertTrue(h.speech.utterances.isEmpty())
        h.voice.setOwnership(true);h.progress();assertEquals(1,h.speech.utterances.size)
    }
    @Test fun readinessFailureNeverProducesFallbackSpeech() {
        for(reason in listOf(VoiceReadiness.NO_ENGINE,VoiceReadiness.KOREAN_UNAVAILABLE,VoiceReadiness.ERROR)) {
            val h=Harness();h.speech.ready!!(reason);h.activate();h.progress()
            assertFalse(h.voice.state.ready);assertEquals(0,h.focus.requests);assertEquals(reason.name,h.voice.state.status.name)
        }
    }
    @Test fun readinessReportsNetworkRequirementWithoutClaimingUnknownVoiceIsOffline() {
        for(required in listOf(true,false,null)) {
            val h=Harness();h.speech.networkRequired=required;h.speech.ready!!(VoiceReadiness.READY)
            assertEquals(required,h.voice.state.voiceNetworkRequired)
        }
    }
    @Test fun thresholdsProduceOnlyOneInstructionPerStageDespiteGpsJitter() {
        val h=Harness();h.activate()
        listOf(501.0,500.0,499.0,510.0,500.0,101.0,100.0,99.0,110.0,100.0,31.0,30.0,29.0,40.0,30.0).forEach {h.progress(distance=it)}
        assertEquals(listOf("500미터 앞에서 우회전하세요.","100미터 앞에서 우회전하세요.","잠시 후 우회전하세요."),h.speech.utterances.map {it.second})
    }
    @Test fun jumpingToNearStageSkipsStaleFarInstructions() {
        val h=Harness();h.activate();h.progress(distance=20.0);h.progress(distance=300.0)
        assertEquals(listOf("잠시 후 우회전하세요."),h.speech.utterances.map {it.second})
    }
    @Test fun ferryEntryAndExitNeverClaimBridgeManeuvers() {
        val h=Harness();h.activate();h.progress(type=61,distance=30.0);h.progress(index=2,type=62,distance=30.0)
        assertEquals(listOf("잠시 후 페리 항로로 진입하세요.","잠시 후 페리 항로에서 진출하세요."),h.speech.utterances.map {it.second})
        assertFalse(h.speech.utterances.any {it.second.contains("다리")})
    }
    @Test fun directionsOneTollingCodeDoesNotBecomeAHiPassLaneInstruction() {
        val h=Harness();h.activate();h.progress(type=85,distance=30.0)
        assertEquals("잠시 후 원톨링 구간으로 진입하세요.",h.speech.utterances.single().second)
    }
    @Test fun joinAfterBranchRetainsTheRequiredManeuverOrder() {
        val h=Harness();h.activate();h.progress(type=86,distance=30.0)
        assertEquals("잠시 후 분기 후 합류 구간으로 진행하세요.",h.speech.utterances.single().second)
    }
    @Test fun clockDirectionFamiliesRetainClockValueAndRoadContext() {
        val cases=listOf(
            18 to "1시 방향으로 진행하세요.",19 to "2시 방향으로 진행하세요.",20 to "3시 방향으로 진행하세요.",
            21 to "4시 방향으로 진행하세요.",22 to "5시 방향으로 진행하세요.",23 to "6시 방향으로 진행하세요.",
            24 to "7시 방향으로 진행하세요.",25 to "8시 방향으로 진행하세요.",26 to "9시 방향으로 진행하세요.",
            27 to "10시 방향으로 진행하세요.",28 to "11시 방향으로 진행하세요.",29 to "12시 방향으로 진행하세요.",
            30 to "로터리에서 1시 방향으로 진행하세요.",35 to "로터리에서 6시 방향으로 진행하세요.",41 to "로터리에서 12시 방향으로 진행하세요.",
            70 to "회전 교차로에서 1시 방향으로 진행하세요.",75 to "회전 교차로에서 6시 방향으로 진행하세요.",81 to "회전 교차로에서 12시 방향으로 진행하세요.")
        for((type,phrase) in cases) {
            val h=Harness();h.activate();h.progress(type=type,distance=30.0)
            assertEquals("type=$type","잠시 후 $phrase",h.speech.utterances.single().second)
        }
    }
    @Test fun remainingSupportedDirectionsCodesRetainFiniteMeaningAndSide() {
        val cases=listOf(
            0 to "직진하세요.",1 to "좌회전하세요.",2 to "우회전하세요.",3 to "유턴하세요.",
            5 to "왼쪽 방향으로 진행하세요.",6 to "오른쪽 방향으로 진행하세요.",
            7 to "출구로 진행하세요.",42 to "출구로 진행하세요.",8 to "왼쪽 출구로 진행하세요.",43 to "왼쪽 출구로 진행하세요.",9 to "오른쪽 출구로 진행하세요.",44 to "오른쪽 출구로 진행하세요.",
            10 to "진입로로 진행하세요.",45 to "진입로로 진행하세요.",11 to "왼쪽 진입로로 진행하세요.",46 to "왼쪽 진입로로 진행하세요.",48 to "왼쪽 진입로로 진행하세요.",
            12 to "오른쪽 진입로로 진행하세요.",47 to "오른쪽 진입로로 진행하세요.",49 to "오른쪽 진입로로 진행하세요.",
            14 to "고가 도로로 진입하세요.",15 to "지하 차도로 진입하세요.",16 to "고가 도로 옆길로 진행하세요.",17 to "지하 차도 옆길로 진행하세요.",
            82 to "왼쪽으로 직진하세요.",83 to "오른쪽으로 직진하세요.",84 to "톨게이트로 진입하세요.",300 to "톨게이트로 진행하세요.",301 to "휴게소로 진행하세요.")
        for((type,phrase) in cases) {
            val h=Harness();h.activate();h.progress(type=type,distance=30.0)
            assertEquals("type=$type","잠시 후 $phrase",h.speech.utterances.single().second)
        }
    }
    @Test fun spokenDistanceUsesCurrentConservativeBucketAcrossThresholdBoundaries() {
        val cases=listOf(
            500.01 to null,500.0 to "500미터 앞에서 우회전하세요.",499.99 to "약 490미터 앞에서 우회전하세요.",
            237.0 to "약 230미터 앞에서 우회전하세요.",110.0 to "약 110미터 앞에서 우회전하세요.",
            100.01 to "약 100미터 앞에서 우회전하세요.",100.0 to "100미터 앞에서 우회전하세요.",99.99 to "약 90미터 앞에서 우회전하세요.",
            31.0 to "약 30미터 앞에서 우회전하세요.",30.01 to "약 30미터 앞에서 우회전하세요.",
            30.0 to "잠시 후 우회전하세요.",0.0 to "잠시 후 우회전하세요.")
        for((distance,text) in cases) {
            val h=Harness();h.activate();h.progress(distance=distance)
            assertEquals("distance=$distance",text,h.speech.utterances.singleOrNull()?.second)
        }
    }
    @Test fun changingTenMeterBucketsDoesNotRepeatTheSameApproachStage() {
        val h=Harness();h.activate()
        listOf(237.0,230.0,220.0,110.0,99.0,90.0,80.0,31.0,30.0,29.0).forEach {h.progress(distance=it)}
        assertEquals(listOf("약 230미터 앞에서 우회전하세요.","약 90미터 앞에서 우회전하세요.","잠시 후 우회전하세요."),h.speech.utterances.map {it.second})
    }
    @Test fun newManeuverCanAnnounceSameStageWithoutRepeatingOldManeuver() {
        val h=Harness();h.activate();h.progress();h.progress(index=2,type=1);h.progress(index=1)
        assertEquals(2,h.speech.utterances.size)
        assertEquals("500미터 앞에서 좌회전하세요.",h.speech.utterances.last().second)
    }
    @Test fun observingFarNewManeuverPreventsLateFixFromSpeakingAnOldTurn() {
        val h=Harness();h.activate();h.progress();h.progress(index=2,distance=600.0)
        h.progress(index=1,distance=20.0);h.progress(index=2,distance=100.0)
        assertEquals(listOf("500미터 앞에서 우회전하세요.","100미터 앞에서 우회전하세요."),h.speech.utterances.map {it.second})
    }
    @Test fun routeChangeStopsOldQueueAndRestoresDedupWithoutAcceptingOlderRoute() {
        val h=Harness();h.activate();h.progress();val before=h.speech.stops
        h.progress(generation=2);h.progress(generation=1,distance=20.0)
        assertTrue(h.speech.stops>before);assertEquals(2,h.speech.utterances.size)
    }
    @Test fun routeInvalidationRejectsOldRouteAndClearsOldCallbacks() {
        val h=Harness();h.activate();h.progress();val old=h.speech.utterances.last().first
        h.voice.invalidateRoute();h.progress();h.speech.events!!.onDone(old)
        assertEquals(1,h.speech.utterances.size);assertEquals(0,h.voice.state.completed)
        h.progress(generation=2);assertEquals(2,h.speech.utterances.size)
    }
    @Test fun invalidatingQueuedOrSpeakingRouteReportsReadyWithoutResettingPlaybackCounters() {
        for(speaking in listOf(false,true)) {
            val h=Harness();h.activate();h.progress()
            if(speaking) h.speech.events!!.onStart(h.speech.utterances.last().first)
            val before=h.voice.state
            h.voice.invalidateRoute()
            assertEquals(before.copy(focusGranted=false,status=VoiceStatus.READY),h.voice.state)
        }
    }
    @Test fun invalidatingRouteKeepsDisabledAndNotOwnedGatesVisible() {
        val disabled=Harness();disabled.activate();disabled.progress();disabled.voice.setEnabled(false)
        val disabledBefore=disabled.voice.state;disabled.voice.invalidateRoute()
        assertEquals(disabledBefore.copy(focusGranted=false,status=VoiceStatus.DISABLED),disabled.voice.state)
        val notOwned=Harness();notOwned.activate();notOwned.progress();notOwned.voice.setOwnership(false)
        val notOwnedBefore=notOwned.voice.state;notOwned.voice.invalidateRoute()
        assertEquals(notOwnedBefore.copy(focusGranted=false,status=VoiceStatus.NOT_OWNED),notOwned.voice.state)
    }
    @Test fun disabledUpdatesDoNotConsumeFutureInstruction() {
        val h=Harness();h.voice.setOwnership(true);h.progress();h.voice.setEnabled(true);h.progress()
        assertEquals(1,h.speech.utterances.size)
        h.voice.setEnabled(false);assertFalse(h.voice.state.focusGranted)
    }
    @Test fun ownershipLossImmediatelyStopsAndAbandonsAndLateDoneIsIgnored() {
        val h=Harness();h.activate();h.progress();val old=h.speech.utterances.last().first;val before=h.speech.stops
        h.voice.setOwnership(false);h.speech.events!!.onDone(old);h.progress(distance=100.0)
        assertTrue(h.speech.stops>before);assertTrue(h.focus.abandons>0)
        assertEquals(0,h.voice.state.completed);assertEquals(1,h.speech.utterances.size)
    }
    @Test fun focusDeniedNeverSubmitsAndNeverRetriesSameThresholdEveryFix() {
        val h=Harness();h.activate();h.focus.allowed=false
        repeat(5) {h.progress()}
        assertTrue(h.speech.utterances.isEmpty());assertEquals(1,h.focus.requests)
        assertEquals(VoiceStatus.FOCUS_DENIED,h.voice.state.status);assertFalse(h.voice.state.enabled)
    }
    @Test fun asynchronousFocusLossStopsAndDisablesWithoutAutoResumeOnGpsFix() {
        val h=Harness();h.activate();h.progress();val before=h.speech.stops
        h.focus.losses.last()();h.progress(distance=100.0)
        assertTrue(h.speech.stops>before);assertFalse(h.voice.state.enabled);assertFalse(h.voice.state.focusGranted)
        assertEquals(VoiceStatus.FOCUS_LOST,h.voice.state.status);assertEquals(1,h.speech.utterances.size)
    }
    @Test fun staleFocusLossCannotStopNewUtteranceLease() {
        val h=Harness();h.activate();h.progress();val stale=h.focus.losses.last()
        h.speech.events!!.onDone(h.speech.utterances.last().first);h.progress(distance=100.0);stale()
        assertTrue(h.voice.state.enabled);assertTrue(h.voice.state.focusGranted)
    }
    @Test fun submissionSuccessDoesNotPretendUtteranceStartedOrCompleted() {
        val h=Harness();h.activate();h.progress();val id=h.speech.utterances.last().first
        assertEquals(1,h.voice.state.requested);assertEquals(0,h.voice.state.started);assertEquals(0,h.voice.state.completed)
        h.speech.events!!.onStart(id);assertEquals(1,h.voice.state.started)
        h.speech.events!!.onDone(id);h.speech.events!!.onDone(id)
        assertEquals(1,h.voice.state.completed);assertFalse(h.voice.state.focusGranted)
    }
    @Test fun repeatedOwnershipRefreshPreservesActualPlaybackAndFailureStatus() {
        val h=Harness();h.activate();h.progress();h.voice.setOwnership(true)
        assertEquals(VoiceStatus.QUEUED,h.voice.state.status)
        h.speech.events!!.onStart(h.speech.utterances.last().first);h.voice.setOwnership(true)
        assertEquals(VoiceStatus.SPEAKING,h.voice.state.status)
        h.focus.losses.last()();h.voice.setOwnership(true)
        assertEquals(VoiceStatus.FOCUS_LOST,h.voice.state.status)
    }
    @Test fun failedEngineStopStillAbandonsFocusAndPreventsAnyReplacementSpeech() {
        val h=Harness();h.activate();h.progress();val before=h.focus.abandons
        h.speech.failStop=true;h.progress(distance=100.0)
        assertTrue(h.focus.abandons>before);assertEquals(1,h.speech.utterances.size)
        assertFalse(h.voice.state.enabled);assertEquals(VoiceStatus.ERROR,h.voice.state.status)
    }
    @Test fun replacedUtteranceCannotFinishOrErrorNewUtterance() {
        val h=Harness();h.activate();h.progress();val stale=h.speech.utterances.last().first
        h.progress(distance=100.0);h.speech.events!!.onDone(stale);h.speech.events!!.onError(stale)
        assertEquals(0,h.voice.state.completed);assertEquals(0,h.voice.state.errors);assertTrue(h.voice.state.focusGranted)
    }
    @Test fun speechRejectionAndAsyncErrorReleaseFocusWithoutFalseCompletion() {
        val h=Harness();h.activate();h.speech.accepted=false;h.progress()
        assertEquals(1,h.voice.state.errors);assertFalse(h.voice.state.focusGranted);assertEquals(0,h.voice.state.completed)
        val a=Harness();a.activate();a.progress();a.speech.events!!.onError(a.speech.utterances.last().first)
        assertEquals(1,a.voice.state.errors);assertFalse(a.voice.state.focusGranted)
    }
    @Test fun asynchronousUtteranceErrorDisablesVoiceUntilExplicitUserReenable() {
        val h=Harness();h.activate();h.progress()
        h.speech.events!!.onError(h.speech.utterances.last().first)
        assertFalse(h.voice.state.enabled);assertEquals(VoiceStatus.ERROR,h.voice.state.status)
        repeat(3) {h.progress(distance=100.0)}
        assertEquals(1,h.focus.requests);assertEquals(1,h.speech.utterances.size)
        assertEquals(1,h.voice.state.errors);assertEquals(0,h.voice.state.completed)
        h.voice.setEnabled(true);h.progress(distance=100.0)
        assertEquals(2,h.focus.requests);assertEquals(2,h.speech.utterances.size)
    }
    @Test fun rejectedSpeechDisablesVoiceAndDoesNotRetryOnProgressUntilExplicitReenable() {
        val h=Harness();h.activate();h.speech.accepted=false;h.progress()
        assertFalse(h.voice.state.enabled);assertEquals(VoiceStatus.ERROR,h.voice.state.status)
        h.speech.accepted=true;repeat(3) {h.progress(distance=100.0)}
        assertEquals(1,h.focus.requests);assertEquals(1,h.speech.utterances.size)
        assertEquals(0,h.voice.state.requested);assertEquals(1,h.voice.state.errors)
        h.voice.setEnabled(true);h.progress(distance=100.0)
        assertEquals(2,h.focus.requests);assertEquals(1,h.voice.state.requested)
    }
    @Test fun invalidNumbersUnknownCodesAndArrivalNeverSpeakRawInputOrArrivalClaim() {
        val h=Harness();h.activate()
        listOf(Double.NaN,Double.POSITIVE_INFINITY,-1.0).forEach {h.progress(distance=it)}
        listOf(-100,999,100,101,1000).forEachIndexed {i,type->h.progress(index=i+1,type=type)}
        assertEquals(0,h.focus.requests);assertTrue(h.speech.utterances.isEmpty())
    }
    @Test fun controlledStatusCuesDeduplicateUntilRouteChanges() {
        val h=Harness();h.activate();h.progress(distance=600.0)
        repeat(4) {h.voice.announceRerouting();h.voice.announceGpsLost();h.voice.announceNearDestination()}
        assertEquals(listOf("경로를 다시 확인하고 있습니다.","위치 신호를 확인하고 있습니다.","목적지 근처입니다. 주변을 확인하세요."),h.speech.utterances.map {it.second})
        assertFalse(h.speech.utterances.any {it.second.contains("도착")})
    }
    @Test fun stopAndCloseRejectLateReadinessAndUtteranceCallbacks() {
        val h=Harness();h.activate();h.progress();val id=h.speech.utterances.last().first
        h.voice.stop();h.speech.events!!.onStart(id);h.speech.events!!.onDone(id);h.progress(distance=100.0)
        assertEquals(0,h.voice.state.started);assertEquals(0,h.voice.state.completed);assertFalse(h.voice.state.enabled)
        h.voice.close();h.voice.close();h.speech.ready!!(VoiceReadiness.READY);h.voice.setEnabled(true);h.progress(generation=2)
        assertEquals(VoiceStatus.CLOSED,h.voice.state.status);assertEquals(1,h.speech.closes);assertEquals(1,h.speech.utterances.size)
    }
}
