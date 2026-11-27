package com.project.argus.domain.model

data class BoundingBox2D(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    fun width(): Float = (right - left).coerceAtLeast(0f)
    fun height(): Float = (bottom - top).coerceAtLeast(0f)
    fun centerX(): Float = (left + right) / 2f
    fun centerY(): Float = (top + bottom) / 2f
    fun area(): Float = width() * height()

    fun iou(other: BoundingBox2D): Float {
        val interLeft = maxOf(left, other.left)
        val interTop = maxOf(top, other.top)
        val interRight = minOf(right, other.right)
        val interBottom = minOf(bottom, other.bottom)
        val interWidth = (interRight - interLeft).coerceAtLeast(0f)
        val interHeight = (interBottom - interTop).coerceAtLeast(0f)
        val interArea = interWidth * interHeight
        if (interArea <= 0f) return 0f
        val unionArea = area() + other.area() - interArea
        return if (unionArea > 0f) interArea / unionArea else 0f
    }
}

// Horizontal field of view zone for obstacle positioning.
enum class HorizontalZone {
    LEFT,
    CENTER,
    RIGHT
}

// Spatial hazard indicators detected in the environment.
sealed interface SpatialHazard {
    data object None : SpatialHazard
    data class StairsAhead(val confidence: Float) : SpatialHazard
    data class WallProximity(val confidence: Float) : SpatialHazard
    data class DoorAhead(val confidence: Float) : SpatialHazard
}

data class DetectedObstacle(
    val id: Int,
    val label: String,
    val confidence: Float,
    val boundingBox: BoundingBox2D,
    val zone: HorizontalZone,
    val estimatedDistanceCategory: DistanceCategory,
    val dangerPriority: Int = ObstacleCatalog.dangerPriority(label)
)

// Frame area coverage thresholds synchronized with SpatialClassifier (< 6% is ignored for standard objects).
enum class DistanceCategory {
    NEAR,    // High proximity: >= 28% standard (>= 14% compact)
    MEDIUM,  // Moderate: 12%-28% standard (5%-14% compact)
    FAR      // Distant: 6%-12% standard (2%-5% compact)
}

data class VisionFrameResult(
    val obstacles: List<DetectedObstacle> = emptyList(),
    val spatialHazards: List<SpatialHazard> = emptyList(),
    val rawLabels: List<String> = emptyList(),
    val frameWidth: Int = 0,
    val frameHeight: Int = 0,
    val timestamp: Long = System.currentTimeMillis()
)
