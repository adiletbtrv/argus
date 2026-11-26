package com.project.argus.domain.tts

import java.util.Locale

// Pure-Kotlin cooldown policy preventing alternating-object voice spam while keeping urgent alerts responsive.
class SpeechCooldownPolicy(
    private val minAlertIntervalMs: Long = DEFAULT_MIN_ALERT_INTERVAL_MS,
    private val sameNonUrgentCooldownMs: Long = DEFAULT_SAME_NON_URGENT_COOLDOWN_MS,
    private val urgentSameCooldownMs: Long = DEFAULT_URGENT_SAME_COOLDOWN_MS,
    private val urgentDifferentCooldownMs: Long = DEFAULT_URGENT_DIFFERENT_COOLDOWN_MS
) {
    private var lastSpokenTimeMs = -1L
    private var lastSpokenKey = ""

    private var lastUrgentTimeMs = -1L
    private var lastUrgentKey = ""

    private val recentPhrases = LinkedHashMap<String, Long>(MAX_PHRASE_HISTORY, 0.75f, true)

    @Synchronized
    fun shouldSpeak(
        text: String,
        urgent: Boolean,
        isCurrentlySpeaking: Boolean,
        nowMs: Long
    ): Boolean {
        val key = text.trim().lowercase(Locale.ROOT)
        if (key.isEmpty()) return false

        if (urgent) {
            if (lastUrgentTimeMs >= 0L) {
                val isSameUrgent = key == lastUrgentKey
                if (isSameUrgent) {
                    if (nowMs - lastUrgentTimeMs < urgentSameCooldownMs || isCurrentlySpeaking) {
                        return false
                    }
                } else {
                    if (nowMs - lastUrgentTimeMs < urgentDifferentCooldownMs && isCurrentlySpeaking) {
                        return false
                    }
                }
            }

            lastUrgentTimeMs = nowMs
            lastUrgentKey = key
            lastSpokenTimeMs = nowMs
            lastSpokenKey = key
            recordPhrase(key, nowMs)
            return true
        }

        if (isCurrentlySpeaking && key == lastSpokenKey) {
            return false
        }

        val lastPhraseTime = recentPhrases[key]
        if (lastPhraseTime != null && nowMs - lastPhraseTime < sameNonUrgentCooldownMs) {
            return false
        }

        if (lastSpokenTimeMs >= 0L && nowMs - lastSpokenTimeMs < minAlertIntervalMs) {
            return false
        }

        lastSpokenTimeMs = nowMs
        lastSpokenKey = key
        recordPhrase(key, nowMs)
        return true
    }

    @Synchronized
    fun reset() {
        lastSpokenTimeMs = -1L
        lastSpokenKey = ""
        lastUrgentTimeMs = -1L
        lastUrgentKey = ""
        recentPhrases.clear()
    }

    private fun recordPhrase(key: String, nowMs: Long) {
        recentPhrases[key] = nowMs
        if (recentPhrases.size > MAX_PHRASE_HISTORY) {
            val oldestKey = recentPhrases.entries.firstOrNull()?.key
            if (oldestKey != null) {
                recentPhrases.remove(oldestKey)
            }
        }
    }

    companion object {
        const val DEFAULT_MIN_ALERT_INTERVAL_MS = 2200L
        const val DEFAULT_SAME_NON_URGENT_COOLDOWN_MS = 6000L
        const val DEFAULT_URGENT_SAME_COOLDOWN_MS = 3500L
        const val DEFAULT_URGENT_DIFFERENT_COOLDOWN_MS = 1200L
        private const val MAX_PHRASE_HISTORY = 16
    }
}
