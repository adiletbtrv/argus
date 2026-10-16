package com.project.argus.domain.tts

interface SpeechLifecycle {
    //Shuts down and releases all TTS engine resources.
    fun shutdown()
}
