package com.project.argus.domain.tts

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechCooldownPolicyTest {

    @Test
    fun shouldSpeak_firstInvocationAtZeroTimestamp_returnsTrue() {
        var nowMs = 0L
        val policy = SpeechCooldownPolicy(clock = { nowMs })

        assertTrue(policy.shouldSpeak("Впереди стул", urgent = false))
    }

    @Test
    fun shouldSpeak_blankOrWhitespaceText_returnsFalse() {
        val policy = SpeechCooldownPolicy(clock = { 1000L })

        assertFalse(policy.shouldSpeak("", urgent = false))
        assertFalse(policy.shouldSpeak("   ", urgent = true))
    }

    @Test
    fun shouldSpeak_sameNonUrgentPhrase_blocksWithin4000msAndAllowsAt4000ms() {
        var nowMs = 0L
        val policy = SpeechCooldownPolicy(clock = { nowMs })

        assertTrue(policy.shouldSpeak("Впереди стул", urgent = false))

        nowMs = 1500L
        assertFalse(policy.shouldSpeak("Впереди стул", urgent = false))

        nowMs = 3999L
        assertFalse(policy.shouldSpeak("  впереди стул ", urgent = false))

        nowMs = 4000L
        assertTrue(policy.shouldSpeak("Впереди стул", urgent = false))
    }

    @Test
    fun shouldSpeak_differentNonUrgentPhrases_throttledByGlobalCooldownAndHistory() {
        var nowMs = 1000L
        val policy = SpeechCooldownPolicy(
            sameTextCooldownMs = 4000L,
            globalCooldownMs = 2500L,
            clock = { nowMs }
        )

        assertTrue(policy.shouldSpeak("Впереди стул", urgent = false))

        // Different non-urgent phrase inside 2500 ms global window is throttled
        nowMs = 2500L
        assertFalse(policy.shouldSpeak("Впереди стол", urgent = false))

        // Allowed once 2500 ms global window elapses
        nowMs = 3500L
        assertTrue(policy.shouldSpeak("Впереди стол", urgent = false))

        // Original phrase at 4500 ms (3500 ms since 1000 ms < 4000 ms) is still suppressed by history
        nowMs = 4500L
        assertFalse(policy.shouldSpeak("Впереди стул", urgent = false))

        // At 6000 ms both 4000 ms same-phrase and 2500 ms global windows have elapsed
        nowMs = 6000L
        assertTrue(policy.shouldSpeak("Впереди стул", urgent = false))
    }

    @Test
    fun shouldSpeak_newUrgentPhrase_alwaysInterruptsOngoingNormalOrPriorUrgentSpeech() {
        var nowMs = 1000L
        val policy = SpeechCooldownPolicy(clock = { nowMs })

        assertTrue(policy.shouldSpeak("Впереди дверной проем", urgent = false, isCurrentlySpeaking = false))

        // Urgent alert breaks through ongoing normal speech immediately
        nowMs = 1100L
        assertTrue(
            policy.shouldSpeak(
                "Внимание, стена прямо по курсу!",
                urgent = true,
                isCurrentlySpeaking = true
            )
        )

        // A NEW, higher-priority urgent threat 150 ms later MUST ALSO break through immediately
        nowMs = 1250L
        assertTrue(
            policy.shouldSpeak(
                "Внимание, близко препятствие: 22 см!",
                urgent = true,
                isCurrentlySpeaking = true
            )
        )
    }

    @Test
    fun shouldSpeak_duplicateUrgentPhrase_suppressedWithinShortWindowThenAllowed() {
        var nowMs = 1000L
        val policy = SpeechCooldownPolicy(urgentSameTextCooldownMs = 1500L, clock = { nowMs })

        assertTrue(
            policy.shouldSpeak(
                "Осторожно, впереди лестница!",
                urgent = true,
                isCurrentlySpeaking = false
            )
        )

        // Duplicate urgent phrase inside 1500 ms window is suppressed
        nowMs = 2499L
        assertFalse(
            policy.shouldSpeak(
                "Осторожно, впереди лестница!",
                urgent = true,
                isCurrentlySpeaking = false
            )
        )

        // Suppressed if engine still reports speaking the same urgent utterance
        nowMs = 2600L
        assertFalse(
            policy.shouldSpeak(
                "Осторожно, впереди лестница!",
                urgent = true,
                isCurrentlySpeaking = true
            )
        )

        // Allowed once 1500 ms elapsed and utterance finished
        assertTrue(
            policy.shouldSpeak(
                "Осторожно, впереди лестница!",
                urgent = true,
                isCurrentlySpeaking = false
            )
        )
    }

    @Test
    fun shouldSpeak_whenClockRollsBackwards_recoversWithoutPermanentLockout() {
        var nowMs = 50_000L
        val policy = SpeechCooldownPolicy(
            sameTextCooldownMs = 4000L,
            globalCooldownMs = 2500L,
            urgentSameTextCooldownMs = 1500L,
            clock = { nowMs }
        )

        assertTrue(policy.shouldSpeak("Впереди стул", urgent = false))

        // Simulate clock rollback from 50_000 to 10_000
        nowMs = 10_000L
        assertFalse(policy.shouldSpeak("Впереди стул", urgent = false))

        // Still inside re-anchored 4000 ms window
        nowMs = 13_999L
        assertFalse(policy.shouldSpeak("Впереди стул", urgent = false))

        // Recovers at 10_000 + 4000 = 14_000 instead of staying locked until 54_000
        nowMs = 14_000L
        assertTrue(policy.shouldSpeak("Впереди стул", urgent = false))
    }

    @Test
    fun reset_clearsCooldownHistoryImmediately() {
        var nowMs = 1000L
        val policy = SpeechCooldownPolicy(clock = { nowMs })

        assertTrue(policy.shouldSpeak("Впереди стул", urgent = false))
        assertTrue(policy.shouldSpeak("Осторожно, впереди лестница!", urgent = true))

        nowMs = 1200L
        assertFalse(policy.shouldSpeak("Впереди стул", urgent = false))
        assertFalse(policy.shouldSpeak("Осторожно, впереди лестница!", urgent = true))

        policy.reset()

        assertTrue(policy.shouldSpeak("Впереди стул", urgent = false))
        assertTrue(policy.shouldSpeak("Осторожно, впереди лестница!", urgent = true))
    }
}
