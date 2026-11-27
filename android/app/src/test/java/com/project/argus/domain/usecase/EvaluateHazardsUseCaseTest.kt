package com.project.argus.domain.usecase

import com.project.argus.domain.model.BoundingBox2D
import com.project.argus.domain.model.DetectedObstacle
import com.project.argus.domain.model.DistanceCategory
import com.project.argus.domain.model.HorizontalZone
import com.project.argus.domain.model.SpatialHazard
import com.project.argus.domain.model.VisionFrameResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [EvaluateHazardsUseCase] verifying temporal debouncing,
 * priority alert hierarchy, danger-priority gating, Russian alerts, and corridor heuristics.
 */
class EvaluateHazardsUseCaseTest {

    private lateinit var useCase: EvaluateHazardsUseCase

    @Before
    fun setUp() {
        useCase = EvaluateHazardsUseCase()
    }

    // 1. Temporal Debouncing & Safety Glint Prevention (Stairs)

    @Test
    fun `single frame stairs is debounced and does not trigger speech`() {
        val singleFrame = VisionFrameResult(
            spatialHazards = listOf(SpatialHazard.StairsAhead(confidence = 0.85f))
        )

        val decision = useCase(singleFrame)

        assertNull("Single frame of stairs must not trigger alert", decision.spokenText)
        assertFalse(decision.isUrgent)
        assertTrue("Stairs hazard must not be validated on single frame", decision.validatedHazards.isEmpty())
    }

    @Test
    fun `consecutive frames of stairs trigger urgent safety alert`() {
        val frame1 = VisionFrameResult(
            spatialHazards = listOf(SpatialHazard.StairsAhead(confidence = 0.85f))
        )
        val frame2 = VisionFrameResult(
            spatialHazards = listOf(SpatialHazard.StairsAhead(confidence = 0.88f))
        )

        // First frame debounced
        useCase(frame1)

        // Immediate second frame confirms hazard
        val decision2 = useCase(frame2)

        assertEquals("Осторожно, впереди лестница!", decision2.spokenText)
        assertTrue(decision2.isUrgent)
        assertTrue(decision2.validatedHazards.any { it is SpatialHazard.StairsAhead })
    }

    @Test
    fun `intervening furniture suppressed frame resets stairs accumulator`() {
        val stairsFrame = VisionFrameResult(
            spatialHazards = listOf(SpatialHazard.StairsAhead(confidence = 0.80f))
        )
        val suppressedFurnitureFrame = VisionFrameResult(
            spatialHazards = emptyList()
        )

        // Frame 1: Stairs (count = 1)
        useCase(stairsFrame)

        // Frame 2: Furniture suppressed stairs -> resets accumulator to 0 without armrest speech
        val decision2 = useCase(suppressedFurnitureFrame)
        assertNull(decision2.spokenText)
        assertFalse(decision2.isUrgent)
        assertTrue(decision2.validatedHazards.isEmpty())

        // Frame 3: Single stairs glint again must NOT trigger alert because counter was reset
        val decision3 = useCase(stairsFrame)
        assertNull("Stairs must be debounced again after non-consecutive frame", decision3.spokenText)
    }

    // 2. Alert Priority Hierarchy Tests

    @Test
    fun `urgent stairs alert takes precedence over wall proximity and center obstacles`() {
        val frame1 = VisionFrameResult(
            spatialHazards = listOf(
                SpatialHazard.StairsAhead(0.9f),
                SpatialHazard.WallProximity(0.8f)
            )
        )
        val frame2 = VisionFrameResult(
            spatialHazards = listOf(
                SpatialHazard.StairsAhead(0.9f),
                SpatialHazard.WallProximity(0.8f)
            ),
            obstacles = listOf(
                DetectedObstacle(
                    id = 0,
                    label = "chair",
                    confidence = 0.9f,
                    boundingBox = BoundingBox2D(0f, 0f, 100f, 100f),
                    zone = HorizontalZone.CENTER,
                    estimatedDistanceCategory = DistanceCategory.NEAR
                )
            )
        )

        useCase(frame1)
        val decision = useCase(frame2)

        assertEquals("Осторожно, впереди лестница!", decision.spokenText)
        assertTrue(decision.isUrgent)
    }

    @Test
    fun `wall proximity triggers urgent warning when no stairs present`() {
        val frame = VisionFrameResult(
            spatialHazards = listOf(SpatialHazard.WallProximity(0.75f))
        )

        val decision = useCase(frame)

        assertEquals("Внимание, стена прямо по курсу!", decision.spokenText)
        assertTrue(decision.isUrgent)
        assertTrue(decision.validatedHazards.any { it is SpatialHazard.WallProximity })
    }

