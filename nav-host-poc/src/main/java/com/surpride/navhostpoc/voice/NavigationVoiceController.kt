package com.surpride.navhostpoc.voice

/** Safe diagnostic fields only. Queue acceptance and engine callbacks never attest audible output. */
data class NavigationVoiceState(
    val ready:Boolean=false,val enabled:Boolean=false,val owned:Boolean=false,val focusGranted:Boolean=false,
    val status:VoiceStatus=VoiceStatus.STOPPED,val requested:Int=0,val started:Int=0,val completed:Int=0,val errors:Int=0,
    val voiceNetworkRequired:Boolean?=null,val backend:VoiceBackend=VoiceBackend.UNKNOWN,val preRoll:VoicePreRoll=VoicePreRoll.UNKNOWN
)
enum class VoiceBackend { UNKNOWN, ANDROID_TTS, PLEOS_ON_DEVICE }
enum class VoicePreRoll { UNKNOWN, ANDROID_QUEUE_180_MS, SCHEDULER_200_MS }
enum class VoiceStatus { STOPPED, INITIALIZING, DISABLED, NOT_OWNED, READY, QUEUED, SPEAKING, COMPLETED,
    NO_ENGINE, KOREAN_UNAVAILABLE, FOCUS_DENIED, FOCUS_LOST, TIMEOUT, ERROR, CLOSED }
enum class VoiceReadiness { READY, NO_ENGINE, KOREAN_UNAVAILABLE, ERROR }

internal interface VoiceUtteranceEvents {
    fun onStart(id:String)
    fun onDone(id:String)
    fun onError(id:String)
}
internal interface VoiceSpeechPort {
    val networkRequired:Boolean? get()=null
    val backend:VoiceBackend get()=VoiceBackend.UNKNOWN
    val preRoll:VoicePreRoll get()=VoicePreRoll.UNKNOWN
    fun start(onReady:(VoiceReadiness)->Unit,events:VoiceUtteranceEvents)
    /** True means locally accepted. Android also checks queue SUCCESS; Pleos speak is void.
     * Neither means that playback started or was heard. */
    fun speak(id:String,text:String):Boolean
    fun stop()
    fun close()
}
internal interface VoiceFocusPort {
    fun request(onLoss:()->Unit):Boolean
    fun abandon()
}

/** Main-thread controller; accepts no destination, road, address, coordinate, or backend text. */
internal class NavigationVoiceController(private val speech:VoiceSpeechPort,private val focus:VoiceFocusPort,private val delay:VoiceDelayPort?=null) {
    var state=NavigationVoiceState();private set
    private var changed:(NavigationVoiceState)->Unit={}
    private var running=false;private var closed=false;private var session=0L
    private var utterance=0L;private var focusLease=0L;private var activeId:String?=null;private var activeStarted=false
    private var cancelDeadline:(()->Unit)?=null
    private var routeGeneration:Long?=null;private var minimumGeneration=Long.MIN_VALUE
    private var latestManeuver=-1;private var latestStage=-1
    private val statusCues=mutableSetOf<Int>()

