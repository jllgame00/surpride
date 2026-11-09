package com.surpride.navhostpoc.voice

internal interface PleosTtsEvents {fun onStart();fun onDone();fun onError()}
internal interface PleosTtsSessionPort {
    fun initialize(onReady:(VoiceReadiness)->Unit,events:PleosTtsEvents)
    fun speak(text:String)
    fun stop()
    fun close()
}
internal interface VoiceDelayPort {fun schedule(delayMillis:Long,action:()->Unit):()->Unit}

/** The SDK has no utterance IDs: every request gets its own SDK session and pinned listener. */
internal class PleosOnDeviceVoiceSpeech(private val factory:()->PleosTtsSessionPort,private val delay:VoiceDelayPort):VoiceSpeechPort {
    override val networkRequired=false
    override val backend=VoiceBackend.PLEOS_ON_DEVICE
    // Public SDK 3.3.1 has no audio-padding/silence API. This is only a scheduler delay.
    override val preRoll=VoicePreRoll.SCHEDULER_200_MS
    private class Session(val port:PleosTtsSessionPort,val token:Long) {
        var initialized=false;var id:String?=null;var text:String?=null;var sent=false;var ended=false
        var cancel:(()->Unit)?=null
        var deadlineCancel:(()->Unit)?=null
    }
    private var session:Session?=null
    private var generation=0L;private var closed=false;private var started=false
    private var changed:(VoiceReadiness)->Unit={}
    private var events:VoiceUtteranceEvents?=null
    private var readiness:VoiceReadiness?=null
    override fun start(onReady:(VoiceReadiness)->Unit,events:VoiceUtteranceEvents) {
        if(closed) return
        changed=onReady;this.events=events
        if(started) {readiness?.let(onReady);return}
        started=true;createSession(null,null)
    }
    private fun current(value:Session)=!closed && session===value && value.token==generation
    private fun createSession(id:String?,text:String?):Boolean {
        val value=try {Session(factory(),++generation)} catch(_:Exception) {failCreation(id);return false} catch(_:LinkageError) {failCreation(id);return false}
        value.id=id;value.text=text;session=value
        if(id!=null) armDeadline(value)
        try {value.port.initialize({result->
            if(current(value)) {
                if(result==VoiceReadiness.READY) {
                    value.initialized=true
                    if(value.id==null) {readiness=result;changed(result)} else schedule(value)
                } else {
                    if(value.id==null) {readiness=result;changed(result)} else failure(value)
                }
            }
        },object:PleosTtsEvents {
            override fun onStart() {if(current(value) && value.sent && !value.ended) value.id?.let {events?.onStart(it)}}
            override fun onDone() {
                if(!current(value) || !value.sent || value.ended) return
                value.ended=true;value.text=null;value.deadlineCancel?.invoke();value.deadlineCancel=null
                value.id?.let {events?.onDone(it)}
            }
            override fun onError() {if(current(value)) failure(value)}
        })} catch(_:Exception) {failure(value);return false} catch(_:LinkageError) {failure(value);return false}
        return true
    }
    private fun failCreation(id:String?) {if(id==null) {readiness=VoiceReadiness.ERROR;changed(VoiceReadiness.ERROR)} else events?.onError(id)}
    override fun speak(id:String,text:String):Boolean {
        if(closed || !started || readiness!=VoiceReadiness.READY) return false
        val value=session
        if(value==null) return createSession(id,text)
        if(value.id!=null) {retire();return createSession(id,text)}
        value.id=id;value.text=text
        armDeadline(value)
        if(value.initialized) schedule(value)
        return true // Accepted locally; SDK speak() is void and provides no queue-success return.
    }
    private fun armDeadline(value:Session) {
        value.deadlineCancel=delay.schedule(15_000L) {
            if(current(value) && !value.ended) {
                // Some OEM/API failures are swallowed by the SDK coroutine. Never turn silence
                // into success or retain focus indefinitely while waiting for a missing callback.
                failure(value)
                if(current(value)) try {retire()} catch(_:Exception) { /* Error already reported. */ } catch(_:LinkageError) { /* Error already reported. */ }
            }
        }
    }
    private fun schedule(value:Session) {
        value.cancel?.invoke()
        value.cancel=delay.schedule(200L) pending@{
            if(current(value) && !value.ended && !value.sent) {
                val text=value.text?:return@pending
                value.sent=true;value.text=null
                try {value.port.speak(text)} catch(_:Exception) {failure(value)} catch(_:LinkageError) {failure(value)}
            }
        }
    }
    private fun failure(value:Session) {
        if(!current(value) || value.ended) return
        value.ended=true;value.text=null;value.cancel?.invoke();value.cancel=null
        value.deadlineCancel?.invoke();value.deadlineCancel=null
        if(value.id!=null) events?.onError(value.id!!) else {readiness=VoiceReadiness.ERROR;changed(VoiceReadiness.ERROR)}
    }
    override fun stop() {if(!closed && session?.id!=null) retire()}
    private fun retire() {
        val previous=session?:return
        session=null;generation++;previous.text=null;previous.cancel?.invoke();previous.cancel=null
        previous.deadlineCancel?.invoke();previous.deadlineCancel=null
        // Invalidate first. SDK stop/remove/release can synchronously or asynchronously callback.
        try {if(previous.initialized) previous.port.stop()} finally {previous.port.close()}
    }
    override fun close() {
        if(closed) return
        closed=true;events=null;changed={};readiness=null
        retire()
    }
}
