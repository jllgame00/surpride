package com.surpride.app.moodinput.voice

import org.junit.Assert.*
import org.junit.Test

class SpeechInputDiagnosticsTest {
    @Test fun readyAndRequestReturnAreNotRecognitionSuccess() {
        val diagnostics = SpeechInputDiagnostics()
        diagnostics.record(SpeechDiagnosticEvent.INITIALIZED)
        diagnostics.record(SpeechDiagnosticEvent.REQUEST_RETURNED)
        diagnostics.record(SpeechDiagnosticEvent.READY)

        val snapshot = diagnostics.snapshot()
        assertEquals(1, snapshot.count(SpeechDiagnosticEvent.READY))
        assertTrue(snapshot.requestReturned)
        assertFalse(snapshot.recognitionSucceeded)
        assertEquals(SpeechDiagnosticEvent.READY, snapshot.lastEvent)
    }

    @Test fun onlyNonemptyCompletedResultCountsAsRecognitionSuccess() {
        val diagnostics = SpeechInputDiagnostics()
        diagnostics.recordText(completed = false, nonempty = true)
        diagnostics.recordText(completed = true, nonempty = false)
        assertFalse(diagnostics.snapshot().recognitionSucceeded)

        diagnostics.recordText(completed = true, nonempty = true)
        val snapshot = diagnostics.snapshot()
        assertEquals(1, snapshot.count(SpeechDiagnosticEvent.PARTIAL))
        assertEquals(1, snapshot.count(SpeechDiagnosticEvent.COMPLETED_EMPTY))
        assertEquals(1, snapshot.count(SpeechDiagnosticEvent.COMPLETED_NONEMPTY))
        assertTrue(snapshot.recognitionSucceeded)
    }

    @Test fun callbackCountsAreCappedAndDoNotAccumulatePayloads() {
        val diagnostics = SpeechInputDiagnostics()
        repeat(1_500) {
            diagnostics.record(SpeechDiagnosticEvent.RMS)
            diagnostics.record(SpeechDiagnosticEvent.EPD)
        }
        val snapshot = diagnostics.snapshot()
        assertEquals(999, snapshot.count(SpeechDiagnosticEvent.RMS))
        assertEquals(999, snapshot.count(SpeechDiagnosticEvent.EPD))
        assertEquals(SpeechDiagnosticEvent.EPD, snapshot.lastEvent)
        assertFalse(snapshot.recognitionSucceeded)
    }

    @Test fun snapshotIsImmutableAndErrorUsesOnlyFiniteAppCode() {
        val diagnostics = SpeechInputDiagnostics()
        val before = diagnostics.snapshot()
        diagnostics.recordError(SpeechInputError.MICROPHONE)
        val after = diagnostics.snapshot()
        assertEquals(0, before.count(SpeechDiagnosticEvent.ERROR))
        assertNull(before.lastError)
        assertEquals(1, after.count(SpeechDiagnosticEvent.ERROR))
        assertEquals(SpeechInputError.MICROPHONE, after.lastError)
        assertEquals(SpeechDiagnosticEvent.ERROR, after.lastEvent)
    }

    @Test fun finishingDiagnosticsFreezesCountsAndPreservesLastSdkEvent() {
        val diagnostics = SpeechInputDiagnostics()
        diagnostics.record(SpeechDiagnosticEvent.READY)
        val finished = diagnostics.finish()
        diagnostics.record(SpeechDiagnosticEvent.RMS)
        diagnostics.recordText(completed = true, nonempty = true)
        diagnostics.recordError(SpeechInputError.SDK_ERROR)
        assertEquals(finished, diagnostics.finish())
        assertEquals(SpeechDiagnosticEvent.READY, finished.lastEvent)
        assertEquals(0, finished.count(SpeechDiagnosticEvent.RMS))
        assertFalse(finished.recognitionSucceeded)
    }
}
