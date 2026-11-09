package com.surpride.navhostpoc.voice

import android.content.Context
import android.os.Handler
import android.os.Looper
import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.constant.Mode
import ai.pleos.playground.tts.constant.ErrorType
import ai.pleos.playground.tts.listener.EventListener
import ai.pleos.playground.tts.listener.OnInitializeListener

internal class AndroidVoiceDelay:VoiceDelayPort {
    private val handler=Handler(Looper.getMainLooper())
    override fun schedule(delayMillis:Long,action:()->Unit):()->Unit {
        val task=Runnable(action);handler.postDelayed(task,delayMillis)
        return {handler.removeCallbacks(task)}
    }
}

/** Only documented public SDK 3.3.1 APIs. Never registers an app or selects SERVER/HYBRID. */
internal class OfficialPleosTtsSession(context:Context):PleosTtsSessionPort {
    private val handler=Handler(Looper.getMainLooper())
    private val sdk=TextToSpeech(context.applicationContext,Mode.ON_DEVICE)
    private var closed=false;private var listener:EventListener?=null
    override fun initialize(onReady:(VoiceReadiness)->Unit,events:PleosTtsEvents) {
        sdk.initialize(object:OnInitializeListener {
            override fun onSuccess() {
                handler.post {
                    if(closed) return@post
                    try {
                        sdk.setAudioAttributes(navigationAudioAttributes())
                        sdk.setLocale(java.util.Locale.KOREA)
                        val target=object:EventListener {
                            private fun dispatch(action:()->Unit) {handler.post {if(!closed) action()}}
                            override fun onReady()=Unit
                            override fun onStart()=dispatch(events::onStart)
                            override fun onDone()=dispatch(events::onDone)
                            override fun onStop()=dispatch(events::onError)
                            override fun onError(errorType:ErrorType)=dispatch(events::onError)
                            override fun onUpdatedRms(rms:Double)=Unit
                        }
                        listener=target;sdk.addEventListener(target)
                        onReady(VoiceReadiness.READY)
                    } catch(_:Exception) {onReady(VoiceReadiness.ERROR)} catch(_:LinkageError) {onReady(VoiceReadiness.ERROR)}
                }
            }
            override fun onFailure(errorType:ErrorType) {
                handler.post {if(!closed) onReady(if(errorType==ErrorType.UNAVAILABLE_LANGUAGE) VoiceReadiness.KOREAN_UNAVAILABLE else VoiceReadiness.ERROR)}
            }
        })
    }
    override fun speak(text:String) {
        try {sdk.speak(text,true)} catch(_:LinkageError) {error("PLEOS_TTS_API_UNAVAILABLE")}
    }
    override fun stop() {
        try {sdk.stop()} catch(_:LinkageError) {error("PLEOS_TTS_API_UNAVAILABLE")}
    }
    override fun close() {
        if(closed) return
        closed=true
        val previous=listener;listener=null
        var failed=false
        try {if(previous!=null) sdk.removeEventListener(previous)} catch(_:Exception) {failed=true} catch(_:LinkageError) {failed=true}
        try {sdk.release()} catch(_:Exception) {failed=true} catch(_:LinkageError) {failed=true}
        if(failed) error("PLEOS_TTS_CLEANUP_FAILED")
    }
}
