package com.surpride.navhostpoc.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.annotation.MainThread

/** Public Android / Pleos APIs only. New instances start OFF; close on lifecycle stop. */
@MainThread
class AndroidNavigationVoice(context:Context):AutoCloseable {
    private val controller=NavigationVoiceController(FallbackVoiceSpeech(AndroidVoiceSpeech(context.applicationContext)) {
        PleosOnDeviceVoiceSpeech({OfficialPleosTtsSession(context.applicationContext)},AndroidVoiceDelay())
    },AndroidVoiceFocus(context.applicationContext))
    val state:NavigationVoiceState get() {checkMain();return controller.state}
    fun start(onChanged:(NavigationVoiceState)->Unit) {checkMain();controller.start(onChanged)}
    fun setEnabled(enabled:Boolean) {checkMain();controller.setEnabled(enabled)}
    fun setOwnership(owned:Boolean) {checkMain();controller.setOwnership(owned)}
    /** Generation must increase after route replacement or invalidateRoute(). No backend text accepted. */
    fun onProgress(routeGeneration:Long,maneuverIndex:Int,kakaoType:Int,distanceMeters:Double) {
        checkMain();controller.onProgress(routeGeneration,maneuverIndex,kakaoType,distanceMeters)
    }
    fun announceRerouting() {checkMain();controller.announceRerouting()}
    fun announceGpsLost() {checkMain();controller.announceGpsLost()}
    fun announceNearDestination() {checkMain();controller.announceNearDestination()}
    fun invalidateRoute() {checkMain();controller.invalidateRoute()}
    fun stop() {checkMain();controller.stop()}
    override fun close() {checkMain();controller.close()}
    private fun checkMain() {check(Looper.myLooper()==Looper.getMainLooper()) {"Main thread required"}}
}

internal fun navigationAudioAttributes()=AudioAttributes.Builder()
    .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()

private class AndroidVoiceFocus(context:Context):VoiceFocusPort {
    private val audio=context.getSystemService(AudioManager::class.java)
    private val handler=Handler(Looper.getMainLooper())
    private var request:AudioFocusRequest?=null
    override fun request(onLoss:()->Unit):Boolean {
        abandon()
        val target=AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(navigationAudioAttributes())
            .setAcceptsDelayedFocusGain(false)
            .setWillPauseWhenDucked(true)
            .setOnAudioFocusChangeListener({change->
                if(change==AudioManager.AUDIOFOCUS_LOSS || change==AudioManager.AUDIOFOCUS_LOSS_TRANSIENT || change==AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) onLoss()
            },handler).build()
        request=target
        return audio?.requestAudioFocus(target)==AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }
    override fun abandon() {
        val old=request;request=null
        if(old!=null) audio?.abandonAudioFocusRequest(old)
    }
}

private class AndroidVoiceSpeech(private val context:Context):VoiceSpeechPort {
    override val backend=VoiceBackend.ANDROID_TTS
    override val preRoll=VoicePreRoll.ANDROID_QUEUE_180_MS
    private val handler=Handler(Looper.getMainLooper())
    private var engine:TextToSpeech?=null
    private var events:VoiceUtteranceEvents?=null
    private var readyCallback:((VoiceReadiness)->Unit)?=null
    private var readiness:VoiceReadiness?=null
    override var networkRequired:Boolean?=null;private set
    private var closed=false;private var generation=0L
    override fun start(onReady:(VoiceReadiness)->Unit,events:VoiceUtteranceEvents) {
        if(closed) return
        readyCallback=onReady;this.events=events
        readiness?.let {onReady(it);return}
        if(engine!=null) return
        val token=++generation
        engine=TextToSpeech(context) {status->
            // TTS init/listener callbacks may come from other threads, or during construction.
            handler.post {
                if(!closed && token==generation) configure(status,token)
            }
        }
    }
    private fun configure(status:Int,token:Long) {
        val target=engine?:return
        val result=try {
            when {
                target.engines.isNullOrEmpty()->VoiceReadiness.NO_ENGINE
                status!=TextToSpeech.SUCCESS->VoiceReadiness.ERROR
                target.isLanguageAvailable(java.util.Locale.KOREAN)<TextToSpeech.LANG_AVAILABLE->VoiceReadiness.KOREAN_UNAVAILABLE
                else->{
                    // Prefer installed offline Korean, but the installed official engine may expose
                    // network-only Korean. Missing voice-list support is not evidence of no Korean.
                    val voices=try {target.voices?.toList()} catch(_:Exception) {null}
                    val selection=KoreanVoicePolicy.select(voices?.map {
                        KoreanVoiceCandidate(it.name,it.locale.language,it.isNetworkConnectionRequired,
                            TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in (it.features?:emptySet()),it.quality)
                    },true)
                    val selected=voices?.firstOrNull {it.name==selection?.voiceId}
                    val languageSet=if(selected!=null) target.setVoice(selected)==TextToSpeech.SUCCESS
                        else target.setLanguage(java.util.Locale.KOREAN)>=TextToSpeech.LANG_AVAILABLE
                    if(!languageSet) VoiceReadiness.KOREAN_UNAVAILABLE
                    else if(target.setAudioAttributes(navigationAudioAttributes())!=TextToSpeech.SUCCESS) VoiceReadiness.ERROR
                    else {
                        networkRequired=selection?.networkRequired
                        val listener=object:UtteranceProgressListener() {
                            private fun dispatch(id:String?,action:(VoiceUtteranceEvents,String)->Unit) {
                                if(id==null) return
                                handler.post {if(!closed && token==generation) events?.let {action(it,id)}}
                            }
                            override fun onStart(utteranceId:String?)=dispatch(utteranceId) {e,id->if(!id.endsWith("-pre")) e.onStart(id)}
                            override fun onDone(utteranceId:String?)=dispatch(utteranceId) {e,id->if(!id.endsWith("-pre")) e.onDone(id)}
                            @Deprecated("Android compatibility callback")
                            override fun onError(utteranceId:String?)=dispatch(utteranceId) {e,id->e.onError(id.removeSuffix("-pre"))}
                            override fun onError(utteranceId:String?,errorCode:Int)=dispatch(utteranceId) {e,id->e.onError(id.removeSuffix("-pre"))}
                            override fun onStop(utteranceId:String?,interrupted:Boolean)=dispatch(utteranceId) {e,id->if(!id.endsWith("-pre")) e.onError(id)}
                        }
                        if(target.setOnUtteranceProgressListener(listener)==TextToSpeech.SUCCESS) VoiceReadiness.READY else VoiceReadiness.ERROR
                    }
                }
            }
        } catch(_:Exception) {VoiceReadiness.ERROR}
        if(result!=VoiceReadiness.READY) networkRequired=null
        readiness=result;readyCallback?.invoke(result)
    }
    override fun speak(id:String,text:String):Boolean {
        if(closed || readiness!=VoiceReadiness.READY) return false
        val target=engine?:return false
        // Pleos recommends 150–200 ms before each distinct guidance. This is TTS queue silence,
        // not a scheduler-only delay. Device output initialization/clipping still needs AVD QA.
        if(target.playSilentUtterance(180L,TextToSpeech.QUEUE_FLUSH,"$id-pre")!=TextToSpeech.SUCCESS) return false
        return target.speak(text,TextToSpeech.QUEUE_ADD,Bundle(),id)==TextToSpeech.SUCCESS
    }
    override fun stop() {engine?.stop()}
    override fun close() {
        if(closed) return
        closed=true;generation++;readyCallback=null;events=null;readiness=null;networkRequired=null
        val old=engine;engine=null
        try {old?.stop()} finally {old?.shutdown()}
    }
}
