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
}

//Horizontal field of view zone for obstacle positioning.
enum class HorizontalZone {
    LEFT,
    CENTER,
    RIGHT
}

//Spatial hazard indicators detected in the environment.
sealed interface SpatialHazard {
    data object None : SpatialHazard
    data class StairsAhead(val confidence: Float) : SpatialHazard
    data class ArmrestDetected(val confidence: Float, val zone: HorizontalZone = HorizontalZone.CENTER) : SpatialHazard
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
    val isArmrest: Boolean = false
)


enum class DistanceCategory {
    NEAR,    // High threat: covers > 35% of frame area
    MEDIUM,  // Moderate: covers 10% - 35%
    FAR      // Low: covers < 10%
}


data class VisionFrameResult(
    val obstacles: List<DetectedObstacle> = emptyList(),
    val spatialHazards: List<SpatialHazard> = emptyList(),
    val rawLabels: List<String> = emptyList(),
    val frameWidth: Int = 0,
    val frameHeight: Int = 0,
    val timestamp: Long = System.currentTimeMillis()
)