    fun start(onChanged:(NavigationVoiceState)->Unit) {
        if(closed) return
        changed=onChanged
        if(running) {publish();return}
        running=true
        val token=++session
        update(state.copy(ready=false,enabled=false,status=VoiceStatus.INITIALIZING))
        try {
            speech.start({ready->
                if(running && !closed && token==session) {
                    if(ready!=VoiceReadiness.READY) cancelOutput()
                    update(state.copy(ready=ready==VoiceReadiness.READY,voiceNetworkRequired=if(ready==VoiceReadiness.READY) speech.networkRequired else null,
                        backend=speech.backend,preRoll=speech.preRoll,status=when(ready) {
                        VoiceReadiness.READY->if(!state.enabled) VoiceStatus.DISABLED else if(!state.owned) VoiceStatus.NOT_OWNED else VoiceStatus.READY
                        VoiceReadiness.NO_ENGINE->VoiceStatus.NO_ENGINE
                        VoiceReadiness.KOREAN_UNAVAILABLE->VoiceStatus.KOREAN_UNAVAILABLE
                        VoiceReadiness.ERROR->VoiceStatus.ERROR
                    }))
                }
            },object:VoiceUtteranceEvents {
                private fun current(id:String)=running && !closed && token==session && id==activeId
                override fun onStart(id:String) {
                    if(!current(id) || activeStarted) return
                    activeStarted=true;update(state.copy(started=state.started+1,status=VoiceStatus.SPEAKING))
                }
                override fun onDone(id:String) {
                    if(!current(id)) return
                    releaseOutput();update(state.copy(completed=state.completed+1,status=VoiceStatus.COMPLETED))
                }
                override fun onError(id:String) {
                    if(!current(id)) return
                    cancelOutput();update(state.copy(enabled=false,errors=state.errors+1,status=VoiceStatus.ERROR))
                }
            })
        } catch(_:Exception) {update(state.copy(ready=false,errors=state.errors+1,status=VoiceStatus.ERROR))}
    }
    fun setEnabled(enabled:Boolean) {
        if(closed || !running) return
        if(state.enabled==enabled) return
        if(!enabled) cancelOutput()
        update(state.copy(enabled=enabled,status=if(!state.ready) state.status else if(!enabled) VoiceStatus.DISABLED else if(!state.owned) VoiceStatus.NOT_OWNED else VoiceStatus.READY))
    }
    fun setOwnership(owned:Boolean) {
        if(closed) return
        if(state.owned==owned) return
        if(!owned) cancelOutput()
        update(state.copy(owned=owned,status=if(!state.ready) state.status else if(!state.enabled) VoiceStatus.DISABLED else if(!owned) VoiceStatus.NOT_OWNED else VoiceStatus.READY))
    }
    fun onProgress(routeGeneration:Long,maneuverIndex:Int,kakaoType:Int,distanceMeters:Double) {
        if(closed || !running || routeGeneration<minimumGeneration || maneuverIndex<0 || !distanceMeters.isFinite() || distanceMeters<0) return
        val previous=this.routeGeneration
        if(previous!=null && routeGeneration<previous) return
        if(previous!=routeGeneration) {
            cancelOutput();this.routeGeneration=routeGeneration
            latestManeuver=-1;latestStage=-1;statusCues.clear()
        }
        if(maneuverIndex<latestManeuver) return
        if(maneuverIndex>latestManeuver) {latestManeuver=maneuverIndex;latestStage=-1}
        val phrase=controlledManeuver(kakaoType)?:return
        val stage=when {distanceMeters<=30->2;distanceMeters<=100->1;distanceMeters<=500->0;else->return}
        if(maneuverIndex==latestManeuver && stage<=latestStage) return
        // Stage controls cadence only. It must not masquerade as the current remaining distance.
        // Intermediate distances round down to ten meters; exact trigger anchors stay exact.
        val text=when {
            stage==2->"잠시 후 $phrase"
            distanceMeters==500.0->"500미터 앞에서 $phrase"
            distanceMeters==100.0->"100미터 앞에서 $phrase"
            else->"약 ${distanceMeters.toInt()/10*10}미터 앞에서 $phrase"
        }
        if(emit(text)) {latestManeuver=maneuverIndex;latestStage=stage}
    }
    fun announceRerouting()=cue(0,"경로를 다시 확인하고 있습니다.")
    fun announceGpsLost()=cue(1,"위치 신호를 확인하고 있습니다.")
    fun announceNearDestination()=cue(2,"목적지 근처입니다. 주변을 확인하세요.")
    private fun cue(kind:Int,text:String) {if(kind !in statusCues && emit(text)) statusCues+=kind}
    fun invalidateRoute() {
        if(closed) return
        routeGeneration?.let {minimumGeneration=if(it==Long.MAX_VALUE) Long.MAX_VALUE else it+1}
        val cleaned=cancelOutput();routeGeneration=null;latestManeuver=-1;latestStage=-1;statusCues.clear()
        update(state.copy(status=when {
            !cleaned->VoiceStatus.ERROR
            !state.ready->state.status
            !state.enabled->VoiceStatus.DISABLED
            !state.owned->VoiceStatus.NOT_OWNED
            else->VoiceStatus.READY
        }))
    }
    fun stop() {
        if(closed) return
        running=false;session++;cancelOutput()
        update(state.copy(enabled=false,owned=false,status=VoiceStatus.STOPPED))
    }
    fun close() {
        if(closed) return
        stop();closed=true
        try {speech.close()} catch(_:Exception) {state=state.copy(errors=state.errors+1)}
        update(state.copy(ready=false,status=VoiceStatus.CLOSED))
        changed={}
    }
    private fun emit(text:String):Boolean {
        if(!running || closed || !state.ready || !state.enabled || !state.owned) return false
        if(!cancelOutput()) {update(state.copy(enabled=false,status=VoiceStatus.ERROR));return false}
        val lease=++focusLease
        val granted=try {focus.request {
            if(running && !closed && lease==focusLease) {
                cancelOutput();update(state.copy(enabled=false,status=VoiceStatus.FOCUS_LOST))
            }
        }} catch(_:Exception) {false}
        // A synchronous loss while requesting also invalidates this lease.
        if(!granted || lease!=focusLease) {
            if(lease==focusLease) {cancelOutput();update(state.copy(enabled=false,status=VoiceStatus.FOCUS_DENIED))}
            return false
        }
        state=state.copy(focusGranted=true)
        val id="voice-${++utterance}"
        activeId=id;activeStarted=false
        val queued=try {speech.speak(id,text)} catch(_:Exception) {false}
        if(!queued) {cancelOutput();update(state.copy(enabled=false,errors=state.errors+1,status=VoiceStatus.ERROR));return false}
        // A synchronous completion/error may already have retired this utterance.
        update(state.copy(requested=state.requested+1,status=if(activeId==id && !activeStarted) VoiceStatus.QUEUED else state.status))
        if(activeId==id && speech.backend==VoiceBackend.ANDROID_TTS) {
            val token=session
            // Conservative 2 characters/second plus engine/pre-roll budget; bounded, not an audibility claim.
            val deadline=(5000L+500L*text.codePointCount(0,text.length)).coerceIn(10_000L,30_000L)
            cancelDeadline=(delay?:AndroidVoiceDelay()).schedule(deadline) {
                if(running && !closed && session==token && activeId==id && focusLease==lease) {
                    cancelOutput();update(state.copy(enabled=false,errors=state.errors+1,status=VoiceStatus.TIMEOUT))
                }
            }
        }
        return true
    }
    private fun releaseOutput() {
        activeId=null;activeStarted=false;focusLease++;cancelDeadline?.invoke();cancelDeadline=null
        try {focus.abandon()} catch(_:Exception) {state=state.copy(errors=state.errors+1)}
        state=state.copy(focusGranted=false)
    }
    private fun cancelOutput():Boolean {
        // Invalidate before touching the engine: stop/abandon may deliver callbacks synchronously.
        activeId=null;activeStarted=false;focusLease++;cancelDeadline?.invoke();cancelDeadline=null
        var cleaned=true
        try {speech.stop()} catch(_:Exception) {cleaned=false;state=state.copy(errors=state.errors+1)}
        try {focus.abandon()} catch(_:Exception) {cleaned=false;state=state.copy(errors=state.errors+1)}
        state=state.copy(focusGranted=false)
        return cleaned
    }
    private fun update(value:NavigationVoiceState) {state=value;publish()}
    private fun publish() {changed(state)}
}

