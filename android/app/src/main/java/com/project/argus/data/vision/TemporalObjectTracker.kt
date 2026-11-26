package com.project.argus.data.vision

import com.project.argus.domain.model.BoundingBox2D
import kotlin.math.hypot

// Confirms moderate-confidence detections across consecutive frames to suppress single-frame glitches.
class TemporalObjectTracker(
    private val confirmationWindowMs: Long = DEFAULT_WINDOW_MS
) {
    private data class TrackedEntry(
        val label: String,
        val box: BoundingBox2D,
        val lastSeenMs: Long,
        val consecutiveHits: Int
    )

    private val trackedEntries = mutableListOf<TrackedEntry>()

    @Synchronized
    fun isConfirmed(
        label: String,
        box: BoundingBox2D,
        confidence: Float,
        immediateThreshold: Float,
        timestampMs: Long,
        frameWidth: Int,
        frameHeight: Int
    ): Boolean {
        if (confidence >= immediateThreshold) {
            recordHit(label, box, timestampMs, frameWidth, frameHeight)
            return true
        }
        val hits = recordHit(label, box, timestampMs, frameWidth, frameHeight)
        return hits >= REQUIRED_CONSECUTIVE_HITS
    }

    @Synchronized
    fun pruneExpired(timestampMs: Long) {
        trackedEntries.removeAll { timestampMs - it.lastSeenMs > confirmationWindowMs }
    }

    @Synchronized
    fun reset() {
        trackedEntries.clear()
    }

    private fun recordHit(
        label: String,
        box: BoundingBox2D,
        timestampMs: Long,
        frameWidth: Int,
        frameHeight: Int
    ): Int {
        val normalizedLabel = label.trim().lowercase()
        val diag = hypot(frameWidth.toDouble(), frameHeight.toDouble()).toFloat().coerceAtLeast(1f)

        val matchIdx = trackedEntries.indexOfFirst { entry ->
            entry.label == normalizedLabel &&
                timestampMs - entry.lastSeenMs <= confirmationWindowMs &&
                isSameObject(entry.box, box, diag)
        }

        return if (matchIdx >= 0) {
            val prev = trackedEntries[matchIdx]
            // Same timestamp update should not artificially double-count within one frame.
            val newHits = if (timestampMs > prev.lastSeenMs) {
                prev.consecutiveHits + 1
            } else {
                prev.consecutiveHits
            }
            trackedEntries[matchIdx] = prev.copy(
                box = box,
                lastSeenMs = timestampMs,
                consecutiveHits = newHits
            )
            newHits
        } else {
            trackedEntries.add(
                TrackedEntry(
                    label = normalizedLabel,
                    box = box,
                    lastSeenMs = timestampMs,
                    consecutiveHits = 1
                )
            )
            1
        }
    }

    private fun isSameObject(a: BoundingBox2D, b: BoundingBox2D, frameDiagonal: Float): Boolean {
        if (a.iou(b) >= 0.22f) return true
        val dist = hypot((a.centerX() - b.centerX()).toDouble(), (a.centerY() - b.centerY()).toDouble()).toFloat()
        return dist / frameDiagonal <= 0.16f
    }

    companion object {
        const val DEFAULT_WINDOW_MS = 850L
        private const val REQUIRED_CONSECUTIVE_HITS = 2
    }
}
