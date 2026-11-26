
package com.project.argus.data.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.project.argus.domain.tts.SpeechCooldownPolicy
import com.project.argus.domain.tts.VoiceAlertService
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

// Android implementation of VoiceAlertService using Android TextToSpeech with Russian locale and cooldown filtering.
class AndroidVoiceAlertService(
    context: Context,
    private val cooldownPolicy: SpeechCooldownPolicy = SpeechCooldownPolicy()
) : VoiceAlertService, TextToSpeech.OnInitListener {

    private val appContext = context.applicationContext
    private var tts: TextToSpeech? = TextToSpeech(appContext, this)

    private val isReady = AtomicBoolean(false)
    private val isSpeaking = AtomicBoolean(false)

    companion object {
        private const val TAG = "VoiceAlertService"
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val engine = tts ?: return
            val ruLocale = Locale.forLanguageTag("ru-RU")
            val result = engine.setLanguage(ruLocale)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.w(TAG, "Russian TTS not supported, falling back to default locale")
                engine.setLanguage(Locale.getDefault())
            }
            engine.setSpeechRate(1.05f)

            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    isSpeaking.set(true)
                }

                override fun onDone(utteranceId: String?) {
                    isSpeaking.set(false)
                }

                @Deprecated("Deprecated in Java", ReplaceWith("Unit"))
                override fun onError(utteranceId: String?) {
                    isSpeaking.set(false)
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    isSpeaking.set(false)
                    Log.w(TAG, "TTS Utterance error: $errorCode for $utteranceId")
                }
            })

            isReady.set(true)
            Log.d(TAG, "TTS initialized successfully.")
        } else {
            Log.e(TAG, "TTS initialization failed with status $status")
        }
    }

    @Synchronized
    override fun speak(text: String, urgent: Boolean) {
        val trimmed = text.trim()
        if (!isReady.get() || trimmed.isEmpty()) return

        val now = System.currentTimeMillis()
        if (!cooldownPolicy.shouldSpeak(trimmed, urgent, isSpeaking.get(), now)) {
            return
        }

        if (urgent) {
            tts?.speak(trimmed, TextToSpeech.QUEUE_FLUSH, null, "URGENT_$now")
        } else {
            tts?.speak(trimmed, TextToSpeech.QUEUE_ADD, null, "INFO_$now")
        }
    }

    override fun stop() {
        tts?.stop()
        isSpeaking.set(false)
    }

    override fun shutdown() {
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (e: Exception) {
            Log.e(TAG, "Error shutting down TTS", e)
        } finally {
            tts = null
            isReady.set(false)
            isSpeaking.set(false)
            cooldownPolicy.reset()
        }
    }
}
