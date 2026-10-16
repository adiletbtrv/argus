package com.project.argus.data.vision

import com.project.argus.domain.model.BoundingBox2D
import com.project.argus.domain.model.VisionFrameResult

/**
 * Platform-independent data class representing a detected ML object candidate.
 */
data class RawDetectedObject(
    val boundingBox: BoundingBox2D,
    val labels: List<RawLabel>
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
}
