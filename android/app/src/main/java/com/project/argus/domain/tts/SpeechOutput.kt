package com.project.argus.domain.tts

interface SpeechOutput {

     //Speaks the given English text prompt.
     //@param text English announcement text.
     //@param urgent If true, interrupts and prioritizes over standard obstacle information.
    fun speak(text: String, urgent: Boolean = false)

    /**
     * Stops any currently ongoing speech utterance.
     */
    fun stop()
}
