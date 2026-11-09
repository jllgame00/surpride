package com.surpride.app.moodinput.voice

import org.junit.Assert.*
import org.junit.Test

class SpeechInputControllerTest {
    @Test fun startsOneSessionAndKeepsPartialsSeparateFromFinalText() {
        val source = Source()
        val controller = controller(source)
        val attempt = controller.start()
        controller.start()
        assertEquals(1, source.starts)
        assertEquals(attempt, controller.state.attemptId)
        assertEquals(SpeechInputOrigin.FAKE, controller.state.origin)
        assertEquals(SpeechInputPhase.INITIALIZING, controller.state.phase)
        source.emit(SpeechInputEvent.Listening)
        source.emit(SpeechInputEvent.Text("a partial phrase", false))
        assertEquals(SpeechInputPhase.LISTENING, controller.state.phase)
        assertEquals("a partial phrase", controller.state.partialText)
        assertEquals("", controller.state.finalText)
    }

    @Test fun completedTextBecomesOneFinalAndReleasesMicrophone() {
        val source = Source()
        val controller = controller(source)
        controller.start()
        source.emit(SpeechInputEvent.Text("  a final phrase  ", true))
        source.emit(SpeechInputEvent.Text("late result", true))
        assertEquals(SpeechInputPhase.FINAL, controller.state.phase)
        assertEquals("a final phrase", controller.state.finalText)
        assertEquals("", controller.state.partialText)
        assertEquals(1, source.stops)
        assertEquals(1, source.closes)
    }

    @Test fun endingRecognitionWaitsForACompletedResult() {
        val source = Source()
        val controller = controller(source)
        controller.start()
        source.emit(SpeechInputEvent.Ended)
        assertEquals(SpeechInputPhase.PROCESSING, controller.state.phase)
        source.emit(SpeechInputEvent.Text("final after end", true))
        assertEquals(SpeechInputPhase.FINAL, controller.state.phase)
        assertEquals("final after end", controller.state.finalText)
    }

    @Test fun completedBlankTextProducesNoSpeech() {
        val source = Source()
        val controller = controller(source)
        controller.start()
        source.emit(SpeechInputEvent.Text(" \n ", true))
        assertEquals(SpeechInputPhase.NO_SPEECH, controller.state.phase)
        assertEquals("", controller.state.finalText)
        assertEquals(1, source.closes)
    }

    @Test fun deadlineWithoutFinalProducesNoSpeechAndDropsPartialText() {
        val source = Source()
        val controller = controller(source)
        val attempt = controller.start()!!
        source.emit(SpeechInputEvent.Text("incomplete text", false))
        source.emit(SpeechInputEvent.Ended)
        controller.expire(attempt)
        assertEquals(SpeechInputPhase.NO_SPEECH, controller.state.phase)
        assertEquals("", controller.state.partialText)
        assertEquals("", controller.state.finalText)
        assertEquals(1, source.stops)
        assertEquals(1, source.closes)
    }

    @Test fun cancellationIgnoresAllLateCallbacksAndClearsText() {
        val source = Source()
        val controller = controller(source)
        controller.start()
        source.emit(SpeechInputEvent.Text("private partial", false))
        controller.cancel()
        source.emit(SpeechInputEvent.Listening)
        source.emit(SpeechInputEvent.Text("late final", true))
        source.emit(SpeechInputEvent.Failure(SpeechInputError.MICROPHONE))
        assertEquals(SpeechInputPhase.CANCELLED, controller.state.phase)
        assertEquals("", controller.state.partialText)
        assertEquals("", controller.state.finalText)
        assertEquals(1, source.closes)
    }

    @Test fun newAttemptRejectsOldResultsAndOldDeadline() {
        val first = Source()
        val second = Source()
        val sources = ArrayDeque(listOf(first, second))
        val controller = SpeechInputController({ sources.removeFirst() }) {}
        val oldAttempt = controller.start()!!
        controller.cancel()
        val newAttempt = controller.start()!!
        first.emit(SpeechInputEvent.Text("old final", true))
        controller.expire(oldAttempt)
        assertTrue(newAttempt > oldAttempt)
        assertEquals(SpeechInputPhase.INITIALIZING, controller.state.phase)
        second.emit(SpeechInputEvent.Text("new final", true))
        assertEquals("new final", controller.state.finalText)
        assertEquals(1, first.closes)
        assertEquals(1, second.closes)
    }

