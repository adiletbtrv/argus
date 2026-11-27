package com.project.argus.data.vision

import com.project.argus.domain.model.SpatialHazard
import java.util.Locale

class HazardClassifier {

    /**
     * Analyzes raw ML Kit image labels and returns spatial hazards detected in this frame.
     * Furniture and armrest context suppresses false stair detections caused by striped upholstery
     * when [stairsConfidence] < 0.72f without emitting noisy furniture/armrest hazards.
     */
    fun classify(
        imageLabels: List<RawLabel>,
        hasPhysicalObjects: Boolean
    ): List<SpatialHazard> {
        var stairsConfidence = 0f
        var armrestConfidence = 0f
        var furnitureConfidence = 0f
        var doorConfidence = 0f
        var wallConfidence = 0f

        for (label in imageLabels) {
            val textLower = label.text.lowercase(Locale.ROOT)
            val conf = label.confidence

            when {
                textLower.contains("armrest") || textLower.contains("arm rest") -> {
                    if (conf > armrestConfidence) armrestConfidence = conf
                }
                textLower.contains("chair") || textLower.contains("couch") ||
                    textLower.contains("sofa") || textLower.contains("furniture") ||
                    textLower.contains("seat") || textLower.contains("cushion") ||
                    textLower.contains("recliner") -> {
                    if (conf > furnitureConfidence) furnitureConfidence = conf
                }
                textLower.contains("stair") || textLower.contains("step") || textLower.contains("tread") -> {
                    if (conf > stairsConfidence) stairsConfidence = conf
                }
                textLower.contains("door") -> {
                    if (conf > doorConfidence) doorConfidence = conf
                }
                textLower.contains("wall") -> {
                    if (conf > wallConfidence) wallConfidence = conf
                }
            }
        }

        val hazards = mutableListOf<SpatialHazard>()

        // Suppress false stairs triggered by striped couch/chair upholstery when stairs < 0.72f
        val suppressedByFurnitureContext =
            (armrestConfidence >= 0.40f || furnitureConfidence >= 0.55f) &&
                stairsConfidence < 0.72f

        if (!suppressedByFurnitureContext &&
            stairsConfidence >= 0.65f &&
            stairsConfidence > armrestConfidence + 0.20f
        ) {
            hazards.add(SpatialHazard.StairsAhead(stairsConfidence))
        }

        if (doorConfidence >= 0.55f) {
            hazards.add(SpatialHazard.DoorAhead(doorConfidence))
        }

        if (wallConfidence >= 0.65f && !hasPhysicalObjects) {
            hazards.add(SpatialHazard.WallProximity(wallConfidence))
        }

        return hazards
    }
}
