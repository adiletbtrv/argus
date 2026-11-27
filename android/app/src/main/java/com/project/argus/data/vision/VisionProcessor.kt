package com.project.argus.data.vision

import com.project.argus.domain.model.BoundingBox2D
import com.project.argus.domain.model.VisionFrameResult

/**
 * Lightweight pixel-level appearance metrics computed on a 16x16 grid inside a bounding box.
 */
data class BoxAppearanceStats(
    val meanLuminance: Float,
    val luminanceStdDev: Float,
    val meanGradient: Float,
    val colorSpread: Float,
    val borderMeanLuminance: Float,
    val quadrantMeanSpread: Float
) {
    val brightnessRatio: Float
        get() = meanLuminance / borderMeanLuminance.coerceAtLeast(1f)
}

/**
 * Platform-independent data class representing a detected ML object candidate.
 */
data class RawDetectedObject(
    val boundingBox: BoundingBox2D,
    val labels: List<RawLabel>,
    val appearanceStats: BoxAppearanceStats? = null
)

/**
 * Platform-independent data class representing a detected classification label.
 */
data class RawLabel(
    val text: String,
    val confidence: Float
)

interface VisionProcessor {
    fun process(
        detectedObjects: List<RawDetectedObject>,
        imageLabels: List<RawLabel>,
        imageWidth: Int,
        imageHeight: Int,
        timestamp: Long = System.currentTimeMillis()
    ): VisionFrameResult

    fun reset() {}
}
