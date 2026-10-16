package com.project.argus.domain.usecase

import com.project.argus.domain.model.SpatialHazard
import com.project.argus.domain.model.VisionFrameResult


 //Result of evaluating hazards, containing speech prompt and filtered spatial hazards.

data class AlertDecision(
    val spokenText: String?,
    val isUrgent: Boolean,
    val validatedHazards: List<SpatialHazard>
)


interface AlertStrategy {

    //@return an [AlertDecision] if this strategy matches the current state, otherwise null.

    fun evaluate(
        validatedHazards: List<SpatialHazard>,
        frame: VisionFrameResult
    ): AlertDecision?
}



 //[SRP] Handles urgent stairs alert — highest navigational priority.

internal class StairsAlertStrategy : AlertStrategy {
    override fun evaluate(
        validatedHazards: List<SpatialHazard>,
        frame: VisionFrameResult
    ): AlertDecision? {
        val stairs = validatedHazards.filterIsInstance<SpatialHazard.StairsAhead>().firstOrNull()
            ?: return null
        return AlertDecision(
            spokenText = "Caution, stairs ahead!",
            isUrgent = true,
            validatedHazards = validatedHazards
        )
    }
}


//[SRP] Handles urgent wall proximity alert.

internal class WallProximityAlertStrategy : AlertStrategy {
    override fun evaluate(
        validatedHazards: List<SpatialHazard>,
        frame: VisionFrameResult
    ): AlertDecision? {
        val wall = validatedHazards.filterIsInstance<SpatialHazard.WallProximity>().firstOrNull()
            ?: return null
        return AlertDecision(
            spokenText = "Warning, wall directly ahead!",
            isUrgent = true,
            validatedHazards = validatedHazards
        )
    }
}


//[SRP] Handles armrest directional guidance — includes zone-aware phrasing.

internal class ArmrestAlertStrategy : AlertStrategy {
    override fun evaluate(
        validatedHazards: List<SpatialHazard>,
        frame: VisionFrameResult
    ): AlertDecision? {
        val armrest = validatedHazards.filterIsInstance<SpatialHazard.ArmrestDetected>().firstOrNull()
            ?: return null
        val side = when (armrest.zone) {
            com.project.argus.domain.model.HorizontalZone.LEFT -> "on the left"
            com.project.argus.domain.model.HorizontalZone.RIGHT -> "on the right"
            com.project.argus.domain.model.HorizontalZone.CENTER -> "ahead"
        }
        return AlertDecision(
            spokenText = "Armrest $side",
            isUrgent = false,
            validatedHazards = validatedHazards
        )
    }
}

//[SRP] Handles doorway announcement.

internal class DoorAheadAlertStrategy : AlertStrategy {
    override fun evaluate(
        validatedHazards: List<SpatialHazard>,
        frame: VisionFrameResult
    ): AlertDecision? {
        val door = validatedHazards.filterIsInstance<SpatialHazard.DoorAhead>().firstOrNull()
            ?: return null
        return AlertDecision(
            spokenText = "Doorway ahead",
            isUrgent = false,
            validatedHazards = validatedHazards
        )
    }
}

//[SRP] Handles nearby or medium obstacles in the direct center collision path.

internal class CenterObstacleAlertStrategy : AlertStrategy {
    override fun evaluate(
        validatedHazards: List<SpatialHazard>,
        frame: VisionFrameResult
    ): AlertDecision? {
        val centerObstacle = frame.obstacles.firstOrNull {
            it.zone == com.project.argus.domain.model.HorizontalZone.CENTER &&
                (it.estimatedDistanceCategory == com.project.argus.domain.model.DistanceCategory.NEAR ||
                 it.estimatedDistanceCategory == com.project.argus.domain.model.DistanceCategory.MEDIUM)
        } ?: return null

        val prompt = if (centerObstacle.estimatedDistanceCategory ==
            com.project.argus.domain.model.DistanceCategory.NEAR
        ) {
            "Close: ${centerObstacle.label}"
        } else {
            "${centerObstacle.label} ahead"
        }
        return AlertDecision(
            spokenText = prompt,
            isUrgent = false,
            validatedHazards = validatedHazards
        )
    }
}
