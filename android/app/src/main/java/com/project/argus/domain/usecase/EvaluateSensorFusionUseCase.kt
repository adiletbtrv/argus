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
    val recommendedAction: String? = null
)

/**
 * Sensor Fusion Use Case combining ultrasonic & IMU telemetry from the physical cane
 * with real-time visual obstacles from the smartphone camera.
 *
 * Rules:
 * 1. Critical Sonar (< 35 cm): Urgent alert with queue flush.
 * 2. Cane Direction + Camera Side collision:
 *    If Cane DIR == LEFT and Camera detects obstacle on LEFT -> "Осторожно, слева преграда!"
 *    If Cane DIR == RIGHT and Camera detects obstacle on RIGHT -> "Осторожно, справа преграда!"
 * 3. Free corridor guidance for central obstacles:
 *    "Прямо <объект>, обойдите слева" / "Прямо <объект>, обойдите справа"
 * 4. Area thresholds: >= 0.28 "Вплотную, <объект>!", >= 0.12 "Впереди <объект>", < 0.06 ignored.
 */
class EvaluateSensorFusionUseCase {

    operator fun invoke(
        frameResult: VisionFrameResult,
        caneTelemetry: CaneTelemetry?,
        currentTimeMs: Long = System.currentTimeMillis()
    ): SensorFusionDecision {
        val isTelemetryFresh = caneTelemetry != null && (currentTimeMs - caneTelemetry.timestamp < TELEMETRY_FRESHNESS_WINDOW_MS)

        // Filter valid obstacles by unified distance scale (FAR is tracked silently)
        val relevantObstacles = frameResult.obstacles.filter { obs ->
            obs.estimatedDistanceCategory != DistanceCategory.FAR
        }

        // 1. Sensor Fusion: Cane IMU direction + Camera Zone match
        if (isTelemetryFresh) {
            when (caneTelemetry.direction) {
                CaneDirection.LEFT -> {
                    val leftHazard = relevantObstacles.firstOrNull { it.zone == HorizontalZone.LEFT }
                    if (leftHazard != null) {
                        return SensorFusionDecision(
                            spokenText = "Осторожно, слева преграда!",
                            isUrgent = true,
                            caneTelemetry = caneTelemetry,
                            recommendedAction = "AVOID_LEFT"
                        )
                    }
                }
                CaneDirection.RIGHT -> {
                    val rightHazard = relevantObstacles.firstOrNull { it.zone == HorizontalZone.RIGHT }
                    if (rightHazard != null) {
                        return SensorFusionDecision(
                            spokenText = "Осторожно, справа преграда!",
                            isUrgent = true,
                            caneTelemetry = caneTelemetry,
                            recommendedAction = "AVOID_RIGHT"
                        )
                    }
                }
                CaneDirection.CENTER -> Unit
            }
        }

        // 2. Critical Cane Sonar Threshold (< 35 cm)
        if (isTelemetryFresh && caneTelemetry.isCriticalDistance) {
            return SensorFusionDecision(
                spokenText = "Внимание, близко препятствие: ${caneTelemetry.distanceCm} см!",
                isUrgent = true,
                caneTelemetry = caneTelemetry,
                recommendedAction = "STOP"
            )
        }

        // 3. Central Obstacle and Free Corridor Arbitration
        val centerObstacle = relevantObstacles.firstOrNull { it.zone == HorizontalZone.CENTER }
        if (centerObstacle != null) {
            val leftBlocked = relevantObstacles.any { it.zone == HorizontalZone.LEFT }
            val rightBlocked = relevantObstacles.any { it.zone == HorizontalZone.RIGHT }

            val label = com.project.argus.domain.model.ObstacleTranslator.translate(centerObstacle.label)

            // When obstacle is right in front (NEAR / area >= 0.28)
            if (centerObstacle.estimatedDistanceCategory == DistanceCategory.NEAR) {
                return SensorFusionDecision(
                    spokenText = "Вплотную, $label!",
                    isUrgent = true,
                    caneTelemetry = caneTelemetry,
                    recommendedAction = "STOP"
                )
            }

            // Corridor determination for MEDIUM distance
            val corridorText = when {
                !leftBlocked -> "Прямо $label, обойдите слева"
                !rightBlocked -> "Прямо $label, обойдите справа"
                else -> "Впереди $label"
            }

            return SensorFusionDecision(
                spokenText = corridorText,
                isUrgent = false,
                caneTelemetry = caneTelemetry,
                recommendedAction = if (!leftBlocked) "BYPASS_LEFT" else if (!rightBlocked) "BYPASS_RIGHT" else "AHEAD"
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
    }
}
