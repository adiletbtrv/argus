package com.project.argus.domain.usecase

import com.project.argus.domain.model.DistanceCategory
import com.project.argus.domain.model.HorizontalZone
import com.project.argus.domain.model.ObstacleTranslator
import com.project.argus.domain.model.SpatialHazard
import com.project.argus.domain.model.VisionFrameResult

// Result of evaluating hazards, containing speech prompt, filtered spatial hazards, and priority rank (1 = highest).
data class AlertDecision(
    val spokenText: String?,
    val isUrgent: Boolean,
    val validatedHazards: List<SpatialHazard>,
    val priority: Int = Int.MAX_VALUE
)

interface AlertStrategy {
    // Returns an AlertDecision if this strategy matches the current state, otherwise null.
    fun evaluate(
        validatedHazards: List<SpatialHazard>,
        frame: VisionFrameResult
    ): AlertDecision?
}

// Priority 1: Urgent stairs alert.
internal class StairsAlertStrategy : AlertStrategy {
    override fun evaluate(
        validatedHazards: List<SpatialHazard>,
        frame: VisionFrameResult
    ): AlertDecision? {
        validatedHazards.filterIsInstance<SpatialHazard.StairsAhead>().firstOrNull()
            ?: return null
        return AlertDecision(
            spokenText = "Осторожно, впереди лестница!",
            isUrgent = true,
            validatedHazards = validatedHazards,
            priority = PRIORITY_STAIRS
        )
    }

    companion object {
        const val PRIORITY_STAIRS = 1
    }
}

// Priority 1: Urgent overhead/low branch alert missed by ground cane sonar.
internal class LowBranchAlertStrategy : AlertStrategy {
    override fun evaluate(
        validatedHazards: List<SpatialHazard>,
        frame: VisionFrameResult
    ): AlertDecision? {
        validatedHazards.filterIsInstance<SpatialHazard.LowBranchAhead>().firstOrNull()
            ?: return null
        return AlertDecision(
            spokenText = "Осторожно, впереди ветка!",
            isUrgent = true,
            validatedHazards = validatedHazards,
            priority = PRIORITY_BRANCH
        )
    }

    companion object {
        const val PRIORITY_BRANCH = 1
    }
}

// Priority 3: Urgent wall proximity alert.
internal class WallProximityAlertStrategy : AlertStrategy {
    override fun evaluate(
        validatedHazards: List<SpatialHazard>,
        frame: VisionFrameResult
    ): AlertDecision? {
        validatedHazards.filterIsInstance<SpatialHazard.WallProximity>().firstOrNull()
            ?: return null
        return AlertDecision(
            spokenText = "Внимание, стена прямо по курсу!",
            isUrgent = true,
            validatedHazards = validatedHazards,
            priority = PRIORITY_WALL
        )
    }

    companion object {
        const val PRIORITY_WALL = 3
    }
}

// Priority 3: Urgent near obstacle directly in the center path.
internal class NearCenterObstacleAlertStrategy : AlertStrategy {
    override fun evaluate(
        validatedHazards: List<SpatialHazard>,
        frame: VisionFrameResult
    ): AlertDecision? {
        val nearObstacle = findHighestPriorityCenterObstacle(frame, DistanceCategory.NEAR) ?: return null
        val label = ObstacleTranslator.translate(nearObstacle.label)
        return AlertDecision(
            spokenText = "Вплотную, $label!",
            isUrgent = true,
            validatedHazards = validatedHazards,
            priority = PRIORITY_NEAR_CENTER
        )
    }

    companion object {
        const val PRIORITY_NEAR_CENTER = 3
    }
}

// Priority 4: Handrail guidance.
internal class HandrailAlertStrategy : AlertStrategy {
    override fun evaluate(
        validatedHazards: List<SpatialHazard>,
        frame: VisionFrameResult
    ): AlertDecision? {
        validatedHazards.filterIsInstance<SpatialHazard.HandrailAhead>().firstOrNull()
            ?: return null
        return AlertDecision(
            spokenText = "Впереди перила",
            isUrgent = false,
            validatedHazards = validatedHazards,
            priority = PRIORITY_HANDRAIL
        )
    }

    companion object {
        const val PRIORITY_HANDRAIL = 4
    }
}

// Priority 4: Doorway announcement.
internal class DoorAheadAlertStrategy : AlertStrategy {
    override fun evaluate(
        validatedHazards: List<SpatialHazard>,
        frame: VisionFrameResult
    ): AlertDecision? {
        validatedHazards.filterIsInstance<SpatialHazard.DoorAhead>().firstOrNull()
            ?: return null
        return AlertDecision(
            spokenText = "Впереди дверной проем",
            isUrgent = false,
            validatedHazards = validatedHazards,
            priority = PRIORITY_DOOR
        )
    }

    companion object {
        const val PRIORITY_DOOR = 4
    }
}

// Priority 4: Verified ML Kit scene object not covered by COCO-80 bounding boxes.
internal class SceneObjectAlertStrategy : AlertStrategy {
    override fun evaluate(
        validatedHazards: List<SpatialHazard>,
        frame: VisionFrameResult
    ): AlertDecision? {
        val sceneObj = validatedHazards.filterIsInstance<SpatialHazard.SceneObjectAhead>().firstOrNull()
            ?: return null
        val translated = ObstacleTranslator.translate(sceneObj.label)
        return AlertDecision(
            spokenText = "Впереди $translated",
            isUrgent = false,
            validatedHazards = validatedHazards,
            priority = PRIORITY_SCENE_OBJECT
        )
    }

    companion object {
        const val PRIORITY_SCENE_OBJECT = 4
    }
}

// Priority 5: Medium-distance center obstacles with corridor bypass hints.
internal class CenterCorridorAlertStrategy : AlertStrategy {
    override fun evaluate(
        validatedHazards: List<SpatialHazard>,
        frame: VisionFrameResult
    ): AlertDecision? {
        val mediumObstacle = findHighestPriorityCenterObstacle(frame, DistanceCategory.MEDIUM) ?: return null
        val label = ObstacleTranslator.translate(mediumObstacle.label)

        val leftBlocked = isSideBlocked(frame, HorizontalZone.LEFT)
        val rightBlocked = isSideBlocked(frame, HorizontalZone.RIGHT)

        val prompt = when {
            !leftBlocked -> "Прямо $label, обойдите слева"
            !rightBlocked -> "Прямо $label, обойдите справа"
            else -> "Впереди $label"
        }

        return AlertDecision(
            spokenText = prompt,
            isUrgent = false,
            validatedHazards = validatedHazards,
            priority = PRIORITY_CENTER_CORRIDOR
        )
    }

    private fun isSideBlocked(frame: VisionFrameResult, zone: HorizontalZone): Boolean {
        return frame.obstacles.any {
            it.zone == zone &&
                (it.estimatedDistanceCategory == DistanceCategory.NEAR ||
                    it.estimatedDistanceCategory == DistanceCategory.MEDIUM)
        }
    }

    companion object {
        const val PRIORITY_CENTER_CORRIDOR = 5
    }
}

private fun findHighestPriorityCenterObstacle(
    frame: VisionFrameResult,
    category: DistanceCategory
) = frame.obstacles
    .filter { it.zone == HorizontalZone.CENTER && it.estimatedDistanceCategory == category }
    .maxWithOrNull(
        compareBy<com.project.argus.domain.model.DetectedObstacle> { it.dangerPriority }
            .thenBy { it.boundingBox.area() }
            .thenBy { it.confidence }
    )
