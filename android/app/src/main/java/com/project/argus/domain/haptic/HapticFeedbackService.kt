package com.project.argus.domain.haptic

interface HapticFeedbackService {
    fun vibrateUrgent()
}

object NoOpHapticFeedbackService : HapticFeedbackService {
    override fun vibrateUrgent() = Unit
}

class HapticCooldownGate(
    private val cooldownMs: Long = 1500L,
    private val clock: () -> Long = System::currentTimeMillis
) {
    private val lock = Any()
    private var hasTriggered = false
    private var lastTriggerTimeMs = 0L

    fun shouldTrigger(): Boolean = synchronized(lock) {
        val now = clock()
        if (!hasTriggered || now - lastTriggerTimeMs >= cooldownMs) {
            hasTriggered = true
            lastTriggerTimeMs = now
            return true
        }
        if (now < lastTriggerTimeMs) {
            lastTriggerTimeMs = now
        }
        false
    }
}
