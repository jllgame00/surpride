package com.surpride.navhostpoc.voice

import org.junit.Assert.*
import org.junit.Test

class PleosOnDeviceVoiceSpeechTest {
    private class Session:PleosTtsSessionPort {
        var initialized:((VoiceReadiness)->Unit)?=null;var events:PleosTtsEvents?=null
        var stops=0;var closes=0;var failSpeak=false
        val spoken=mutableListOf<String>()
        override fun initialize(onReady:(VoiceReadiness)->Unit,events:PleosTtsEvents) {initialized=onReady;this.events=events}
        override fun speak(text:String) {if(failSpeak) error("test failure");spoken+=text}
        override fun stop() {stops++}
        override fun close() {closes++}
    }
    private class Delay:VoiceDelayPort {
        val delays=mutableListOf<Long>();val tasks=mutableListOf<()->Unit>();var cancels=0
        override fun schedule(delayMillis:Long,action:()->Unit):()->Unit {delays+=delayMillis;tasks+=action;return {cancels++}}
        fun fire() {tasks.last()()}
    }
    private class Events:VoiceUtteranceEvents {
        val started=mutableListOf<String>();val done=mutableListOf<String>();val errors=mutableListOf<String>()
        override fun onStart(id:String) {started+=id}
        override fun onDone(id:String) {done+=id}
        override fun onError(id:String) {errors+=id}
    }
    private class Harness {
        val sessions=mutableListOf<Session>();val delay=Delay();val events=Events();val ready=mutableListOf<VoiceReadiness>()
        val voice=PleosOnDeviceVoiceSpeech({Session().also {sessions+=it}},delay)
        init {voice.start(ready::add,events);sessions.last().initialized!!(VoiceReadiness.READY)}
        val session get()=sessions.last()
    }
    @Test fun onDeviceMetadataAndDelayDoNotClaimAudioLevelSilenceOrImmediatePlayback() {
        val h=Harness();assertEquals(false,h.voice.networkRequired)
        assertEquals(VoiceBackend.PLEOS_ON_DEVICE,h.voice.backend);assertEquals(VoicePreRoll.SCHEDULER_200_MS,h.voice.preRoll)
        assertTrue(h.voice.speak("id-1","controlled"));assertEquals(listOf(15_000L,200L),h.delay.delays)
        assertTrue(h.session.spoken.isEmpty());assertTrue(h.events.started.isEmpty());assertTrue(h.events.done.isEmpty())
        h.delay.fire();assertEquals(listOf("controlled"),h.session.spoken)
        assertTrue(h.events.started.isEmpty());h.session.events!!.onStart();h.session.events!!.onDone()
        assertEquals(listOf("id-1"),h.events.started);assertEquals(listOf("id-1"),h.events.done)
    }
    @Test fun stopCancelsPendingSpeechAndRetiresListenerBeforeNewSdkSession() {
        val h=Harness();h.voice.speak("old","old-controlled");val old=h.session;val oldDelay=h.delay.tasks.last()
        h.voice.stop();oldDelay();assertTrue(old.spoken.isEmpty());assertEquals(1,old.closes)
        h.voice.speak("new","new-controlled");assertNotSame(old,h.session)
        h.session.initialized!!(VoiceReadiness.READY);h.delay.fire()
        old.events!!.onStart();old.events!!.onDone();old.events!!.onError()
        assertTrue(h.events.started.isEmpty());assertTrue(h.events.done.isEmpty());assertTrue(h.events.errors.isEmpty())
        h.session.events!!.onStart();h.session.events!!.onDone();assertEquals(listOf("new"),h.events.done)
    }
    @Test fun nextRequestUsesFreshSessionEvenAfterSuccessfulCompletion() {
        val h=Harness();h.voice.speak("first","controlled");h.delay.fire();val first=h.session
        first.events!!.onStart();first.events!!.onDone();h.voice.speak("second","controlled")
        assertNotSame(first,h.session);first.events!!.onDone();assertEquals(listOf("first"),h.events.done)
        h.session.initialized!!(VoiceReadiness.READY);h.delay.fire();h.session.events!!.onStart();h.session.events!!.onDone()
        assertEquals(listOf("first","second"),h.events.done)
    }
    @Test fun sdkInitializationOrSpeakFailureNeverPretendsCompletion() {
        val h=Harness();h.voice.speak("first","controlled");h.session.failSpeak=true;h.delay.fire()
        assertEquals(listOf("first"),h.events.errors);assertTrue(h.events.done.isEmpty())
        h.voice.stop();h.voice.speak("second","controlled");h.session.initialized!!(VoiceReadiness.ERROR)
        assertEquals(listOf("first","second"),h.events.errors);assertTrue(h.events.done.isEmpty())
    }
    @Test fun closeReleasesSessionAndRejectsLateSdkInitializationAndDelayCallbacks() {
        val h=Harness();h.voice.speak("first","controlled");val old=h.session;val task=h.delay.tasks.last()
        h.voice.close();h.voice.close();task();old.initialized!!(VoiceReadiness.READY);old.events!!.onDone()
        assertEquals(1,old.closes);assertTrue(old.spoken.isEmpty());assertTrue(h.events.done.isEmpty())
        assertFalse(h.voice.speak("late","controlled"))
    }
    @Test fun stopDuringReplacementInitializationRejectsLateReadyAndDoesNotSpeak() {
        val h=Harness();h.voice.speak("first","controlled");h.voice.stop();h.voice.speak("second","controlled");val pending=h.session
        h.voice.stop();pending.initialized!!(VoiceReadiness.READY)
        assertTrue(pending.spoken.isEmpty());assertEquals(1,pending.closes);assertTrue(h.events.started.isEmpty())
    }
    @Test fun missingOemCallbacksTimeOutAsErrorAndLateCompletionCannotBecomeSuccess() {
        val h=Harness();h.voice.speak("silent-oem","controlled");h.delay.fire();val old=h.session
        val watchdog=h.delay.delays.indexOf(15_000L)
        assertTrue("SDK requests need a bounded failure deadline",watchdog>=0)
        h.delay.tasks[watchdog]()
        assertEquals(listOf("silent-oem"),h.events.errors);assertTrue(h.events.done.isEmpty())
        assertEquals(1,old.stops);assertEquals(1,old.closes)
        old.events!!.onStart();old.events!!.onDone();assertTrue(h.events.started.isEmpty());assertTrue(h.events.done.isEmpty())
    }
}
