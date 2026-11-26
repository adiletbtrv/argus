package com.project.argus.data.vision

import com.project.argus.domain.model.DistanceCategory
import com.project.argus.domain.model.HorizontalZone

class SpatialClassifier {

    // Determines horizontal zone from the object's center-X relative to imageWidth.
    fun classifyZone(centerX: Float, imageWidth: Int): HorizontalZone {
        if (imageWidth <= 0) return HorizontalZone.CENTER
        return when {
            centerX < imageWidth * 0.33f -> HorizontalZone.LEFT
            centerX > imageWidth * 0.66f -> HorizontalZone.RIGHT
            else -> HorizontalZone.CENTER
        }
    }

    // Determines distance category from frame area coverage.
    // Standard objects: NEAR >= 28%, MEDIUM 12%-28%, FAR 6%-12%.
    // Compact/round objects: NEAR >= 14%, MEDIUM 5%-14%, FAR 1.5%-5%.
    fun classifyDistance(
        boxArea: Float,
        totalArea: Float,
        isCompact: Boolean = false
    ): DistanceCategory {
        val fraction = if (totalArea > 0f) boxArea / totalArea else 0f
        val nearThreshold = if (isCompact) THRESHOLD_COMPACT_NEAR else THRESHOLD_NEAR
        val mediumThreshold = if (isCompact) THRESHOLD_COMPACT_MEDIUM else THRESHOLD_MEDIUM
        return when {
            fraction >= nearThreshold -> DistanceCategory.NEAR
            fraction >= mediumThreshold -> DistanceCategory.MEDIUM
            else -> DistanceCategory.FAR
        }
    }

    // Standard objects below 6% and compact objects below 1.5% of frame area are ignored.
    fun isIgnored(
        boxArea: Float,
        totalArea: Float,
        isCompact: Boolean = false
    ): Boolean {
        val fraction = if (totalArea > 0f) boxArea / totalArea else 0f
        val ignoreThreshold = if (isCompact) THRESHOLD_COMPACT_IGNORE else THRESHOLD_IGNORE
        return fraction < ignoreThreshold
    }

    companion object {
        const val THRESHOLD_NEAR = 0.28f
        const val THRESHOLD_MEDIUM = 0.12f
        const val THRESHOLD_IGNORE = 0.06f

        const val THRESHOLD_COMPACT_NEAR = 0.14f
        const val THRESHOLD_COMPACT_MEDIUM = 0.05f
        const val THRESHOLD_COMPACT_IGNORE = 0.015f
    }
}
