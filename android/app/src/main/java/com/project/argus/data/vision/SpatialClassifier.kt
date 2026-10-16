package com.project.argus.data.vision

import com.project.argus.domain.model.DistanceCategory
import com.project.argus.domain.model.HorizontalZone


class SpatialClassifier {

    /**
     * Determines horizontal zone from the object's center-X relative to [imageWidth].
     * Zone thresholds: LEFT < 33% | CENTER 33%–66% | RIGHT > 66%.
     */
    fun classifyZone(centerX: Float, imageWidth: Int): HorizontalZone = when {
        centerX < imageWidth * 0.33f -> HorizontalZone.LEFT
        centerX > imageWidth * 0.66f -> HorizontalZone.RIGHT
        else -> HorizontalZone.CENTER
    }

    /**
     * Determines distance category from the fraction of the total frame area
     * covered by the bounding box.
     * NEAR > 35% | MEDIUM 10%–35% | FAR < 10%.
     */
    fun classifyDistance(boxArea: Float, totalArea: Float): DistanceCategory {
        val fraction = if (totalArea > 0f) boxArea / totalArea else 0f
        return when {
            fraction > 0.35f -> DistanceCategory.NEAR
            fraction > 0.10f -> DistanceCategory.MEDIUM
            else -> DistanceCategory.FAR
        }
    }
}
