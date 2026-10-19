package com.project.argus.data.vision

import com.project.argus.domain.model.DetectedObstacle
import com.project.argus.domain.model.VisionFrameResult

/**
 * Responsible solely for orchestrating the vision post-processing pipeline:
 * filters allowed COCO obstacle classes and suppresses noise below area threshold,
 * delegates hazard classification to [HazardClassifier], geometry to [SpatialClassifier],
 * and label formatting to [LabelFormatter].
 */
class VisionResultProcessor(
    private val hazardClassifier: HazardClassifier = HazardClassifier(),
    private val spatialClassifier: SpatialClassifier = SpatialClassifier(),
    private val labelFormatter: LabelFormatter = LabelFormatter()
) : VisionProcessor {

    companion object {
        val ALLOWED_OBJECT_CLASSES = setOf(
            "person",
            "chair",
            "dining table",
            "bench",
            "backpack",
            "suitcase",
            "potted plant",
            "car",
            "bicycle",
            "dog"
        )
    }

    fun isAllowedObject(label: String): Boolean {
        return ALLOWED_OBJECT_CLASSES.contains(label.trim().lowercase())
    }

    override fun process(
        detectedObjects: List<RawDetectedObject>,
        imageLabels: List<RawLabel>,
        imageWidth: Int,
        imageHeight: Int,
        timestamp: Long
    ): VisionFrameResult {
        val rawLabels = imageLabels.map { label ->
            "${labelFormatter.format(label.text)} (${(label.confidence * 100).toInt()}%)"
        }

        val totalArea = (imageWidth * imageHeight).toFloat()

        val validObjects = detectedObjects.filter { obj ->
            val topLabel = obj.labels.firstOrNull()?.text?.trim()?.lowercase()
            val isAllowed = topLabel != null && topLabel in ALLOWED_OBJECT_CLASSES
            val isNotIgnored = !spatialClassifier.isIgnored(obj.boundingBox.area(), totalArea)
            isAllowed && isNotIgnored
        }

        val hazards = hazardClassifier.classify(
            imageLabels = imageLabels,
            hasPhysicalObjects = validObjects.isNotEmpty()
        )

        val isArmrestDominant = hazards.any { it is com.project.argus.domain.model.SpatialHazard.ArmrestDetected }

        val obstacles = validObjects.mapIndexed { index, mlObject ->
            val box = mlObject.boundingBox
            val zone = spatialClassifier.classifyZone(box.centerX(), imageWidth)
            val distanceCategory = spatialClassifier.classifyDistance(box.area(), totalArea)
            val rawText = mlObject.labels.firstOrNull()?.text ?: "Obstacle"
            val isArmrestObj = hazardClassifier.isArmrestObject(rawText, isArmrestDominant)
            val label = if (isArmrestObj) "Armrest" else labelFormatter.format(rawText)
            val confidence = mlObject.labels.firstOrNull()?.confidence ?: 0.6f

            DetectedObstacle(
                id = index,
                label = label,
                confidence = confidence,
                boundingBox = box,
                zone = zone,
                estimatedDistanceCategory = distanceCategory,
                isArmrest = isArmrestObj
            )
        }

        return VisionFrameResult(
            obstacles = obstacles,
            spatialHazards = hazards,
            rawLabels = rawLabels,
            frameWidth = imageWidth,
            frameHeight = imageHeight,
            timestamp = timestamp
        )
    }
}
