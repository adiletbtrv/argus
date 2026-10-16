package com.project.argus.data.vision

import com.project.argus.domain.model.SpatialHazard
import java.util.Locale

class HazardClassifier {

    /**
     * Analyzes raw image labels and returns the list of spatial hazards detected in this frame.
     *
     * @param imageLabels Raw classification labels from ML Kit Image Labeler.
     * @param hasPhysicalObjects True if the Object Detector found at least one bounding-box object,
     *        used to suppress [SpatialHazard.WallProximity] when a real object is in the foreground.
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

        // Critical disambiguation: armrest/furniture confidence suppresses stairs
        val isArmrestDominant = armrestConfidence >= 0.40f ||
            (furnitureConfidence >= 0.55f && stairsConfidence < 0.72f)

        if (isArmrestDominant) {
            val finalConf = armrestConfidence.coerceAtLeast(furnitureConfidence)
            hazards.add(SpatialHazard.ArmrestDetected(finalConf))
        } else if (stairsConfidence >= 0.65f && stairsConfidence > armrestConfidence + 0.20f) {
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

    /**
     * Returns true if the given object label indicates it belongs to the armrest/seating category.
     */
    fun isArmrestObject(rawText: String, isArmrestDominant: Boolean): Boolean =
        isArmrestDominant && (
            rawText.contains("furniture", ignoreCase = true) ||
            rawText.contains("chair", ignoreCase = true) ||
            rawText.contains("couch", ignoreCase = true) ||
            rawText.contains("arm", ignoreCase = true)
        )
}
