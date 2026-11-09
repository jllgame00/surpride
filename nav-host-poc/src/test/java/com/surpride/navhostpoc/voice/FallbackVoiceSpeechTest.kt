package com.surpride.navhostpoc.voice

import org.junit.Assert.*
import org.junit.Test

class FallbackVoiceSpeechTest {
    private class Speech:VoiceSpeechPort {
        var ready:((VoiceReadiness)->Unit)?=null;var events:VoiceUtteranceEvents?=null
        var starts=0;var stops=0;var closes=0;var speaks=0
        override fun start(onReady:(VoiceReadiness)->Unit,events:VoiceUtteranceEvents) {starts++;ready=onReady;this.events=events}
        override fun speak(id:String,text:String):Boolean {speaks++;return true}
        override fun stop() {stops++}
        override fun close() {closes++}
    }
    private class Events:VoiceUtteranceEvents {
        val started=mutableListOf<String>();val done=mutableListOf<String>();val errors=mutableListOf<String>()
        override fun onStart(id:String) {started+=id}
        override fun onDone(id:String) {done+=id}
        override fun onError(id:String) {errors+=id}
    }
    @Test fun onlyMissingAndroidEngineSelectsFallbackAndClosesPrimaryBeforeInitialization() {
        val android=Speech();val pleos=Speech();var creates=0;val ready=mutableListOf<VoiceReadiness>()
        val voice=FallbackVoiceSpeech(android) {assertEquals(1,android.closes);creates++;pleos}
        voice.start(ready::add,Events());android.ready!!(VoiceReadiness.NO_ENGINE);android.ready!!(VoiceReadiness.NO_ENGINE)
        assertEquals(1,creates);assertEquals(1,pleos.starts);assertTrue(ready.isEmpty())
        pleos.ready!!(VoiceReadiness.READY);assertEquals(listOf(VoiceReadiness.READY),ready)
        assertTrue(voice.speak("safe-id","controlled"));assertEquals(1,pleos.speaks);assertEquals(0,android.speaks)
    }
    @Test fun availableAndroidAndNonMissingEngineErrorsNeverConstructPleos() {
        for(status in listOf(VoiceReadiness.READY,VoiceReadiness.KOREAN_UNAVAILABLE,VoiceReadiness.ERROR)) {
            val android=Speech();var creates=0;var result:VoiceReadiness?=null
            val voice=FallbackVoiceSpeech(android) {creates++;Speech()}
            voice.start({result=it},Events());android.ready!!(status)
            assertEquals(0,creates);assertEquals(status,result)
        }
    }
    @Test fun fallbackFailureReportsErrorWithoutRetryingOrInventingSpeechCallbacks() {
        val android=Speech();val pleos=Speech();val events=Events();var result:VoiceReadiness?=null
        val voice=FallbackVoiceSpeech(android) {pleos};voice.start({result=it},events)
        android.ready!!(VoiceReadiness.NO_ENGINE);pleos.ready!!(VoiceReadiness.ERROR)
        assertEquals(VoiceReadiness.ERROR,result);assertTrue(events.started.isEmpty());assertTrue(events.done.isEmpty())
        voice.close();assertEquals(1,pleos.closes)
    }
    @Test fun retiredAndroidCallbacksCannotChangeFallbackReadinessOrFinishItsRequest() {
        val android=Speech();val pleos=Speech();val events=Events();val readiness=mutableListOf<VoiceReadiness>()
        val voice=FallbackVoiceSpeech(android) {pleos};voice.start(readiness::add,events)
        android.ready!!(VoiceReadiness.NO_ENGINE);pleos.ready!!(VoiceReadiness.READY)
        android.ready!!(VoiceReadiness.READY);android.events!!.onDone("new-id");android.events!!.onStart("new-id")
        assertEquals(listOf(VoiceReadiness.READY),readiness);assertTrue(events.done.isEmpty());assertTrue(events.started.isEmpty())
        pleos.events!!.onStart("new-id");pleos.events!!.onDone("new-id")
        assertEquals(listOf("new-id"),events.started);assertEquals(listOf("new-id"),events.done)
    }
    @Test fun closedFallbackRejectsLateInitializationAndNeverConstructsAnotherEngine() {
        val android=Speech();var creates=0;val ready=mutableListOf<VoiceReadiness>()
        val voice=FallbackVoiceSpeech(android) {creates++;Speech()};voice.start(ready::add,Events());voice.close();voice.close()
        android.ready!!(VoiceReadiness.NO_ENGINE);assertEquals(0,creates);assertTrue(ready.isEmpty());assertEquals(1,android.closes)
        assertFalse(voice.speak("id","controlled"))
    }
}
