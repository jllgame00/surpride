package com.surpride.navhostpoc.voice

import android.os.Looper
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34])
class AndroidVoiceWatchdogTest {
    private open class Speech:VoiceSpeechPort {
        override val backend=VoiceBackend.ANDROID_TTS
        lateinit var events:VoiceUtteranceEvents
        val ids=mutableListOf<String>();var stops=0
        override fun start(onReady:(VoiceReadiness)->Unit,events:VoiceUtteranceEvents) {this.events=events;onReady(VoiceReadiness.READY)}
        override fun speak(id:String,text:String):Boolean {ids+=id;return true}
        override fun stop() {stops++}
        override fun close()=Unit
    }
    private class Focus:VoiceFocusPort {
        var held=false;var releases=0;var loss:(()->Unit)?=null
        override fun request(onLoss:()->Unit):Boolean {held=true;loss=onLoss;return true}
        override fun abandon() {held=false;releases++}
    }
    private class Harness {
        val speech=Speech();val focus=Focus();val voice=NavigationVoiceController(speech,focus)
        init {voice.start {};voice.setEnabled(true);voice.setOwnership(true)}
        fun say(index:Int=1)=voice.onProgress(1,index,2,500.0)
        fun waitMs(ms:Long)=shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))
    }
    @Test fun queueSuccessWithoutCallbacksCancelsSpeechAndReleasesFocusAtDeadline() {
        val h=Harness();h.say();assertTrue(h.focus.held)
        h.waitMs(13_499);assertTrue(h.focus.held)
        h.waitMs(1);assertFalse(h.focus.held);assertFalse(h.voice.state.enabled)
        assertEquals(1,h.voice.state.errors);assertEquals(1,h.speech.ids.size)
    }
    @Test fun doneJustBeforeDeadlinePreventsTimeoutAndDuplicateCleanup() {
        val h=Harness();h.say();h.waitMs(13_499);h.speech.events.onDone(h.speech.ids.single())
        val releases=h.focus.releases;h.waitMs(1)
        assertEquals(VoiceStatus.COMPLETED,h.voice.state.status);assertEquals(0,h.voice.state.errors)
        assertEquals(releases,h.focus.releases)
    }
    @Test fun lateCompletionAfterTimeoutCannotTurnFailureIntoSuccess() {
        val h=Harness();h.say();val id=h.speech.ids.single();h.waitMs(30_000)
        h.speech.events.onDone(id)
        assertEquals(0,h.voice.state.completed);assertFalse(h.focus.held);assertEquals(1,h.voice.state.errors)
    }
    @Test fun replacingUtteranceDoesNotLetOldDeadlineReleaseNewFocus() {
        val h=Harness();h.say();h.waitMs(5000);h.say(2)
        h.waitMs(8500);assertTrue(h.focus.held)
        h.waitMs(5000);assertFalse(h.focus.held);assertEquals(2,h.speech.ids.size)
    }
    @Test fun ownershipLossCancelsDeadlineAndSpeech() {
        val h=Harness();h.say();h.voice.setOwnership(false);val releases=h.focus.releases
        h.waitMs(30_000);assertFalse(h.focus.held);assertEquals(releases,h.focus.releases);assertEquals(0,h.voice.state.errors)
    }
    @Test fun audioFocusLossCancelsDeadlineWithoutRetry() {
        val h=Harness();h.say();h.focus.loss!!();h.waitMs(30_000)
        assertEquals(VoiceStatus.FOCUS_LOST,h.voice.state.status);assertFalse(h.focus.held);assertEquals(1,h.speech.ids.size)
    }
    @Test fun closeAndReentryIgnoreOldCallbacksAndTimers() {
        val old=Harness();old.say();val id=old.speech.ids.single();old.voice.close()
        val next=Harness();next.say();old.speech.events.onDone(id);old.waitMs(5000)
        assertTrue(next.focus.held);assertEquals(0,next.voice.state.completed)
        next.voice.close();next.waitMs(30_000);assertFalse(next.focus.held)
    }
    @Test fun cancelledOldTimeoutCannotReleaseANewerLeaseEvenWhenDeliveredAnyway() {
        val actions=mutableListOf<()->Unit>();val speech=Speech();val focus=Focus()
        val voice=NavigationVoiceController(speech,focus,object:VoiceDelayPort {override fun schedule(delayMillis:Long,action:()->Unit):()->Unit {actions+=action;return {}}})
        voice.start {};voice.setEnabled(true);voice.setOwnership(true)
        voice.onProgress(1,1,2,500.0);voice.onProgress(1,2,2,500.0)
        actions.first()();assertTrue(focus.held);assertEquals(0,voice.state.errors)
        actions.last()();actions.last()();assertFalse(focus.held);assertEquals(1,voice.state.errors)
        voice.close()
    }
    @Test fun stopAndRestartRejectPreviousSessionTimeoutAndCompletion() {
        val actions=mutableListOf<()->Unit>();val speech=Speech();val focus=Focus()
        val voice=NavigationVoiceController(speech,focus,object:VoiceDelayPort {override fun schedule(delayMillis:Long,action:()->Unit):()->Unit {actions+=action;return {}}})
        voice.start {};voice.setEnabled(true);voice.setOwnership(true);voice.onProgress(1,1,2,500.0)
        val oldId=speech.ids.single();val oldEvents=speech.events
        voice.stop();voice.start {};voice.setEnabled(true);voice.setOwnership(true);voice.onProgress(2,1,2,500.0)
        actions.first()();oldEvents.onDone(oldId)
        assertTrue(focus.held);assertEquals(0,voice.state.completed);assertEquals(0,voice.state.errors)
        voice.close()
    }
    @Test fun synchronousCompletionCannotBeOverwrittenByQueuedStateOrArmADeadline() {
        val speech=object:Speech() {override fun speak(id:String,text:String):Boolean {super.speak(id,text);events.onDone(id);return true}}
        val focus=Focus();var armed=0
        val voice=NavigationVoiceController(speech,focus,object:VoiceDelayPort {override fun schedule(delayMillis:Long,action:()->Unit):()->Unit {armed++;return {}}})
        voice.start {};voice.setEnabled(true);voice.setOwnership(true);voice.onProgress(1,1,2,500.0)
        assertEquals(VoiceStatus.COMPLETED,voice.state.status);assertEquals(1,voice.state.completed)
        assertEquals(0,armed);assertFalse(focus.held);voice.close()
    }

}
