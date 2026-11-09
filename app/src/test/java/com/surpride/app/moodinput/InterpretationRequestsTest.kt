package com.surpride.app.moodinput

import com.surpride.app.moodinput.core.*
import org.junit.Assert.*
import org.junit.Test

class InterpretationRequestsTest {
    private class Pending:ConditionInterpreter {
        val replies=mutableListOf<(InterpretationReply)->Unit>();var cancelled=0
        override fun request(text:String,now:Long,done:(InterpretationReply)->Unit):()->Unit {replies+=done;return {cancelled++}}
    }
    private fun good()=InterpretationReply.Success(MoodInterpretation(PreferencePatch(availableMinutes=90)))
    @Test fun newRequestCancelsOldWorkAndIgnoresItsLateResult() {
        val port=Pending();val gate=InterpretationRequests(port){}
        gate.start("private input",1);gate.start("changed",2)
        assertEquals(1,port.cancelled);port.replies[0](good());assertEquals(InterpretationPhase.WORKING,gate.state.phase)
        port.replies[1](good());assertEquals(90,gate.state.result?.patch?.availableMinutes)
        assertFalse(gate.state.toString().contains("private input"))
    }
    @Test fun timeoutIsAttemptScopedAndHasNoFalseSuccess() {
        val port=Pending();val gate=InterpretationRequests(port){}
        val old=gate.start("old",1);val current=gate.start("new",2)
        gate.expire(old);assertEquals(InterpretationPhase.WORKING,gate.state.phase)
        gate.expire(current);assertEquals(InterpretationFailure.TIMEOUT,gate.state.error)
        port.replies[1](good());assertNull(gate.state.result)
    }
    @Test fun invalidStructuredOutputAndProviderFailureNeverBecomePreferences() {
        val port=Pending();val gate=InterpretationRequests(port){}
        gate.start("x",1);port.replies.last()(InterpretationReply.Success(MoodInterpretation(PreferencePatch(randomness=101))))
        assertEquals(InterpretationFailure.INVALID_RESPONSE,gate.state.error)
        gate.start("x",1);port.replies.last()(InterpretationReply.Failure(InterpretationFailure.UNAVAILABLE))
        assertEquals(InterpretationFailure.UNAVAILABLE,gate.state.error);assertNull(gate.state.result)
    }
    @Test fun backgroundCloseCancelsAndDropsCallbacksAndFutureRequests() {
        val port=Pending();val gate=InterpretationRequests(port){}
        gate.start("x",1);gate.close();port.replies.last()(good())
        assertEquals(InterpretationPhase.CLOSED,gate.state.phase);assertNull(gate.state.result)
        gate.start("x",1);assertEquals(1,port.replies.size)
    }
    @Test fun providerExceptionIsSanitized() {
        val gate=InterpretationRequests(object:ConditionInterpreter {
            override fun request(text:String,now:Long,done:(InterpretationReply)->Unit):()->Unit=throw IllegalStateException("private provider detail")
        }){}
        gate.start("private user text",1)
        assertEquals(InterpretationFailure.PROVIDER_ERROR,gate.state.error)
        assertFalse(gate.state.toString().contains("private"))
    }
    @Test fun cancellationCallbackCannotPublishOldPreferencesDuringReplacement() {
        val states=mutableListOf<InterpretationWorkState>()
        val port=ConditionInterpreter {_,_,done->{done(good())}}
        val gate=InterpretationRequests(port){states+=it}
        val old=gate.start("old",1);gate.start("new",2)
        assertFalse(states.any {it.id==old && it.phase==InterpretationPhase.READY})
    }
}
