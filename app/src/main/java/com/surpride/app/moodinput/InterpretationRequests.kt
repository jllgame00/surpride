package com.surpride.app.moodinput

import com.surpride.app.moodinput.core.*

enum class InterpretationFailure { UNAVAILABLE, NETWORK, TIMEOUT, PROVIDER_ERROR, INVALID_RESPONSE }
enum class InterpretationPhase { IDLE, WORKING, READY, FAILED, CANCELLED, CLOSED }
sealed interface InterpretationReply {
    data class Success(val result:MoodInterpretation):InterpretationReply
    data class Failure(val error:InterpretationFailure):InterpretationReply
}
fun interface ConditionInterpreter {
    /** Callback and cancellation are marshalled to the owner's UI thread. No destination input. */
    fun request(text:String,now:Long,done:(InterpretationReply)->Unit):()->Unit
}
data class InterpretationWorkState(val id:Long=0,val phase:InterpretationPhase=InterpretationPhase.IDLE,
    val result:MoodInterpretation?=null,val error:InterpretationFailure?=null)

/** Attempt-scoped timeout/cancellation; contains no utterance, coordinates or provider error text. */
class InterpretationRequests(private val provider:ConditionInterpreter,private val changed:(InterpretationWorkState)->Unit) {
    var state=InterpretationWorkState();private set
    private var cancelWork:(()->Unit)?=null
    private var closed=false
    fun start(text:String,now:Long):Long {
        if(closed) return state.id
        val id=state.id+1;state=InterpretationWorkState(id,InterpretationPhase.WORKING)
        cancelPending();changed(state)
        if(!current(id)) return id
        try {
            val cancel=provider.request(text,now) {reply->
                if(current(id)) {
                    cancelWork=null
                    state=when(reply) {
                        is InterpretationReply.Success->if(reply.result.patch.isValid())
                            InterpretationWorkState(id,InterpretationPhase.READY,reply.result)
                            else InterpretationWorkState(id,InterpretationPhase.FAILED,error=InterpretationFailure.INVALID_RESPONSE)
                        is InterpretationReply.Failure->InterpretationWorkState(id,InterpretationPhase.FAILED,error=reply.error)
                    };changed(state)
                }
            }
            if(current(id)) cancelWork=cancel else cancel()
        } catch(_:Exception) {
            if(current(id)) {state=InterpretationWorkState(id,InterpretationPhase.FAILED,error=InterpretationFailure.PROVIDER_ERROR);changed(state)}
        }
        return id
    }
    fun expire(id:Long) {
        if(!current(id)) return
        state=InterpretationWorkState(id,InterpretationPhase.FAILED,error=InterpretationFailure.TIMEOUT)
        cancelPending();changed(state)
    }
    fun cancel() {
        if(closed) return
        state=InterpretationWorkState(state.id+1,InterpretationPhase.CANCELLED);cancelPending();changed(state)
    }
    fun close() {if(!closed) {closed=true;state=InterpretationWorkState(state.id+1,InterpretationPhase.CLOSED);cancelPending();changed(state)}}
    private fun current(id:Long)=!closed && state.id==id && state.phase==InterpretationPhase.WORKING
    private fun cancelPending() {val cancel=cancelWork;cancelWork=null;try {cancel?.invoke()} catch(_:Exception) {}}
}
