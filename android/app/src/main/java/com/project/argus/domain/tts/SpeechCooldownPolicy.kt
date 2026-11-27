package com.project.argus.domain.tts

import java.util.Locale

/**
 * Pure-Kotlin thread-safe speech cooldown policy preventing repetitive voice spam
 * and A->B->A urgent ping-pong interruptions while guaranteeing that every new urgent
 * threat breaks through immediately.
 */
class SpeechCooldownPolicy(
    private val sameTextCooldownMs: Long = DEFAULT_SAME_TEXT_COOLDOWN_MS,
    private val globalCooldownMs: Long = DEFAULT_GLOBAL_COOLDOWN_MS,
    private val urgentSameTextCooldownMs: Long = DEFAULT_URGENT_SAME_TEXT_COOLDOWN_MS,
    private val clock: () -> Long = System::currentTimeMillis
) {
    private val lock = Any()

    private var hasSpoken = false
    private var lastSpokenTimeMs = 0L
    private var lastSpokenKey = ""

    private val recentPhrases = LinkedHashMap<String, Long>(MAX_PHRASE_HISTORY, 0.75f, true)
    private val recentUrgentPhrases = LinkedHashMap<String, Long>(MAX_PHRASE_HISTORY, 0.75f, true)

    fun shouldSpeak(
        text: String,
        urgent: Boolean,
        isCurrentlySpeaking: Boolean = false
    ): Boolean = synchronized(lock) {
        val key = text.trim().lowercase(Locale.ROOT)
        if (key.isEmpty()) return false

        val now = clock()
        reanchorOnClockRollbackLocked(now)

        if (urgent) {
            val lastUrgentTime = recentUrgentPhrases[key]
            if (lastUrgentTime != null && now - lastUrgentTime < urgentSameTextCooldownMs) {
                return false
            }
            if (isCurrentlySpeaking && hasSpoken && key == lastSpokenKey) {
                return false
            }
            hasSpoken = true
            lastSpokenTimeMs = now
            lastSpokenKey = key
            recordPhraseLocked(recentPhrases, key, now)
            recordPhraseLocked(recentUrgentPhrases, key, now)
            return true
        }

        if (isCurrentlySpeaking && hasSpoken && key == lastSpokenKey) {
            return false
        }

        val lastPhraseTime = recentPhrases[key]
        if (lastPhraseTime != null && now - lastPhraseTime < sameTextCooldownMs) {
            return false
        }

        if (hasSpoken && now - lastSpokenTimeMs < globalCooldownMs) {
            return false
        }

        hasSpoken = true
        lastSpokenTimeMs = now
        lastSpokenKey = key
        recordPhraseLocked(recentPhrases, key, now)
        return true
    }

    fun reset() = synchronized(lock) {
        hasSpoken = false
        lastSpokenTimeMs = 0L
        lastSpokenKey = ""
        recentPhrases.clear()
        recentUrgentPhrases.clear()
    }

    private fun reanchorOnClockRollbackLocked(now: Long) {
        if (hasSpoken && now < lastSpokenTimeMs) {
            lastSpokenTimeMs = now
        }
        reanchorMapLocked(recentPhrases, now)
        reanchorMapLocked(recentUrgentPhrases, now)
    }

    private fun reanchorMapLocked(map: LinkedHashMap<String, Long>, now: Long) {
        for (entry in map.entries) {
            if (now < entry.value) {
                entry.setValue(now)
            }
        }
    }

    private fun recordPhraseLocked(map: LinkedHashMap<String, Long>, key: String, now: Long) {
        map[key] = now
        if (map.size > MAX_PHRASE_HISTORY) {
            val oldestKey = map.entries.firstOrNull()?.key
            if (oldestKey != null) {
                map.remove(oldestKey)
            }
        }
    }

    companion object {
        const val DEFAULT_SAME_TEXT_COOLDOWN_MS = 4000L
        const val DEFAULT_GLOBAL_COOLDOWN_MS = 2500L
        const val DEFAULT_URGENT_SAME_TEXT_COOLDOWN_MS = 1500L
        private const val MAX_PHRASE_HISTORY = 16
    }
}
