package com.project.argus.domain.usecase

import com.project.argus.domain.model.SpatialHazard
import com.project.argus.domain.model.VisionFrameResult


class EvaluateHazardsUseCase(
     //Priority-ordered list of alert strategies.
     //Evaluated top-to-bottom; first match wins.
    private val alertStrategies: List<AlertStrategy> = defaultStrategies()
) {
    private var consecutiveStairsCount = 0
    private var lastStairsTime = 0L

    operator fun invoke(frameResult: VisionFrameResult): AlertDecision {
        val now = System.currentTimeMillis()
        val validatedHazards = validateHazards(frameResult, now)

        // Walk priority-ordered strategies; return the first matching decision
        for (strategy in alertStrategies) {
            val decision = strategy.evaluate(validatedHazards, frameResult)
            if (decision != null) return decision
        }

        return AlertDecision(spokenText = null, isUrgent = false, validatedHazards = validatedHazards)
    }


    //Applies temporal debouncing to stairs and collects confirmed hazards
    private fun validateHazards(frameResult: VisionFrameResult, now: Long): List<SpatialHazard> {
        val validatedHazards = mutableListOf<SpatialHazard>()

        val armrestHazard = frameResult.spatialHazards
            .filterIsInstance<SpatialHazard.ArmrestDetected>()
            .firstOrNull()

        val rawStairsHazard = frameResult.spatialHazards
            .filterIsInstance<SpatialHazard.StairsAhead>()
            .firstOrNull()

        // 1. Armrest vs Stairs temporal arbitration
        if (armrestHazard != null) {
            consecutiveStairsCount = 0
            validatedHazards.add(armrestHazard)
        } else if (rawStairsHazard != null) {
            if (now - lastStairsTime < 750L) {
                consecutiveStairsCount++
            } else {
                consecutiveStairsCount = 1
            }
            lastStairsTime = now
            // Real stairs require at least 2 consecutive positive frames
            if (consecutiveStairsCount >= 2) {
                validatedHazards.add(rawStairsHazard)
            }
        } else {
            if (now - lastStairsTime > 800L) {
                consecutiveStairsCount = 0
            }
        }

        // 2. Additional environmental hazards (no debouncing needed)
        frameResult.spatialHazards.forEach { hazard ->
            when (hazard) {
                is SpatialHazard.WallProximity -> validatedHazards.add(hazard)
                is SpatialHazard.DoorAhead -> validatedHazards.add(hazard)
                else -> Unit
            }
        }

        return validatedHazards
    }

    fun reset() {
        consecutiveStairsCount = 0
        lastStairsTime = 0L
    }

    companion object {
        //Default priority-ordered strategies used in production.
        fun defaultStrategies(): List<AlertStrategy> = listOf(
            StairsAlertStrategy(),
            WallProximityAlertStrategy(),
            ArmrestAlertStrategy(),
            DoorAheadAlertStrategy(),
            CenterObstacleAlertStrategy()
        )
    }
}
