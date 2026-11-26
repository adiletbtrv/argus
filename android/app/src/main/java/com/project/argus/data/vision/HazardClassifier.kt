package com.project.argus.data.vision

import com.project.argus.domain.model.SpatialHazard
import java.util.Locale

class HazardClassifier {

    // Analyzes ML Kit scene labels to identify spatial hazards and non-COCO scene obstacles.
    // Furniture context suppresses false stair triggers without emitting armrest hazards.
    fun classify(
        imageLabels: List<RawLabel>,
        hasPhysicalObjects: Boolean,
        hasFurnitureObjects: Boolean = false,
        hasVehicleObjects: Boolean = false,
        hasTablewareObjects: Boolean = false
    ): List<SpatialHazard> {
        var stairsConfidence = 0f
        var furnitureConfidence = 0f
        var doorConfidence = 0f
        var wallConfidence = 0f
        var branchConfidence = 0f
        var handrailConfidence = 0f
        var wheelConfidence = 0f
        var saucerConfidence = 0f
        var cabinetryConfidence = 0f

        for (label in imageLabels) {
            val textLower = label.text.lowercase(Locale.ROOT)
            val conf = label.confidence

            when {
                textLower.contains("chair") || textLower.contains("couch") ||
                    textLower.contains("sofa") || textLower.contains("loveseat") ||
                    textLower.contains("furniture") || textLower.contains("seat") ||
                    textLower.contains("cushion") || textLower.contains("recliner") ||
                    textLower.contains("bench") || textLower.contains("desk") ||
                    textLower.contains("bunk bed") -> {
                    if (conf > furnitureConfidence) furnitureConfidence = conf
                }
                textLower.contains("cabinetry") -> {
                    if (conf > furnitureConfidence) furnitureConfidence = conf
                    if (conf > cabinetryConfidence) cabinetryConfidence = conf
                }
                textLower.contains("stair") || textLower.contains("step") || textLower.contains("tread") -> {
                    if (conf > stairsConfidence) stairsConfidence = conf
                }
                textLower.contains("handrail") -> {
                    if (conf > handrailConfidence) handrailConfidence = conf
                }
                textLower.contains("branch") -> {
                    if (conf > branchConfidence) branchConfidence = conf
                }
                textLower.contains("door") -> {
                    if (conf > doorConfidence) doorConfidence = conf
                }
                textLower.contains("wall") -> {
                    if (conf > wallConfidence) wallConfidence = conf
                }
                textLower == "wheel" || textLower == "tire" -> {
                    if (conf > wheelConfidence) wheelConfidence = conf
                }
                textLower == "saucer" -> {
                    if (conf > saucerConfidence) saucerConfidence = conf
                }
            }
        }

        val hazards = mutableListOf<SpatialHazard>()

        // Suppress false stairs triggered by striped furniture upholstery or cushions.
        val furnitureSuppressesStairs =
            (furnitureConfidence >= 0.55f && stairsConfidence < 0.78f) ||
                (hasFurnitureObjects && stairsConfidence < 0.75f)

        if (!furnitureSuppressesStairs &&
            stairsConfidence >= 0.65f &&
            stairsConfidence > furnitureConfidence + 0.10f
        ) {
            hazards.add(SpatialHazard.StairsAhead(stairsConfidence))
        }

        if (branchConfidence >= 0.70f) {
            hazards.add(SpatialHazard.LowBranchAhead(branchConfidence))
        }

        if (handrailConfidence >= 0.65f) {
            hazards.add(SpatialHazard.HandrailAhead(handrailConfidence))
        }

        if (doorConfidence >= 0.55f) {
            hazards.add(SpatialHazard.DoorAhead(doorConfidence))
        }

        if (wallConfidence >= 0.65f && !hasPhysicalObjects) {
            hazards.add(SpatialHazard.WallProximity(wallConfidence))
        }

        // Surface verified ML Kit object categories missing from COCO-80 when not already covered by a box.
        when {
            wheelConfidence >= 0.74f && !hasVehicleObjects -> {
                hazards.add(SpatialHazard.SceneObjectAhead("wheel", wheelConfidence))
            }
            saucerConfidence >= 0.74f && !hasTablewareObjects -> {
                hazards.add(SpatialHazard.SceneObjectAhead("saucer", saucerConfidence))
            }
            cabinetryConfidence >= 0.72f && !hasFurnitureObjects -> {
                hazards.add(SpatialHazard.SceneObjectAhead("cabinetry", cabinetryConfidence))
            }
        }

        return hazards
    }
}
