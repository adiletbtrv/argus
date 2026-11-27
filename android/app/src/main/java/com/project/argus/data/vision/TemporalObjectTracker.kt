package com.project.argus.data.vision

import com.project.argus.domain.model.BoundingBox2D
import com.project.argus.domain.model.DistanceCategory
import java.util.Locale
import kotlin.math.hypot

/**
 * Stabilizes object detections across frames:
 * - High-confidence (>= 0.55) or close (NEAR) detections confirm immediately on frame 1.
 * - Moderate detections (0.45..0.55 and not NEAR) require 2 matching frames within <= 750 ms.
 * Includes clock-rollback recovery and bounded track capacity to prevent memory leaks.
 */
class TemporalObjectTracker(
    private val confirmationWindowMs: Long = DEFAULT_CONFIRMATION_WINDOW_MS,
    private val immediateConfidenceThreshold: Float = DEFAULT_IMMEDIATE_CONFIDENCE,
    private val minConfidenceThreshold: Float = DEFAULT_MIN_CONFIDENCE,
    private val maxTrackedEntries: Int = DEFAULT_MAX_TRACKED_ENTRIES
) {
    private data class TrackedEntry(
        val label: String,
        val box: BoundingBox2D,
        val lastSeenMs: Long,
        val consecutiveHits: Int
    )

    private val lock = Any()
    private val trackedEntries = mutableListOf<TrackedEntry>()
    private var lastObservedTimestampMs: Long = Long.MIN_VALUE

    fun isConfirmed(
        label: String,
        box: BoundingBox2D,
        confidence: Float,
        distanceCategory: DistanceCategory,
        timestampMs: Long,
        frameWidth: Int,
        frameHeight: Int
    ): Boolean = synchronized(lock) {
        if (confidence < minConfidenceThreshold) {
            return false
        }

        pruneExpiredLocked(timestampMs)
        lastObservedTimestampMs = timestampMs

        val hits = recordHitLocked(
            label = label,
            box = box,
            timestampMs = timestampMs,
            frameWidth = frameWidth,
            frameHeight = frameHeight
        )

        if (confidence >= immediateConfidenceThreshold || distanceCategory == DistanceCategory.NEAR) {
            return true
        }

        return hits >= REQUIRED_CONSECUTIVE_HITS
    }

    fun pruneExpired(timestampMs: Long) {
        synchronized(lock) {
            pruneExpiredLocked(timestampMs)
            lastObservedTimestampMs = timestampMs
        }
    }

    fun reset() {
        synchronized(lock) {
            trackedEntries.clear()
            lastObservedTimestampMs = Long.MIN_VALUE
        }
    }

    internal fun activeTrackCount(): Int = synchronized(lock) {
        trackedEntries.size
    }

    private fun pruneExpiredLocked(timestampMs: Long) {
        if (lastObservedTimestampMs != Long.MIN_VALUE && timestampMs < lastObservedTimestampMs) {
            trackedEntries.clear()
            return
        }
        trackedEntries.removeAll { entry ->
            timestampMs < entry.lastSeenMs || timestampMs - entry.lastSeenMs > confirmationWindowMs
        }
    }

    private fun recordHitLocked(
        label: String,
        box: BoundingBox2D,
        timestampMs: Long,
        frameWidth: Int,
        frameHeight: Int
    ): Int {
        val normalizedLabel = label.trim().lowercase(Locale.ROOT)
        val diag = hypot(frameWidth.toDouble(), frameHeight.toDouble()).toFloat().coerceAtLeast(1f)

        val matchIdx = trackedEntries.indexOfFirst { entry ->
            entry.label == normalizedLabel &&
                timestampMs >= entry.lastSeenMs &&
                timestampMs - entry.lastSeenMs <= confirmationWindowMs &&
                isSameObject(entry.box, box, diag)
        }

        return if (matchIdx >= 0) {
            val prev = trackedEntries[matchIdx]
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
            if (trackedEntries.size >= maxTrackedEntries && trackedEntries.isNotEmpty()) {
                val oldestIdx = trackedEntries.indices.minByOrNull { trackedEntries[it].lastSeenMs } ?: 0
                trackedEntries.removeAt(oldestIdx)
            }
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
        if (a.iou(b) >= MIN_MATCH_IOU) return true
        val centerDist = hypot(
            (a.centerX() - b.centerX()).toDouble(),
            (a.centerY() - b.centerY()).toDouble()
        ).toFloat()
        return centerDist / frameDiagonal <= MAX_CENTER_SHIFT_RATIO
    }

    companion object {
        const val DEFAULT_CONFIRMATION_WINDOW_MS = 750L
        const val DEFAULT_IMMEDIATE_CONFIDENCE = 0.55f
        const val DEFAULT_MIN_CONFIDENCE = 0.45f
        const val DEFAULT_MAX_TRACKED_ENTRIES = 32
        private const val REQUIRED_CONSECUTIVE_HITS = 2
        private const val MIN_MATCH_IOU = 0.25f
        private const val MAX_CENTER_SHIFT_RATIO = 0.15f
    }
}
