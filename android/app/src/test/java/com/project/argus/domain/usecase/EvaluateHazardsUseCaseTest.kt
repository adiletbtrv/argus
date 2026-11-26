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

class EvaluateHazardsUseCaseTest {

    private lateinit var useCase: EvaluateHazardsUseCase

    @Before
    fun setUp() {
        useCase = EvaluateHazardsUseCase()
    }

    @Test
    fun stairs_singleFrameIsDebounced_twoFramesTriggerUrgentAlert() {
        val frame = VisionFrameResult(
            spatialHazards = listOf(SpatialHazard.StairsAhead(0.85f))
        )

        val decision1 = useCase(frame, currentTimeMs = 1000L)
        assertNull(decision1.spokenText)

        val decision2 = useCase(frame, currentTimeMs = 1180L)
        assertEquals("Осторожно, впереди лестница!", decision2.spokenText)
        assertTrue(decision2.isUrgent)
    }

    @Test
    fun lowBranch_debouncedAndTriggersUrgentSafetyAlert() {
        val frame = VisionFrameResult(
            spatialHazards = listOf(SpatialHazard.LowBranchAhead(0.80f))
        )

        val decision1 = useCase(frame, currentTimeMs = 1000L)
        assertNull(decision1.spokenText)

        val decision2 = useCase(frame, currentTimeMs = 1180L)
        assertEquals("Осторожно, впереди ветка!", decision2.spokenText)
        assertTrue(decision2.isUrgent)
    }

    @Test
    fun wallProximity_triggersUrgentWarning() {
        val frame = VisionFrameResult(
            spatialHazards = listOf(SpatialHazard.WallProximity(0.80f))
        )
        val decision = useCase(frame, currentTimeMs = 1000L)
        assertEquals("Внимание, стена прямо по курсу!", decision.spokenText)
        assertTrue(decision.isUrgent)
    }

    @Test
    fun nearCenterObstacle_takesPriorityOverDoorway() {
        val frame = VisionFrameResult(
            spatialHazards = listOf(SpatialHazard.DoorAhead(0.75f)),
            obstacles = listOf(
                DetectedObstacle(
                    id = 0,
                    label = "chair",
                    confidence = 0.90f,
                    boundingBox = BoundingBox2D(200f, 200f, 800f, 800f),
                    zone = HorizontalZone.CENTER,
                    estimatedDistanceCategory = DistanceCategory.NEAR,
                    dangerPriority = 2
                )
            )
        )

        val decision = useCase(frame, currentTimeMs = 1000L)
        assertEquals("Вплотную, стул!", decision.spokenText)
        assertTrue(decision.isUrgent)
        assertEquals(3, decision.priority)
    }

    @Test
    fun doorway_takesPriorityOverMediumCenterObstacle() {
        val frame = VisionFrameResult(
            spatialHazards = listOf(SpatialHazard.DoorAhead(0.75f)),
            obstacles = listOf(
                DetectedObstacle(
                    id = 0,
                    label = "chair",
                    confidence = 0.85f,
                    boundingBox = BoundingBox2D(350f, 300f, 650f, 700f),
                    zone = HorizontalZone.CENTER,
                    estimatedDistanceCategory = DistanceCategory.MEDIUM,
                    dangerPriority = 2
                )
            )
        )

        val decision = useCase(frame, currentTimeMs = 1000L)
        assertEquals("Впереди дверной проем", decision.spokenText)
        assertFalse(decision.isUrgent)
        assertEquals(4, decision.priority)
    }

    @Test
    fun mediumCenterObstacle_suggestsBypassDirection() {
        val frame = VisionFrameResult(
            obstacles = listOf(
                DetectedObstacle(
                    id = 0,
                    label = "person",
                    confidence = 0.85f,
                    boundingBox = BoundingBox2D(350f, 300f, 650f, 700f),
                    zone = HorizontalZone.CENTER,
                    estimatedDistanceCategory = DistanceCategory.MEDIUM,
                    dangerPriority = 3
                )
            )
        )

        val decision = useCase(frame, currentTimeMs = 1000L)
        assertEquals("Прямо человек, обойдите слева", decision.spokenText)
        assertFalse(decision.isUrgent)
    }

    @Test
    fun sceneObject_announcesVerifiedNonCocoObject() {
        val frame = VisionFrameResult(
            spatialHazards = listOf(SpatialHazard.SceneObjectAhead("wheel", 0.78f))
        )

        val decision = useCase(frame, currentTimeMs = 1000L)
        assertEquals("Впереди колесо", decision.spokenText)
        assertFalse(decision.isUrgent)
    }
}
