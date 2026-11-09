package com.surpride.navhostpoc.voice

/** Only absence of an Android TTS engine permits the explicitly authorized ON_DEVICE fallback. */
internal class FallbackVoiceSpeech(private val primary:VoiceSpeechPort,private val fallback:()->VoiceSpeechPort):VoiceSpeechPort {
    private var active:VoiceSpeechPort?=primary
    private var changed:(VoiceReadiness)->Unit={}
    private var events:VoiceUtteranceEvents?=null
    private var readiness:VoiceReadiness?=null
    private var generation=0L;private var started=false;private var closed=false;private var usedFallback=false
    override val networkRequired get()=active?.networkRequired
    override val backend get()=active?.backend?:VoiceBackend.UNKNOWN
    override val preRoll get()=active?.preRoll?:VoicePreRoll.UNKNOWN
    override fun start(onReady:(VoiceReadiness)->Unit,events:VoiceUtteranceEvents) {
        if(closed) return
        changed=onReady;this.events=events
        if(started) {readiness?.let(onReady);return}
        started=true;startActive(primary)
    }
    private fun startActive(port:VoiceSpeechPort) {
        val token=++generation
        try {port.start({result->
            if(!closed && token==generation) {
                if(result==VoiceReadiness.NO_ENGINE && !usedFallback) {
                    usedFallback=true;generation++;active=null
                    try {
                        primary.close()
                        val replacement=fallback();active=replacement;startActive(replacement)
                    } catch(_:Exception) {report(VoiceReadiness.ERROR)} catch(_:LinkageError) {report(VoiceReadiness.ERROR)}
                } else report(result)
            }
        },object:VoiceUtteranceEvents {
            override fun onStart(id:String) {if(!closed && token==generation) events?.onStart(id)}
            override fun onDone(id:String) {if(!closed && token==generation) events?.onDone(id)}
            override fun onError(id:String) {if(!closed && token==generation) events?.onError(id)}
        })} catch(_:Exception) {report(VoiceReadiness.ERROR)} catch(_:LinkageError) {report(VoiceReadiness.ERROR)}
    }
    private fun report(result:VoiceReadiness) {if(!closed) {readiness=result;changed(result)}}
    override fun speak(id:String,text:String)=!closed && readiness==VoiceReadiness.READY && active?.speak(id,text)==true
    override fun stop() {if(!closed) active?.stop()}
    override fun close() {
        if(closed) return
        closed=true;generation++;events=null;changed={};readiness=null
        val previous=active;active=null;previous?.close()
    }
}
