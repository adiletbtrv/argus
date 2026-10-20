package com.project.argus.domain.usecase

import com.project.argus.domain.model.BoundingBox2D
import com.project.argus.domain.model.CaneDirection
import com.project.argus.domain.model.CaneTelemetry
import com.project.argus.domain.model.DetectedObstacle
import com.project.argus.domain.model.DistanceCategory
import com.project.argus.domain.model.HorizontalZone
import com.project.argus.domain.model.VisionFrameResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class EvaluateSensorFusionUseCaseTest {

    private lateinit var useCase: EvaluateSensorFusionUseCase

    @Before
    fun setUp() {
        useCase = EvaluateSensorFusionUseCase()
    }

    private fun createObstacle(
        label: String,
        zone: HorizontalZone,
        distanceCategory: DistanceCategory
    ): DetectedObstacle {
        return DetectedObstacle(
            id = 1,
            label = label,
            confidence = 0.9f,
            boundingBox = BoundingBox2D(0f, 0f, 100f, 100f),
            zone = zone,
            estimatedDistanceCategory = distanceCategory
        )
    }

    @Test
    fun invoke_criticalSonarDistance_triggersUrgentAlertImmediately() {
        val frame = VisionFrameResult()
        val telemetry = CaneTelemetry(distanceCm = 28, direction = CaneDirection.CENTER)

        val decision = useCase(frame, telemetry)

        assertTrue(decision.isUrgent)
        assertEquals("Внимание, близко препятствие: 28 см!", decision.spokenText)
        assertEquals("STOP", decision.recommendedAction)
    }

    @Test
    fun invoke_staleTelemetry_doesNotTriggerCriticalAlert() {
        val frame = VisionFrameResult()
        val staleTelemetry = CaneTelemetry(
            distanceCm = 20,
            direction = CaneDirection.CENTER,
            timestamp = System.currentTimeMillis() - 2000L
        )

        val decision = useCase(frame, staleTelemetry)

        assertFalse(decision.isUrgent)
        assertNull(decision.spokenText)
    }

    @Test
    fun invoke_sideConflictLeft_matchesCaneDirectionAndCameraObstacle() {
        val frame = VisionFrameResult(
            obstacles = listOf(
                createObstacle("chair", HorizontalZone.LEFT, DistanceCategory.MEDIUM)
            )
        )
        val telemetry = CaneTelemetry(distanceCm = 60, direction = CaneDirection.LEFT)

        val decision = useCase(frame, telemetry)

        assertTrue(decision.isUrgent)
        assertEquals("Осторожно, слева преграда!", decision.spokenText)
        assertEquals("AVOID_LEFT", decision.recommendedAction)
    }

    @Test
    fun invoke_sideConflictRight_matchesCaneDirectionAndCameraObstacle() {
        val frame = VisionFrameResult(
            obstacles = listOf(
                createObstacle("car", HorizontalZone.RIGHT, DistanceCategory.MEDIUM)
            )
        )
        val telemetry = CaneTelemetry(distanceCm = 60, direction = CaneDirection.RIGHT)

        val decision = useCase(frame, telemetry)

        assertTrue(decision.isUrgent)
        assertEquals("Осторожно, справа преграда!", decision.spokenText)
        assertEquals("AVOID_RIGHT", decision.recommendedAction)
    }

    @Test
    fun invoke_centerObstacle_withLeftFree_recommendsBypassLeft() {
        val frame = VisionFrameResult(
            obstacles = listOf(
                createObstacle("chair", HorizontalZone.CENTER, DistanceCategory.MEDIUM),
                createObstacle("suitcase", HorizontalZone.RIGHT, DistanceCategory.MEDIUM)
            )
        )
        val telemetry = CaneTelemetry(distanceCm = 70, direction = CaneDirection.CENTER)

        val decision = useCase(frame, telemetry)

        assertFalse(decision.isUrgent)
        assertEquals("Прямо стул, обойдите слева", decision.spokenText)
        assertEquals("BYPASS_LEFT", decision.recommendedAction)
    }

    @Test
    fun invoke_centerObstacle_withRightFree_recommendsBypassRight() {
        val frame = VisionFrameResult(
            obstacles = listOf(
                createObstacle("person", HorizontalZone.CENTER, DistanceCategory.MEDIUM),
                createObstacle("backpack", HorizontalZone.LEFT, DistanceCategory.MEDIUM)
            )
        )
        val telemetry = CaneTelemetry(distanceCm = 70, direction = CaneDirection.CENTER)

        val decision = useCase(frame, telemetry)

        assertFalse(decision.isUrgent)
        assertEquals("Прямо человек, обойдите справа", decision.spokenText)
        assertEquals("BYPASS_RIGHT", decision.recommendedAction)
    }

    @Test
    fun invoke_centerObstacleNearDistance_triggersUrgentCloseWarning() {
        val frame = VisionFrameResult(
            obstacles = listOf(
                createObstacle("chair", HorizontalZone.CENTER, DistanceCategory.NEAR)
            )
        )
        val telemetry = CaneTelemetry(distanceCm = 70, direction = CaneDirection.CENTER)

        val decision = useCase(frame, telemetry)

        assertTrue(decision.isUrgent)
        assertEquals("Вплотную, стул!", decision.spokenText)
        assertEquals("STOP", decision.recommendedAction)
    }
}
