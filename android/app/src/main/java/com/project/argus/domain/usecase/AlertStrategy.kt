package com.project.argus.domain.usecase

import com.project.argus.domain.model.DistanceCategory
import com.project.argus.domain.model.HorizontalZone
import com.project.argus.domain.model.SpatialHazard
import com.project.argus.domain.model.VisionFrameResult
import java.util.Locale

import com.project.argus.domain.model.ObstacleTranslator

// Result of evaluating hazards, containing speech prompt and filtered spatial hazards.
data class AlertDecision(
    val spokenText: String?,
    val isUrgent: Boolean,
    val validatedHazards: List<SpatialHazard>
)

interface AlertStrategy {
    // Returns an [AlertDecision] if this strategy matches the current state, otherwise null.
    fun evaluate(
        validatedHazards: List<SpatialHazard>,
        frame: VisionFrameResult
    ): AlertDecision?
}

// Handles urgent stairs alert: highest navigational priority.
internal class StairsAlertStrategy : AlertStrategy {
    override fun evaluate(
        validatedHazards: List<SpatialHazard>,
        frame: VisionFrameResult
    ): AlertDecision? {
        val stairs = validatedHazards.filterIsInstance<SpatialHazard.StairsAhead>().firstOrNull()
            ?: return null
        return AlertDecision(
            spokenText = "Осторожно, впереди лестница!",
            isUrgent = true,
            validatedHazards = validatedHazards
        )
    }
}

// Handles urgent wall proximity alert.
internal class WallProximityAlertStrategy : AlertStrategy {
    override fun evaluate(
        validatedHazards: List<SpatialHazard>,
        frame: VisionFrameResult
    ): AlertDecision? {
        val wall = validatedHazards.filterIsInstance<SpatialHazard.WallProximity>().firstOrNull()
            ?: return null
        return AlertDecision(
            spokenText = "Внимание, стена прямо по курсу!",
            isUrgent = true,
            validatedHazards = validatedHazards
        )
    }
}

// Handles armrest directional guidance: includes zone-aware phrasing.
internal class ArmrestAlertStrategy : AlertStrategy {
    override fun evaluate(
        validatedHazards: List<SpatialHazard>,
        frame: VisionFrameResult
    ): AlertDecision? {
        val armrest = validatedHazards.filterIsInstance<SpatialHazard.ArmrestDetected>().firstOrNull()
            ?: return null
        val side = when (armrest.zone) {
            HorizontalZone.LEFT -> "слева"
            HorizontalZone.RIGHT -> "справа"
            HorizontalZone.CENTER -> "впереди"
        }
        return AlertDecision(
            spokenText = "Подлокотник $side",
            isUrgent = false,
            validatedHazards = validatedHazards
        )
    }
}

// Handles doorway announcement.
internal class DoorAheadAlertStrategy : AlertStrategy {
    override fun evaluate(
        validatedHazards: List<SpatialHazard>,
        frame: VisionFrameResult
    ): AlertDecision? {
        val door = validatedHazards.filterIsInstance<SpatialHazard.DoorAhead>().firstOrNull()
            ?: return null
        return AlertDecision(
            spokenText = "Впереди дверной проём",
            isUrgent = false,
            validatedHazards = validatedHazards
        )
    }
}

// Handles obstacles in direct center path with corridor bypass recommendations.
internal class CenterObstacleAlertStrategy : AlertStrategy {
    override fun evaluate(
        validatedHazards: List<SpatialHazard>,
        frame: VisionFrameResult
    ): AlertDecision? {
        val centerObstacle = frame.obstacles.firstOrNull {
            it.zone == HorizontalZone.CENTER &&
                (it.estimatedDistanceCategory == DistanceCategory.NEAR ||
                 it.estimatedDistanceCategory == DistanceCategory.MEDIUM)
        } ?: return null

        val label = ObstacleTranslator.translate(centerObstacle.label)

        if (centerObstacle.estimatedDistanceCategory == DistanceCategory.NEAR) {
            return AlertDecision(
                spokenText = "Вплотную, $label!",
                isUrgent = true,
                validatedHazards = validatedHazards
            )
        }

        val leftBlocked = frame.obstacles.any {
            it.zone == HorizontalZone.LEFT &&
                (it.estimatedDistanceCategory == DistanceCategory.NEAR ||
                 it.estimatedDistanceCategory == DistanceCategory.MEDIUM)
        }
        val rightBlocked = frame.obstacles.any {
            it.zone == HorizontalZone.RIGHT &&
                (it.estimatedDistanceCategory == DistanceCategory.NEAR ||
                 it.estimatedDistanceCategory == DistanceCategory.MEDIUM)
        }

        val prompt = when {
            !leftBlocked -> "Прямо $label, обойдите слева"
            !rightBlocked -> "Прямо $label, обойдите справа"
            else -> "Впереди $label"
        }

        return AlertDecision(
            spokenText = prompt,
            isUrgent = false,
            validatedHazards = validatedHazards
        )
    }
}