/** Kakao Directions type vocabulary. Do not substitute UI SDK KNRGCode meanings (85 differs).
 * Departure/arrival/waypoint/unknown values are deliberately silent. */
private fun controlledManeuver(type:Int):String?=when(type) {
    0->"직진하세요.";1->"좌회전하세요.";2->"우회전하세요.";3->"유턴하세요."
    5->"왼쪽 방향으로 진행하세요.";6->"오른쪽 방향으로 진행하세요."
    7,42->"출구로 진행하세요.";8,43->"왼쪽 출구로 진행하세요.";9,44->"오른쪽 출구로 진행하세요."
    10,45->"진입로로 진행하세요.";11,46,48->"왼쪽 진입로로 진행하세요.";12,47,49->"오른쪽 진입로로 진행하세요."
    14->"고가 도로로 진입하세요.";15->"지하 차도로 진입하세요.";16->"고가 도로 옆길로 진행하세요.";17->"지하 차도 옆길로 진행하세요."
    in 18..29->"${type-17}시 방향으로 진행하세요."
    in 30..41->"로터리에서 ${type-29}시 방향으로 진행하세요."
    in 70..81->"회전 교차로에서 ${type-69}시 방향으로 진행하세요."
    61->"페리 항로로 진입하세요.";62->"페리 항로에서 진출하세요.";82->"왼쪽으로 직진하세요.";83->"오른쪽으로 직진하세요."
    84->"톨게이트로 진입하세요.";85->"원톨링 구간으로 진입하세요.";86->"분기 후 합류 구간으로 진행하세요."
    300->"톨게이트로 진행하세요.";301->"휴게소로 진행하세요."
    else->null
}
