package com.project.argus.domain.haptic

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HapticCooldownGateTest {

    @Test
    fun shouldTrigger_firstInvocationAtZeroTimestamp_returnsTrue() {
        var nowMs = 0L
        val gate = HapticCooldownGate(cooldownMs = 1500L, clock = { nowMs })

        assertTrue(gate.shouldTrigger())
    }

    @Test
    fun shouldTrigger_withinCooldownWindow_blocksUntilWindowExpires() {
        var nowMs = 1000L
        val gate = HapticCooldownGate(cooldownMs = 1500L, clock = { nowMs })

        assertTrue(gate.shouldTrigger())

        nowMs = 1000L
        assertFalse(gate.shouldTrigger())

        nowMs = 1800L
        assertFalse(gate.shouldTrigger())

        nowMs = 2499L
        assertFalse(gate.shouldTrigger())

        nowMs = 2500L
        assertTrue(gate.shouldTrigger())

        nowMs = 3999L
        assertFalse(gate.shouldTrigger())

        nowMs = 4000L
        assertTrue(gate.shouldTrigger())
    }

    @Test
    fun noOpService_vibrateUrgent_completesWithoutError() {
        NoOpHapticFeedbackService.vibrateUrgent()
    }
}
