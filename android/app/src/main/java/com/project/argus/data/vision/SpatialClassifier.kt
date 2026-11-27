package com.project.argus.data.vision

import com.project.argus.domain.model.DistanceCategory
import com.project.argus.domain.model.HorizontalZone

class SpatialClassifier {

    /**
     * Determines horizontal zone from the object's center-X relative to [imageWidth].
     * Zone thresholds: LEFT < 33% | CENTER 33%-66% | RIGHT > 66%.
     */
    fun classifyZone(centerX: Float, imageWidth: Int): HorizontalZone = when {
        centerX < imageWidth * 0.33f -> HorizontalZone.LEFT
        centerX > imageWidth * 0.66f -> HorizontalZone.RIGHT
        else -> HorizontalZone.CENTER
    }

    /**
     * Determines distance category from the fraction of the total frame area
     * covered by the bounding box.
     * Standard objects: NEAR >= 28%, MEDIUM 12%-28%, FAR 6%-12%.
     * Compact objects (isCompact = true): NEAR >= 14%, MEDIUM 5%-14%, FAR 2%-5%.
     */
    fun classifyDistance(
        boxArea: Float,
        totalArea: Float,
        isCompact: Boolean = false
    ): DistanceCategory {
        val fraction = if (totalArea > 0f) boxArea / totalArea else 0f
        val nearThreshold = if (isCompact) COMPACT_THRESHOLD_NEAR else THRESHOLD_NEAR
        val mediumThreshold = if (isCompact) COMPACT_THRESHOLD_MEDIUM else THRESHOLD_MEDIUM
        return when {
            fraction >= nearThreshold -> DistanceCategory.NEAR
            fraction >= mediumThreshold -> DistanceCategory.MEDIUM
            else -> DistanceCategory.FAR
        }
    }

    /**
     * Standard objects covering less than 6% (2% for compact objects) of the frame area are ignored.
     */
    fun isIgnored(
        boxArea: Float,
        totalArea: Float,
        isCompact: Boolean = false
    ): Boolean {
        val fraction = if (totalArea > 0f) boxArea / totalArea else 0f
        val ignoreThreshold = if (isCompact) COMPACT_THRESHOLD_IGNORE else THRESHOLD_IGNORE
        return fraction < ignoreThreshold
    }

    companion object {
        const val THRESHOLD_NEAR = 0.28f
        const val THRESHOLD_MEDIUM = 0.12f
        const val THRESHOLD_IGNORE = 0.06f

        const val COMPACT_THRESHOLD_NEAR = 0.14f
        const val COMPACT_THRESHOLD_MEDIUM = 0.05f
        const val COMPACT_THRESHOLD_IGNORE = 0.02f
    }
}
