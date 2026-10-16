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
 * armrest vs stairs suppression, priority alert hierarchy, and spatial heuristics.
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

        assertEquals("Caution, stairs ahead!", decision2.spokenText)
        assertTrue(decision2.isUrgent)
        assertTrue(decision2.validatedHazards.any { it is SpatialHazard.StairsAhead })
    }

    @Test
    fun `armrest detection immediately suppresses stairs accumulator`() {
        val stairsFrame = VisionFrameResult(
            spatialHazards = listOf(SpatialHazard.StairsAhead(confidence = 0.80f))
        )
        val armrestFrame = VisionFrameResult(
            spatialHazards = listOf(SpatialHazard.ArmrestDetected(confidence = 0.60f, zone = HorizontalZone.CENTER))
        )

        // Frame 1: Stairs (count = 1)
        useCase(stairsFrame)

        // Frame 2: Armrest arrives -> cancels stairs accumulator
        val decision = useCase(armrestFrame)

        assertEquals("Armrest ahead", decision.spokenText)
        assertFalse(decision.isUrgent)
        assertTrue(decision.validatedHazards.any { it is SpatialHazard.ArmrestDetected })
        assertFalse(decision.validatedHazards.any { it is SpatialHazard.StairsAhead })

        // Frame 3: Another stairs frame should NOT trigger alert because counter was reset to 0
        val decision3 = useCase(stairsFrame)
        assertNull("Stairs must be debounced again after reset", decision3.spokenText)
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
                    label = "Chair",
                    confidence = 0.9f,
                    boundingBox = BoundingBox2D(0f, 0f, 100f, 100f),
                    zone = HorizontalZone.CENTER,
                    estimatedDistanceCategory = DistanceCategory.NEAR
                )
            )
        )

        useCase(frame1)
        val decision = useCase(frame2)

        assertEquals("Caution, stairs ahead!", decision.spokenText)
        assertTrue(decision.isUrgent)
    }

    @Test
    fun `wall proximity triggers urgent warning when no stairs present`() {
        val frame = VisionFrameResult(
            spatialHazards = listOf(SpatialHazard.WallProximity(0.75f))
        )

        val decision = useCase(frame)

        assertEquals("Warning, wall directly ahead!", decision.spokenText)
        assertTrue(decision.isUrgent)
        assertTrue(decision.validatedHazards.any { it is SpatialHazard.WallProximity })
    }

    @Test
    fun `armrest directional alerts announce correct zone`() {
        val leftFrame = VisionFrameResult(
            spatialHazards = listOf(SpatialHazard.ArmrestDetected(0.6f, HorizontalZone.LEFT))
        )
        val rightFrame = VisionFrameResult(
            spatialHazards = listOf(SpatialHazard.ArmrestDetected(0.6f, HorizontalZone.RIGHT))
        )

        val leftDecision = useCase(leftFrame)
        assertEquals("Armrest on the left", leftDecision.spokenText)
        assertFalse(leftDecision.isUrgent)

        val rightDecision = useCase(rightFrame)
        assertEquals("Armrest on the right", rightDecision.spokenText)
        assertFalse(rightDecision.isUrgent)
    }

    @Test
    fun `door ahead triggers doorway prompt`() {
        val frame = VisionFrameResult(
            spatialHazards = listOf(SpatialHazard.DoorAhead(0.7f))
        )

        val decision = useCase(frame)

        assertEquals("Doorway ahead", decision.spokenText)
        assertFalse(decision.isUrgent)
    }

    // 3. Obstacle Collision Warnings in Center Zone

    @Test
    fun `near obstacle in center zone triggers careful prompt`() {
        val frame = VisionFrameResult(
            obstacles = listOf(
                DetectedObstacle(
                    id = 1,
                    label = "Table",
                    confidence = 0.85f,
                    boundingBox = BoundingBox2D(300f, 200f, 700f, 800f),
                    zone = HorizontalZone.CENTER,
                    estimatedDistanceCategory = DistanceCategory.NEAR
                )
            )
        )

        val decision = useCase(frame)

        assertEquals("Careful, table right ahead", decision.spokenText)
        assertFalse(decision.isUrgent)
    }

    @Test
    fun `medium distance obstacle in center zone triggers ahead prompt`() {
        val frame = VisionFrameResult(
            obstacles = listOf(
                DetectedObstacle(
                    id = 1,
                    label = "Person",
                    confidence = 0.85f,
                    boundingBox = BoundingBox2D(350f, 200f, 650f, 500f),
                    zone = HorizontalZone.CENTER,
                    estimatedDistanceCategory = DistanceCategory.MEDIUM
                )
            )
        )

        val decision = useCase(frame)

        assertEquals("Person ahead", decision.spokenText)
        assertFalse(decision.isUrgent)
    }

    @Test
    fun `far or flank obstacles do not trigger spoken alerts`() {
        val frame = VisionFrameResult(
            obstacles = listOf(
                DetectedObstacle(
                    id = 1,
                    label = "Pillar",
                    confidence = 0.85f,
                    boundingBox = BoundingBox2D(50f, 200f, 150f, 500f),
                    zone = HorizontalZone.LEFT,
                    estimatedDistanceCategory = DistanceCategory.NEAR
                ),
                DetectedObstacle(
                    id = 2,
                    label = "Tree",
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
