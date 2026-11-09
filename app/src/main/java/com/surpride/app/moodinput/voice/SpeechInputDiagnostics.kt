package com.surpride.app.moodinput.voice

/** Finite callback vocabulary only: never retain speech, audio, RMS values or EPD offsets. */
internal enum class SpeechDiagnosticEvent {
    INITIALIZED, REQUEST_RETURNED, READY, STARTED, ENDED,
    PARTIAL, COMPLETED_EMPTY, COMPLETED_NONEMPTY, RMS, EPD, ERROR
}

internal data class SpeechDiagnosticSnapshot(
    private val counts: List<Int>,
    val lastEvent: SpeechDiagnosticEvent?,
    val lastError: SpeechInputError?
) {
    fun count(event: SpeechDiagnosticEvent): Int = counts[event.ordinal]
    val requestReturned: Boolean get() = count(SpeechDiagnosticEvent.REQUEST_RETURNED) > 0
    val recognitionSucceeded: Boolean get() = count(SpeechDiagnosticEvent.COMPLETED_NONEMPTY) > 0

    override fun toString(): String = buildString {
        append("requestReturned=$requestReturned recognitionSucceeded=$recognitionSucceeded")
        SpeechDiagnosticEvent.entries.forEach { append(" ${it.name}=${count(it)}") }
        append(" lastEvent=${lastEvent ?: "NONE"} lastError=${lastError ?: "NONE"}")
    }
}

/** One bounded accumulator per source/attempt; SDK callbacks can arrive off the UI thread. */
internal class SpeechInputDiagnostics {
    private val counts = IntArray(SpeechDiagnosticEvent.entries.size)
    private var lastEvent: SpeechDiagnosticEvent? = null
    private var lastError: SpeechInputError? = null
    private var finished: SpeechDiagnosticSnapshot? = null

    @Synchronized fun record(event: SpeechDiagnosticEvent) {
        if (finished != null) return
        counts[event.ordinal] = (counts[event.ordinal] + 1).coerceAtMost(999)
        lastEvent = event
    }

    @Synchronized fun recordText(completed: Boolean, nonempty: Boolean) = record(when {
        !completed -> SpeechDiagnosticEvent.PARTIAL
        nonempty -> SpeechDiagnosticEvent.COMPLETED_NONEMPTY
        else -> SpeechDiagnosticEvent.COMPLETED_EMPTY
    })

    @Synchronized fun recordError(error: SpeechInputError) {
        if (finished != null) return
        lastError = error
        record(SpeechDiagnosticEvent.ERROR)
    }

    @Synchronized fun snapshot(): SpeechDiagnosticSnapshot =
        finished ?: SpeechDiagnosticSnapshot(counts.toList(), lastEvent, lastError)

    @Synchronized fun finish(): SpeechDiagnosticSnapshot =
        finished ?: snapshot().also { finished = it }
}