    @Test
    fun `door ahead triggers doorway prompt`() {
        val frame = VisionFrameResult(
            spatialHazards = listOf(SpatialHazard.DoorAhead(0.7f))
        )

        val decision = useCase(frame)

        assertEquals("Впереди дверной проем", decision.spokenText)
        assertFalse(decision.isUrgent)
    }

    @Test
    fun `near center obstacle takes priority over doorway`() {
        val frame = VisionFrameResult(
            spatialHazards = listOf(
                SpatialHazard.DoorAhead(0.8f)
            ),
            obstacles = listOf(
                DetectedObstacle(
                    id = 1,
                    label = "dining table",
                    confidence = 0.9f,
                    boundingBox = BoundingBox2D(200f, 200f, 800f, 800f),
                    zone = HorizontalZone.CENTER,
                    estimatedDistanceCategory = DistanceCategory.NEAR
                )
            )
        )

        val decision = useCase(frame)

        assertEquals("Вплотную, стол!", decision.spokenText)
        assertTrue(decision.isUrgent)
        assertEquals(3, decision.priority)
    }

    @Test
    fun `door ahead takes priority over medium center obstacle`() {
        val frame = VisionFrameResult(
            spatialHazards = listOf(SpatialHazard.DoorAhead(0.75f)),
            obstacles = listOf(
                DetectedObstacle(
                    id = 1,
                    label = "chair",
                    confidence = 0.9f,
                    boundingBox = BoundingBox2D(350f, 250f, 650f, 750f),
                    zone = HorizontalZone.CENTER,
                    estimatedDistanceCategory = DistanceCategory.MEDIUM
                )
            )
        )

        val decision = useCase(frame)

        assertEquals("Впереди дверной проем", decision.spokenText)
        assertFalse(decision.isUrgent)
        assertEquals(4, decision.priority)
    }

    @Test
    fun `low risk priority 1 obstacle in near center is downgraded to non urgent priority 5`() {
        val ballFrame = VisionFrameResult(
            obstacles = listOf(
                DetectedObstacle(
                    id = 0,
                    label = "Ball",
                    confidence = 0.88f,
                    boundingBox = BoundingBox2D(250f, 250f, 750f, 750f),
                    zone = HorizontalZone.CENTER,
                    estimatedDistanceCategory = DistanceCategory.NEAR
                )
            )
        )
        val handbagFrame = VisionFrameResult(
            obstacles = listOf(
                DetectedObstacle(
                    id = 0,
                    label = "handbag",
                    confidence = 0.86f,
                    boundingBox = BoundingBox2D(250f, 250f, 750f, 750f),
                    zone = HorizontalZone.CENTER,
                    estimatedDistanceCategory = DistanceCategory.NEAR
                )
            )
        )

        val ballDecision = useCase(ballFrame)
        assertEquals("Прямо мяч, обойдите слева", ballDecision.spokenText)
        assertFalse("sports ball must never trigger isUrgent = true", ballDecision.isUrgent)
        assertEquals(5, ballDecision.priority)

        val bagDecision = useCase(handbagFrame)
        assertEquals("Прямо сумка, обойдите слева", bagDecision.spokenText)
        assertFalse("handbag must never trigger isUrgent = true", bagDecision.isUrgent)
        assertEquals(5, bagDecision.priority)
    }

    @Test
    fun `door ahead priority 4 takes precedence over near low risk priority 1 item`() {
        val frame = VisionFrameResult(
            spatialHazards = listOf(SpatialHazard.DoorAhead(0.78f)),
            obstacles = listOf(
                DetectedObstacle(
                    id = 0,
                    label = "umbrella",
                    confidence = 0.90f,
                    boundingBox = BoundingBox2D(250f, 200f, 750f, 800f),
                    zone = HorizontalZone.CENTER,
                    estimatedDistanceCategory = DistanceCategory.NEAR
                )
            )
        )

        val decision = useCase(frame)

        assertEquals("Впереди дверной проем", decision.spokenText)
        assertFalse(decision.isUrgent)
        assertEquals(4, decision.priority)
    }

