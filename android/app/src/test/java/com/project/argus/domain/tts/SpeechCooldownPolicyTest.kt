package com.project.argus.domain.tts

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SpeechCooldownPolicyTest {

    private lateinit var policy: SpeechCooldownPolicy

    @Before
    fun setUp() {
        policy = SpeechCooldownPolicy(
            minAlertIntervalMs = 2000L,
            sameNonUrgentCooldownMs = 5000L,
            urgentSameCooldownMs = 3000L,
            urgentDifferentCooldownMs = 1000L
        )
    }

    @Test
    fun shouldSpeak_firstCall_returnsTrue() {
        assertTrue(policy.shouldSpeak("Впереди стул", urgent = false, isCurrentlySpeaking = false, nowMs = 1000L))
    }

    @Test
    fun shouldSpeak_sameNonUrgentPhraseWithinCooldown_returnsFalse() {
        assertTrue(policy.shouldSpeak("Впереди стул", urgent = false, isCurrentlySpeaking = false, nowMs = 1000L))
        assertFalse(policy.shouldSpeak("Впереди стул", urgent = false, isCurrentlySpeaking = false, nowMs = 3000L))
        assertTrue(policy.shouldSpeak("Впереди стул", urgent = false, isCurrentlySpeaking = false, nowMs = 6500L))
    }

    @Test
    fun shouldSpeak_urgentAlert_interruptsImmediately() {
        assertTrue(policy.shouldSpeak("Впереди стул", urgent = false, isCurrentlySpeaking = true, nowMs = 1000L))
        assertTrue(policy.shouldSpeak("Внимание, близко препятствие: 20 см!", urgent = true, isCurrentlySpeaking = true, nowMs = 1100L))
    }

    @Test
    fun shouldSpeak_rapidAlternatingPhrases_areThrottledByMinInterval() {
        assertTrue(policy.shouldSpeak("Впереди стул", urgent = false, isCurrentlySpeaking = false, nowMs = 1000L))
        assertFalse(policy.shouldSpeak("Впереди стол", urgent = false, isCurrentlySpeaking = false, nowMs = 1500L))
        assertTrue(policy.shouldSpeak("Впереди стол", urgent = false, isCurrentlySpeaking = false, nowMs = 3100L))
    }
}
