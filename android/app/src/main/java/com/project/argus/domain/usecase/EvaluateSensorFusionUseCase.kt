package com.project.argus.domain.usecase

import com.project.argus.domain.model.CaneDirection
import com.project.argus.domain.model.CaneTelemetry
import com.project.argus.domain.model.DistanceCategory
import com.project.argus.domain.model.HorizontalZone
import com.project.argus.domain.model.VisionFrameResult

data class SensorFusionDecision(
    val spokenText: String?,
    val isUrgent: Boolean,
    val caneTelemetry: CaneTelemetry? = null,
    val recommendedAction: String? = null,
    val priority: Int = Int.MAX_VALUE
)

/**
 * Sensor Fusion Use Case combining ultrasonic & IMU telemetry from the physical cane
 * with real-time visual obstacles from the smartphone camera.
 *
 * Rules (unified hierarchy):
 * 1. Priority 1 - Critical Sonar (< 35 cm): Urgent alert with queue flush.
 * 2. Priority 2 - Cane Direction + Camera Side collision:
 *    If Cane DIR == LEFT and Camera detects obstacle on LEFT -> "Осторожно, слева преграда!"
 *    If Cane DIR == RIGHT and Camera detects obstacle on RIGHT -> "Осторожно, справа преграда!"
 * Center obstacles and corridor bypass are handled in AlertStrategy / EvaluateHazardsUseCase (Priorities 3 and 5).
 */
class EvaluateSensorFusionUseCase {

    operator fun invoke(
        frameResult: VisionFrameResult,
        caneTelemetry: CaneTelemetry?,
        currentTimeMs: Long = System.currentTimeMillis()
    ): SensorFusionDecision {
        if (caneTelemetry == null ||
            currentTimeMs - caneTelemetry.timestamp >= TELEMETRY_FRESHNESS_WINDOW_MS
        ) {
            return SensorFusionDecision(
                spokenText = null,
                isUrgent = false,
                caneTelemetry = caneTelemetry
            )
        }

        // Priority 1: Critical Cane Sonar Threshold (< 35 cm)
        if (caneTelemetry.isCriticalDistance) {
            return SensorFusionDecision(
                spokenText = "Внимание, близко препятствие: ${caneTelemetry.distanceCm} см!",
                isUrgent = true,
                caneTelemetry = caneTelemetry,
                recommendedAction = "STOP",
                priority = PRIORITY_CRITICAL_SONAR
            )
        }

        // Filter valid obstacles by unified distance scale (FAR is tracked silently)
        val relevantObstacles = frameResult.obstacles.filter { obs ->
            obs.estimatedDistanceCategory != DistanceCategory.FAR
        }

        // Priority 2: Sensor Fusion (Cane IMU direction + Camera Zone match)
        val sideCollision = when (caneTelemetry.direction) {
            CaneDirection.LEFT -> relevantObstacles
                .firstOrNull { it.zone == HorizontalZone.LEFT }
                ?.let { "Осторожно, слева преграда!" to "AVOID_LEFT" }
            CaneDirection.RIGHT -> relevantObstacles
                .firstOrNull { it.zone == HorizontalZone.RIGHT }
                ?.let { "Осторожно, справа преграда!" to "AVOID_RIGHT" }
            CaneDirection.CENTER -> null
        }

        if (sideCollision != null) {
            return SensorFusionDecision(
                spokenText = sideCollision.first,
                isUrgent = true,
                caneTelemetry = caneTelemetry,
                recommendedAction = sideCollision.second,
                priority = PRIORITY_SIDE_FUSION
            )
        }

        return SensorFusionDecision(
            spokenText = null,
            isUrgent = false,
            caneTelemetry = caneTelemetry
        )
    }

    companion object {
        const val TELEMETRY_FRESHNESS_WINDOW_MS = 1000L
        const val PRIORITY_CRITICAL_SONAR = 1
        const val PRIORITY_SIDE_FUSION = 2
    }
}