    @Test fun permissionFailureIsFiniteAndReleasesSession() {
        val source = Source()
        val controller = controller(source)
        controller.start()
        source.emit(SpeechInputEvent.Failure(SpeechInputError.PERMISSION_DENIED))
        assertEquals(SpeechInputPhase.ERROR, controller.state.phase)
        assertEquals(SpeechInputError.PERMISSION_DENIED, controller.state.error)
        assertEquals(1, source.closes)
    }

    @Test fun synchronousSecurityExceptionDoesNotExposeItsMessage() {
        val source = Source().apply { startFailure = SecurityException("private transcript") }
        val controller = controller(source)
        controller.start()
        assertEquals(SpeechInputError.PERMISSION_DENIED, controller.state.error)
        assertFalse(controller.state.toString().contains("private transcript"))
        assertEquals(1, source.closes)
    }

    @Test fun factoryFailureReportsSdkErrorWithoutExposingExceptionText() {
        val controller = SpeechInputController({ throw IllegalStateException("private content") }) {}
        controller.start()
        assertEquals(SpeechInputPhase.ERROR, controller.state.phase)
        assertEquals(SpeechInputError.SDK_ERROR, controller.state.error)
        assertFalse(controller.state.toString().contains("private content"))
    }

    @Test fun closeIsIdempotentClearsTextAndPreventsRestart() {
        val source = Source()
        val observed = mutableListOf<SpeechInputState>()
        val controller = SpeechInputController({ source }, observed::add)
        controller.start()
        source.emit(SpeechInputEvent.Text("private partial", false))
        controller.close()
        controller.close()
        assertNull(controller.start())
        source.emit(SpeechInputEvent.Text("late final", true))
        assertEquals(SpeechInputPhase.CLOSED, controller.state.phase)
        assertEquals("", controller.state.partialText)
        assertEquals("", controller.state.finalText)
        assertEquals(1, source.stops)
        assertEquals(1, source.closes)
        assertEquals(SpeechInputPhase.CLOSED, observed.last().phase)
    }

    @Test fun cleanupContinuesWhenStopThrows() {
        val source = Source().apply { stopFailure = IllegalStateException("private content") }
        val controller = controller(source)
        controller.start()
        controller.cancel()
        assertEquals(SpeechInputPhase.CANCELLED, controller.state.phase)
        assertEquals(1, source.closes)
        assertFalse(controller.state.toString().contains("private content"))
    }

    @Test fun stateAndTextEventsRedactTranscriptsFromStringRepresentation() {
        val source = Source()
        val controller = controller(source)
        controller.start()
        source.emit(SpeechInputEvent.Text("private partial", false))
        assertFalse(controller.state.toString().contains("private partial"))
        source.emit(SpeechInputEvent.Text("private final", true))
        assertFalse(controller.state.toString().contains("private final"))
        assertFalse(SpeechInputEvent.Text("private event", true).toString().contains("private event"))
    }

    private fun controller(source: Source) = SpeechInputController({ source }) {}

    private class Source : SpeechInputSource {
        override val origin = SpeechInputOrigin.FAKE
        var starts = 0
        var stops = 0
        var closes = 0
        var startFailure: Exception? = null
        var stopFailure: Exception? = null
        private var listener: ((SpeechInputEvent) -> Unit)? = null
        override fun start(listener: (SpeechInputEvent) -> Unit) {
            starts++
            this.listener = listener
            startFailure?.let { throw it }
        }
        override fun stop() {
            stops++
            stopFailure?.let { throw it }
        }
        override fun close() { closes++ }
        fun emit(event: SpeechInputEvent) { listener?.invoke(event) }
    }
}
