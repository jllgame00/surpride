package com.surpride.app.moodinput.voice

enum class SpeechInputPhase { IDLE, INITIALIZING, LISTENING, PROCESSING, FINAL, NO_SPEECH, ERROR, CANCELLED, CLOSED }
enum class SpeechInputOrigin { PLEOS_ON_DEVICE, FAKE }
enum class SpeechInputError {
    PERMISSION_DENIED, UNSUPPORTED, MICROPHONE, ENGINE, LANGUAGE, INVALID_REQUEST,
    UNAUTHENTICATED, NETWORK, TIMEOUT, SDK_ERROR
}

/** Text is held only for the active screen; do not persist this object or its text fields. */
data class SpeechInputState(
    val attemptId: Long = 0,
    val phase: SpeechInputPhase = SpeechInputPhase.IDLE,
    val partialText: String = "",
    val finalText: String = "",
    val error: SpeechInputError? = null,
    val origin: SpeechInputOrigin = SpeechInputOrigin.PLEOS_ON_DEVICE
) {
    override fun toString() = "SpeechInputState(attemptId=$attemptId, phase=$phase, error=$error, origin=$origin, text=REDACTED)"
}

sealed interface SpeechInputEvent {
    data object Listening : SpeechInputEvent
    data object Ended : SpeechInputEvent
    data class Text(val text: String, val completed: Boolean) : SpeechInputEvent {
        override fun toString() = "Text(completed=$completed, text=REDACTED)"
    }
    data class Failure(val error: SpeechInputError) : SpeechInputEvent
}

/** One source instance belongs to one attempt. Callbacks and controller calls use the UI thread. */
interface SpeechInputSource {
    val origin: SpeechInputOrigin get() = SpeechInputOrigin.PLEOS_ON_DEVICE
    fun start(listener: (SpeechInputEvent) -> Unit)
    fun stop()
    fun close()
}

/** The screen supplies parked checks, explicit user consent and an attempt-scoped deadline. */
class SpeechInputController(
    private val factory: () -> SpeechInputSource,
    private val onStateChanged: (SpeechInputState) -> Unit
) {
    var state = SpeechInputState()
        private set
    private var source: SpeechInputSource? = null
    private var active = false
    private var closed = false

    fun start(): Long? {
        if (closed) return null
        if (active) return state.attemptId
        val attempt = state.attemptId + 1
        active = true
        state = SpeechInputState(attemptId = attempt, phase = SpeechInputPhase.INITIALIZING)
        publish()
        if (!accepts(attempt)) return attempt
        try {
            val current = factory()
            source = current
            state = state.copy(origin = current.origin)
            current.start { event -> if (accepts(attempt)) receive(event) }
        } catch (error: Exception) {
            if (accepts(attempt)) finish(SpeechInputPhase.ERROR,
                error = if (error is SecurityException) SpeechInputError.PERMISSION_DENIED else SpeechInputError.SDK_ERROR)
        }
        return attempt
    }

    fun expire(attemptId: Long) {
        if (accepts(attemptId)) finish(SpeechInputPhase.NO_SPEECH)
    }

    fun cancel() {
        if (active) finish(SpeechInputPhase.CANCELLED)
        else if (!closed && (state.partialText.isNotEmpty() || state.finalText.isNotEmpty())) {
            state = state.copy(phase = SpeechInputPhase.CANCELLED, partialText = "", finalText = "", error = null)
            publish()
        }
    }

    fun close() {
        if (closed) return
        closed = true
        finish(SpeechInputPhase.CLOSED)
    }

    private fun accepts(attemptId: Long) = active && !closed && state.attemptId == attemptId

    private fun receive(event: SpeechInputEvent) {
        when (event) {
            SpeechInputEvent.Listening -> {
                if (state.phase != SpeechInputPhase.PROCESSING) state = state.copy(phase = SpeechInputPhase.LISTENING)
                publish()
            }
            SpeechInputEvent.Ended -> {
                state = state.copy(phase = SpeechInputPhase.PROCESSING)
                publish()
            }
            is SpeechInputEvent.Text -> {
                if (event.completed) {
                    val final = event.text.trim()
                    finish(if (final.isEmpty()) SpeechInputPhase.NO_SPEECH else SpeechInputPhase.FINAL, final)
                } else {
                    state = state.copy(partialText = event.text)
                    publish()
                }
            }
            is SpeechInputEvent.Failure -> finish(SpeechInputPhase.ERROR, error = event.error)
        }
    }

    private fun finish(phase: SpeechInputPhase, finalText: String = "", error: SpeechInputError? = null) {
        active = false // Invalidate callbacks before stop/release can synchronously emit more events.
        val current = source
        source = null
        try { current?.stop() } catch (_: Exception) { }
        try { current?.close() } catch (_: Exception) { }
        state = state.copy(phase = phase, partialText = "", finalText = finalText, error = error)
        publish()
    }

    private fun publish() = onStateChanged(state)
}
