package com.surpride.navhostpoc

import android.content.Context
import android.content.ContextWrapper
import android.graphics.SurfaceTexture
import android.view.Surface
import androidx.car.app.CarContext
import androidx.car.app.HostDispatcher
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.surpride.navhostpoc.voice.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Native Surface.release and controller deadlines; no Host, GPS, actual TTS or focus authority. */
@RunWith(AndroidJUnit4::class)
class LifecycleVoiceDeviceTest {
    @Test fun lateNativeSurfaceCannotReattachAfterScreenHasNoForegroundAuthority() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val owner=object:LifecycleOwner {val registry=LifecycleRegistry(this);override fun getLifecycle()=registry}
            val base=instrumentation.targetContext
            val isolated=object:ContextWrapper(base) {override fun getApplicationContext():Context=this}
            val context=object:CarContext(owner.registry,HostDispatcher()) {init {attachBaseContext(isolated)}}
            val screen=ActiveMysteryScreen(context,null)
            val field=screen.javaClass.getDeclaredField("surfaces").apply {isAccessible=true}
            val texture=SurfaceTexture(0);val surface=Surface(texture)
            assertTrue(surface.isValid)
            (field.get(screen) as SurfaceCallback).onSurfaceAvailable(SurfaceContainer(surface,1200,800,160))
            assertFalse(surface.isValid)
            assertNull(screen.javaClass.getDeclaredField("surface").apply {isAccessible=true}.get(screen))
            texture.release()
        }
    }
    @Test fun nativeControllerTimeoutRetiresLeaseAndIgnoresLateCallbackWithoutReplay() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            var timeout:(()->Unit)?=null;var millis=0L;var held=false;var spoken=0
            lateinit var events:VoiceUtteranceEvents;var id=""
            val speech=object:VoiceSpeechPort {
                override val backend=VoiceBackend.ANDROID_TTS
                override fun start(onReady:(VoiceReadiness)->Unit,callbacks:VoiceUtteranceEvents) {events=callbacks;onReady(VoiceReadiness.READY)}
                override fun speak(utterance:String,text:String):Boolean {id=utterance;spoken++;return true}
                override fun stop()=Unit
                override fun close()=Unit
            }
            val delay=object:VoiceDelayPort {override fun schedule(delayMillis:Long,action:()->Unit):()->Unit {millis=delayMillis;timeout=action;return {}}}
            val controller=NavigationVoiceController(speech,object:VoiceFocusPort {
                override fun request(onLoss:()->Unit):Boolean {held=true;return true}
                override fun abandon() {held=false}
            },delay)
            controller.start {};controller.setEnabled(true);controller.setOwnership(true)
            controller.onProgress(1,1,2,500.0)
            assertTrue(held);assertEquals(13_500L,millis)
            timeout!!();timeout!!();events.onDone(id)
            assertFalse(held);assertFalse(controller.state.enabled);assertEquals(VoiceStatus.TIMEOUT,controller.state.status)
            assertEquals(1,controller.state.errors);assertEquals(0,controller.state.completed);assertEquals(1,spoken)
            controller.close()
        }
    }
}