    @Test
    fun `person in near center takes priority over chair in near center`() {
        val frame = VisionFrameResult(
            obstacles = listOf(
                DetectedObstacle(
                    id = 0,
                    label = "chair",
                    confidence = 0.95f,
                    boundingBox = BoundingBox2D(250f, 200f, 750f, 800f),
                    zone = HorizontalZone.CENTER,
                    estimatedDistanceCategory = DistanceCategory.NEAR
                ),
                DetectedObstacle(
                    id = 1,
                    label = "person",
                    confidence = 0.80f,
                    boundingBox = BoundingBox2D(260f, 150f, 740f, 850f),
                    zone = HorizontalZone.CENTER,
                    estimatedDistanceCategory = DistanceCategory.NEAR
                )
            )
        )

        val decision = useCase(frame)

        assertEquals("Вплотную, человек!", decision.spokenText)
        assertTrue(decision.isUrgent)
        assertEquals(3, decision.priority)
    }

    // 3. Obstacle Collision Warnings in Center Zone

    @Test
    fun `near obstacle in center zone triggers close urgent warning`() {
        val frame = VisionFrameResult(
            obstacles = listOf(
                DetectedObstacle(
                    id = 1,
                    label = "table",
                    confidence = 0.85f,
                    boundingBox = BoundingBox2D(300f, 200f, 700f, 800f),
                    zone = HorizontalZone.CENTER,
                    estimatedDistanceCategory = DistanceCategory.NEAR
                )
            )
        )

        val decision = useCase(frame)

        assertEquals("Вплотную, стол!", decision.spokenText)
        assertTrue(decision.isUrgent)
    }

    @Test
    fun `medium distance obstacle in center zone with left free recommends bypass left`() {
        val frame = VisionFrameResult(
            obstacles = listOf(
                DetectedObstacle(
                    id = 1,
                    label = "person",
                    confidence = 0.85f,
                    boundingBox = BoundingBox2D(350f, 200f, 650f, 500f),
                    zone = HorizontalZone.CENTER,
                    estimatedDistanceCategory = DistanceCategory.MEDIUM
                )
            )
        )

        val decision = useCase(frame)

        assertEquals("Прямо человек, обойдите слева", decision.spokenText)
        assertFalse(decision.isUrgent)
    }

    @Test
    fun `medium distance obstacle in center zone with left blocked recommends bypass right`() {
        val frame = VisionFrameResult(
            obstacles = listOf(
                DetectedObstacle(
                    id = 1,
                    label = "person",
                    confidence = 0.85f,
                    boundingBox = BoundingBox2D(350f, 200f, 650f, 500f),
                    zone = HorizontalZone.CENTER,
                    estimatedDistanceCategory = DistanceCategory.MEDIUM
                ),
                DetectedObstacle(
                    id = 2,
                    label = "chair",
                    confidence = 0.85f,
                    boundingBox = BoundingBox2D(50f, 200f, 250f, 500f),
                    zone = HorizontalZone.LEFT,
                    estimatedDistanceCategory = DistanceCategory.MEDIUM
                )
            )
        )

        val decision = useCase(frame)

        assertEquals("Прямо человек, обойдите справа", decision.spokenText)
        assertFalse(decision.isUrgent)
    }

    @Test
    fun `far or flank obstacles do not trigger spoken alerts`() {
        val frame = VisionFrameResult(
            obstacles = listOf(
                DetectedObstacle(
                    id = 1,
                    label = "backpack",
                    confidence = 0.85f,
                    boundingBox = BoundingBox2D(50f, 200f, 150f, 500f),
                    zone = HorizontalZone.LEFT,
                    estimatedDistanceCategory = DistanceCategory.NEAR
                ),
                DetectedObstacle(
                    id = 2,
                    label = "bench",
                    confidence = 0.85f,
                    boundingBox = BoundingBox2D(400f, 200f, 500f, 300f),
                    zone = HorizontalZone.CENTER,
                    estimatedDistanceCategory = DistanceCategory.FAR
                )
            )
        )

        val decision = useCase(frame)

        assertNull("Flank and far obstacles should not trigger routine alerts", decision.spokenText)
    }

    @Test
    fun `reset clears consecutive stairs state`() {
        val stairsFrame = VisionFrameResult(
            spatialHazards = listOf(SpatialHazard.StairsAhead(confidence = 0.85f))
        )

        // First frame accumulated
        useCase(stairsFrame)

        // Explicit reset
        useCase.reset()

        // Following frame should be treated as first frame (debounced)
        val decision = useCase(stairsFrame)
        assertNull("Stairs must be debounced after reset", decision.spokenText)
    }
}
