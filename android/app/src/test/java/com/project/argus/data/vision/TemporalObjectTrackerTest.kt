package com.project.argus.data.vision

import com.project.argus.domain.model.BoundingBox2D
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TemporalObjectTrackerTest {

    private lateinit var tracker: TemporalObjectTracker

    @Before
    fun setUp() {
        tracker = TemporalObjectTracker(confirmationWindowMs = 800L)
    }

    @Test
    fun isConfirmed_highConfidenceObject_isConfirmedImmediately() {
        val box = BoundingBox2D(100f, 100f, 300f, 300f)
        val confirmed = tracker.isConfirmed(
            label = "person",
            box = box,
            confidence = 0.75f,
            immediateThreshold = 0.62f,
            timestampMs = 1000L,
            frameWidth = 1000,
            frameHeight = 1000
        )
        assertTrue(confirmed)
    }

    @Test
    fun isConfirmed_moderateConfidence_requiresTwoConsecutiveFrames() {
        val box1 = BoundingBox2D(100f, 100f, 300f, 300f)
        val box2 = BoundingBox2D(105f, 102f, 305f, 302f)

        val frame1 = tracker.isConfirmed(
            label = "cup",
            box = box1,
            confidence = 0.42f,
            immediateThreshold = 0.48f,
            timestampMs = 1000L,
            frameWidth = 1000,
            frameHeight = 1000
        )
        assertFalse(frame1)

        val frame2 = tracker.isConfirmed(
            label = "cup",
            box = box2,
            confidence = 0.44f,
            immediateThreshold = 0.48f,
            timestampMs = 1180L,
            frameWidth = 1000,
            frameHeight = 1000
        )
        assertTrue(frame2)
    }

    @Test
    fun isConfirmed_expiredWindow_resetsConsecutiveCounter() {
        val box = BoundingBox2D(100f, 100f, 300f, 300f)

        tracker.isConfirmed(
            label = "ball",
            box = box,
            confidence = 0.40f,
            immediateThreshold = 0.48f,
            timestampMs = 1000L,
            frameWidth = 1000,
            frameHeight = 1000
        )

        // Window expired (gap > 800 ms)
        val afterExpiry = tracker.isConfirmed(
            label = "ball",
            box = box,
            confidence = 0.40f,
            immediateThreshold = 0.48f,
            timestampMs = 2100L,
            frameWidth = 1000,
            frameHeight = 1000
        )
        assertFalse(afterExpiry)
    }
}
