package com.project.argus.domain.usecase

import com.project.argus.domain.model.SpatialHazard
import com.project.argus.domain.model.VisionFrameResult

class EvaluateHazardsUseCase(
    // Priority-ordered list of alert strategies evaluated top-to-bottom.
    private val alertStrategies: List<AlertStrategy> = defaultStrategies()
) {
    private var consecutiveStairsCount = 0
    private var lastStairsTime = 0L

    private var consecutiveBranchCount = 0
    private var lastBranchTime = 0L

    operator fun invoke(
        frameResult: VisionFrameResult,
        currentTimeMs: Long = System.currentTimeMillis()
    ): AlertDecision {
        val validatedHazards = validateHazards(frameResult, currentTimeMs)

        for (strategy in alertStrategies) {
            val decision = strategy.evaluate(validatedHazards, frameResult)
            if (decision != null) return decision
        }

        return AlertDecision(spokenText = null, isUrgent = false, validatedHazards = validatedHazards)
    }

    // Applies temporal debouncing to urgent scene hazards (stairs, low branches) and collects confirmed hazards.
    private fun validateHazards(frameResult: VisionFrameResult, now: Long): List<SpatialHazard> {
        val validatedHazards = mutableListOf<SpatialHazard>()

        val rawStairsHazard = frameResult.spatialHazards
            .filterIsInstance<SpatialHazard.StairsAhead>()
            .firstOrNull()

        if (rawStairsHazard != null) {
            consecutiveStairsCount = if (now - lastStairsTime < TEMPORAL_WINDOW_MS) {
                consecutiveStairsCount + 1
            } else {
                1
            }
            lastStairsTime = now
            if (consecutiveStairsCount >= REQUIRED_FRAMES) {
                validatedHazards.add(rawStairsHazard)
            }
        } else if (now - lastStairsTime > RESET_WINDOW_MS) {
            consecutiveStairsCount = 0
        }

        val rawBranchHazard = frameResult.spatialHazards
            .filterIsInstance<SpatialHazard.LowBranchAhead>()
            .firstOrNull()

        if (rawBranchHazard != null) {
            consecutiveBranchCount = if (now - lastBranchTime < TEMPORAL_WINDOW_MS) {
                consecutiveBranchCount + 1
            } else {
                1
            }
            lastBranchTime = now
            if (consecutiveBranchCount >= REQUIRED_FRAMES) {
                validatedHazards.add(rawBranchHazard)
            }
        } else if (now - lastBranchTime > RESET_WINDOW_MS) {
            consecutiveBranchCount = 0
        }

        frameResult.spatialHazards.forEach { hazard ->
            when (hazard) {
                is SpatialHazard.WallProximity,
                is SpatialHazard.HandrailAhead,
                is SpatialHazard.DoorAhead,
                is SpatialHazard.SceneObjectAhead -> validatedHazards.add(hazard)
                else -> Unit
            }
        }

        return validatedHazards
    }

    fun reset() {
        consecutiveStairsCount = 0
        lastStairsTime = 0L
        consecutiveBranchCount = 0
        lastBranchTime = 0L
    }

    companion object {
        private const val TEMPORAL_WINDOW_MS = 750L
        private const val RESET_WINDOW_MS = 800L
        private const val REQUIRED_FRAMES = 2

        fun defaultStrategies(): List<AlertStrategy> = listOf(
            StairsAlertStrategy(),
            LowBranchAlertStrategy(),
            WallProximityAlertStrategy(),
            NearCenterObstacleAlertStrategy(),
            HandrailAlertStrategy(),
            DoorAheadAlertStrategy(),
            SceneObjectAlertStrategy(),
            CenterCorridorAlertStrategy()
        )
    }
}
