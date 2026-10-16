package com.project.argus.domain.tts

 //[ISP] Composite interface combining SpeechOutput and SpeechLifecycle.
 //Components that only need to speak depend on [SpeechOutput] directly.
interface VoiceAlertService : SpeechOutput, SpeechLifecycle
