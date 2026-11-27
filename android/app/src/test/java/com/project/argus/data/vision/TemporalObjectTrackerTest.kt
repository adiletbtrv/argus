package com.project.argus.data.vision

import com.project.argus.domain.model.BoundingBox2D
import com.project.argus.domain.model.DistanceCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TemporalObjectTrackerTest {

    private lateinit var tracker: TemporalObjectTracker

    @Before
    fun setUp() {
        tracker = TemporalObjectTracker()
    }

    @Test
    fun `confident detection at or above 0_55 confirms immediately on first frame`() {
        val box = BoundingBox2D(200f, 200f, 450f, 500f)

        val confirmed = tracker.isConfirmed(
            label = "person",
            box = box,
            confidence = 0.55f,
            distanceCategory = DistanceCategory.MEDIUM,
            timestampMs = 1000L,
            frameWidth = 640,
            frameHeight = 480
        )

        assertTrue(confirmed)
    }

    @Test
    fun `near obstacle confirms immediately on first frame even with moderate confidence`() {
        val nearBox = BoundingBox2D(100f, 80f, 540f, 440f)

        val confirmed = tracker.isConfirmed(
            label = "chair",
            box = nearBox,
            confidence = 0.46f,
            distanceCategory = DistanceCategory.NEAR,
            timestampMs = 1000L,
            frameWidth = 640,
            frameHeight = 480
        )

        assertTrue("NEAR obstacles must have zero confirmation latency", confirmed)
    }

    @Test
    fun `weak non near detection requires two matching frames within 750ms window`() {
        val box1 = BoundingBox2D(220f, 160f, 420f, 360f)
        val box2 = BoundingBox2D(230f, 165f, 430f, 365f)

        val firstFrame = tracker.isConfirmed(
            label = "chair",
            box = box1,
            confidence = 0.48f,
            distanceCategory = DistanceCategory.MEDIUM,
            timestampMs = 1000L,
            frameWidth = 640,
            frameHeight = 480
        )
        assertFalse(firstFrame)

        val secondFrameAtBoundary = tracker.isConfirmed(
            label = "chair",
            box = box2,
            confidence = 0.49f,
            distanceCategory = DistanceCategory.MEDIUM,
            timestampMs = 1750L,
            frameWidth = 640,
            frameHeight = 480
        )
        assertTrue(secondFrameAtBoundary)
    }

    @Test
    fun `weak detection after 750ms window expires starts fresh and requires another hit`() {
        val box = BoundingBox2D(200f, 150f, 400f, 350f)

        assertFalse(
            tracker.isConfirmed(
                label = "bench",
                box = box,
                confidence = 0.47f,
                distanceCategory = DistanceCategory.MEDIUM,
                timestampMs = 1000L,
                frameWidth = 640,
                frameHeight = 480
            )
        )

        // 751 ms later (> 750 ms window): expired, treated as hit 1 again
        assertFalse(
            tracker.isConfirmed(
                label = "bench",
                box = box,
                confidence = 0.47f,
                distanceCategory = DistanceCategory.MEDIUM,
                timestampMs = 1751L,
                frameWidth = 640,
                frameHeight = 480
            )
        )

        // Follow-up within 750 ms confirms
        assertTrue(
            tracker.isConfirmed(
                label = "bench",
                box = box,
                confidence = 0.48f,
                distanceCategory = DistanceCategory.MEDIUM,
                timestampMs = 1930L,
                frameWidth = 640,
                frameHeight = 480
            )
        )
    }

    @Test
    fun `weak detection at distant position or different class does not confirm prior track`() {
        val leftBox = BoundingBox2D(20f, 150f, 180f, 350f)
        val rightBox = BoundingBox2D(460f, 150f, 620f, 350f)

        assertFalse(
            tracker.isConfirmed(
                label = "suitcase",
                box = leftBox,
                confidence = 0.48f,
                distanceCategory = DistanceCategory.MEDIUM,
                timestampMs = 1000L,
                frameWidth = 640,
                frameHeight = 480
            )
        )

        // Same class, far away on opposite side of frame -> not same object
        assertFalse(
            tracker.isConfirmed(
                label = "suitcase",
                box = rightBox,
                confidence = 0.48f,
                distanceCategory = DistanceCategory.MEDIUM,
                timestampMs = 1180L,
                frameWidth = 640,
                frameHeight = 480
            )
        )

        // Same position as leftBox, but different class -> not same object
        assertFalse(
            tracker.isConfirmed(
                label = "backpack",
                box = leftBox,
                confidence = 0.48f,
                distanceCategory = DistanceCategory.MEDIUM,
                timestampMs = 1200L,
                frameWidth = 640,
                frameHeight = 480
            )
        )
    }

    @Test
    fun `clock rollback reanchors state instead of locking out subsequent confirmations`() {
        val box = BoundingBox2D(220f, 160f, 420f, 360f)

        assertFalse(
            tracker.isConfirmed(
                label = "couch",
                box = box,
                confidence = 0.48f,
                distanceCategory = DistanceCategory.MEDIUM,
                timestampMs = 50_000L,
                frameWidth = 640,
                frameHeight = 480
            )
        )

        // Clock rolls backward from 50_000L to 1_000L: re-anchors cleanly without permanent lockout
        assertFalse(
            tracker.isConfirmed(
                label = "couch",
                box = box,
                confidence = 0.48f,
                distanceCategory = DistanceCategory.MEDIUM,
                timestampMs = 1_000L,
                frameWidth = 640,
                frameHeight = 480
            )
        )

        // Next frame at 1_180L confirms normally
        assertTrue(
            tracker.isConfirmed(
                label = "couch",
                box = box,
                confidence = 0.49f,
                distanceCategory = DistanceCategory.MEDIUM,
                timestampMs = 1_180L,
                frameWidth = 640,
                frameHeight = 480
            )
        )
    }

    @Test
    fun `pruneExpired and max capacity prevent unbounded track growth during long session`() {
        val boundedTracker = TemporalObjectTracker(maxTrackedEntries = 16)

        // Simulate 500 frames over a walk with varying weak detections
        for (i in 0 until 500) {
            val timestamp = i * 180L
            boundedTracker.pruneExpired(timestamp)
            for (j in 0 until 5) {
                val x = (j * 110).toFloat()
                boundedTracker.isConfirmed(
                    label = "chair",
                    box = BoundingBox2D(x, 100f, x + 90f, 280f),
                    confidence = 0.47f,
                    distanceCategory = DistanceCategory.FAR,
                    timestampMs = timestamp,
                    frameWidth = 640,
                    frameHeight = 480
                )
            }
            assertTrue(
                "Track count must never exceed maxTrackedEntries",
                boundedTracker.activeTrackCount() <= 16
            )
        }

        // After 1 second of empty scene, all expired tracks are cleaned up
        boundedTracker.pruneExpired(500 * 180L + 1000L)
        assertEquals(0, boundedTracker.activeTrackCount())
    }

    @Test
    fun `reset clears pending tracks immediately`() {
        val box = BoundingBox2D(220f, 160f, 420f, 360f)

        assertFalse(
            tracker.isConfirmed(
                label = "potted plant",
                box = box,
                confidence = 0.48f,
                distanceCategory = DistanceCategory.MEDIUM,
                timestampMs = 1000L,
                frameWidth = 640,
                frameHeight = 480
            )
        )
        assertEquals(1, tracker.activeTrackCount())

        tracker.reset()
        assertEquals(0, tracker.activeTrackCount())

        // After reset, next weak detection is first frame again (not confirmed)
        assertFalse(
            tracker.isConfirmed(
                label = "potted plant",
                box = box,
                confidence = 0.48f,
                distanceCategory = DistanceCategory.MEDIUM,
                timestampMs = 1180L,
                frameWidth = 640,
                frameHeight = 480
            )
        )
    }
}
