package com.surpride.app.moodinput.voice

import ai.pleos.playground.stt.SpeechToText
import ai.pleos.playground.stt.constant.ErrorType
import ai.pleos.playground.stt.constant.Mode
import ai.pleos.playground.stt.listener.OnInitializeListener
import ai.pleos.playground.stt.listener.ResultListener
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.Locale

/** Public SpeechToText 3.3.1 API only. ON_DEVICE does not register credentials or upload app audio. */
class PleosSpeechInputSource(context: Context) : SpeechInputSource {
    private val main = Handler(Looper.getMainLooper())
    private val sdk = SpeechToText(context.applicationContext, Mode.ON_DEVICE)
    private val diagnostics = SpeechInputDiagnostics()
    private var callback: ((SpeechInputEvent) -> Unit)? = null
    private var started = false
    private var closed = false
    private var registered = false
    private var requested = false

    private val results = object : ResultListener {
        override fun onReady() {
            diagnostics.record(SpeechDiagnosticEvent.READY)
        }
        override fun onStartedRecognition() {
            diagnostics.record(SpeechDiagnosticEvent.STARTED)
            emit(SpeechInputEvent.Listening)
        }
        override fun onUpdated(stt: String, completed: Boolean) {
            diagnostics.recordText(completed, stt.isNotBlank())
            emit(SpeechInputEvent.Text(stt, completed))
        }
        override fun onEndedRecognition() {
            diagnostics.record(SpeechDiagnosticEvent.ENDED)
            emit(SpeechInputEvent.Ended)
        }
        override fun onError(errorType: ErrorType) = fail(mapError(errorType))
        override fun onUpdatedEpdData(on: Long, off: Long) = diagnostics.record(SpeechDiagnosticEvent.EPD)
        override fun onUpdatedRms(rms: Double) = diagnostics.record(SpeechDiagnosticEvent.RMS)
    }

    override fun start(listener: (SpeechInputEvent) -> Unit) {
        check(!closed && !started)
        started = true
        callback = listener
        sdk.initialize(object : OnInitializeListener {
            override fun onSuccess() = onMain {
                if (closed) {
                    // A service connection can finish after the screen has already closed.
                    try { sdk.release() } catch (_: Exception) { }
                } else {
                    try {
                        diagnostics.record(SpeechDiagnosticEvent.INITIALIZED)
                        sdk.addListener(results)
                        registered = true
                        sdk.setLocale(Locale.KOREA)
                        requested = true
                        sdk.request()
                        diagnostics.record(SpeechDiagnosticEvent.REQUEST_RETURNED)
                    } catch (error: Exception) {
                        fail(if (error is SecurityException)
                            SpeechInputError.PERMISSION_DENIED else SpeechInputError.SDK_ERROR)
                    }
                }
            }
            override fun onFailure(errorType: ErrorType) {
                fail(mapError(errorType))
            }
        })
    }

    override fun stop() {
        if (!requested) return
        requested = false
        // The controller stops before closing; do not count cleanup callbacks as input events.
        diagnostics.finish()
        sdk.stop()
    }

    override fun close() {
        if (closed) return
        closed = true
        // Freeze before cleanup: stop/release may emit callbacks synchronously.
        Log.i("SurprideStt", "SUMMARY mode=ON_DEVICE ${diagnostics.finish()}")
        callback = null
        try { stop() } finally {
            try {
                if (registered) {
                    registered = false
                    sdk.removeListener(results)
                }
            } finally { sdk.release() }
        }
    }

    private fun fail(error: SpeechInputError) {
        diagnostics.recordError(error)
        emit(SpeechInputEvent.Failure(error))
    }

    private fun emit(event: SpeechInputEvent) = onMain { if (!closed) callback?.invoke(event) }
    private fun onMain(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) action() else main.post { action() }
    }

    private fun mapError(error: ErrorType): SpeechInputError = when (error) {
        ErrorType.PERMISSION_DENIED -> SpeechInputError.PERMISSION_DENIED
        ErrorType.NOT_SUPPORTED_API -> SpeechInputError.UNSUPPORTED
        ErrorType.MIC_ERROR, ErrorType.AUDIO_ERROR -> SpeechInputError.MICROPHONE
        ErrorType.ON_DEVICE_ENGINE_ERROR -> SpeechInputError.ENGINE
        ErrorType.UNAVAILABLE_LANGUAGE -> SpeechInputError.LANGUAGE
        ErrorType.INVALID_REQUEST_ERROR -> SpeechInputError.INVALID_REQUEST
        ErrorType.UNAUTHENTICATED -> SpeechInputError.UNAUTHENTICATED
        ErrorType.NETWORK_ERROR -> SpeechInputError.NETWORK
        ErrorType.TIMEOUT -> SpeechInputError.TIMEOUT
        else -> SpeechInputError.SDK_ERROR
    }
}
