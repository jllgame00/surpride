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
    private var callback: ((SpeechInputEvent) -> Unit)? = null
    private var started = false
    private var closed = false
    private var registered = false
    private var requested = false

    private val results = object : ResultListener {
        override fun onReady() {Log.i("SurprideStt","SDK_READY mode=ON_DEVICE")}
        override fun onStartedRecognition() = emit(SpeechInputEvent.Listening)
        override fun onUpdated(stt: String, completed: Boolean) = emit(SpeechInputEvent.Text(stt, completed))
        override fun onEndedRecognition() = emit(SpeechInputEvent.Ended)
        override fun onError(errorType: ErrorType) = emit(SpeechInputEvent.Failure(mapError(errorType)))
        override fun onUpdatedEpdData(on: Long, off: Long) = Unit
        override fun onUpdatedRms(rms: Double) = Unit
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
                        Log.i("SurprideStt","SDK_INITIALIZED mode=ON_DEVICE")
                        sdk.addListener(results)
                        registered = true
                        sdk.setLocale(Locale.KOREA)
                        requested = true
                        sdk.request()
                        Log.i("SurprideStt","REQUEST_CALL_RETURNED mode=ON_DEVICE")
                    } catch (error: Exception) {
                        emit(SpeechInputEvent.Failure(if (error is SecurityException)
                            SpeechInputError.PERMISSION_DENIED else SpeechInputError.SDK_ERROR))
                    }
                }
            }
            override fun onFailure(errorType: ErrorType) {
                Log.i("SurprideStt","INITIALIZATION_FAILED error=${mapError(errorType)}")
                emit(SpeechInputEvent.Failure(mapError(errorType)))
            }
        })
    }

    override fun stop() {
        if (!requested) return
        requested = false
        sdk.stop()
    }

    override fun close() {
        if (closed) return
        closed = true
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
