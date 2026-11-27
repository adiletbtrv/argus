package com.project.argus.data.vision

import com.project.argus.domain.model.DetectedObstacle
import com.project.argus.domain.model.ObstacleCatalog
import com.project.argus.domain.model.VisionFrameResult

/**
 * Orchestrates the vision post-processing pipeline:
 * filters curated COCO navigation classes, applies compact/standard area gates,
 * stabilizes moderate detections via [TemporalObjectTracker], classifies scene hazards
 * via [HazardClassifier], and orders obstacles by threat priority.
 */
class VisionResultProcessor(
    private val hazardClassifier: HazardClassifier = HazardClassifier(),
    private val spatialClassifier: SpatialClassifier = SpatialClassifier(),
    private val labelFormatter: LabelFormatter = LabelFormatter(),
    private val temporalTracker: TemporalObjectTracker = TemporalObjectTracker()
) : VisionProcessor {

    companion object {
        val ALLOWED_OBJECT_CLASSES: Set<String> = ObstacleCatalog.allowedCocoClasses
        const val MIN_DETECTION_CONFIDENCE = 0.45f
    }

    fun isAllowedObject(label: String): Boolean {
        return ObstacleCatalog.isAllowedCocoClass(label)
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

        temporalTracker.pruneExpired(timestamp)

        val totalArea = (imageWidth * imageHeight).toFloat()
        if (totalArea <= 0f) {
            return VisionFrameResult(
                obstacles = emptyList(),
                spatialHazards = hazardClassifier.classify(
                    imageLabels = imageLabels,
                    hasPhysicalObjects = false
                ),
                rawLabels = rawLabels,
                frameWidth = imageWidth,
                frameHeight = imageHeight,
                timestamp = timestamp
            )
        }

        val confirmedObstacles = mutableListOf<DetectedObstacle>()

        for (mlObject in detectedObjects) {
            val topLabel = mlObject.labels.firstOrNull() ?: continue
            val spec = ObstacleCatalog.findSpec(topLabel.text) ?: continue
            val confidence = topLabel.confidence
            if (confidence < MIN_DETECTION_CONFIDENCE) continue

            val box = mlObject.boundingBox
            if (spatialClassifier.isIgnored(box.area(), totalArea, isCompact = spec.isCompact)) {
                continue
            }

            val distanceCategory = spatialClassifier.classifyDistance(
                boxArea = box.area(),
                totalArea = totalArea,
                isCompact = spec.isCompact
            )

            val confirmed = temporalTracker.isConfirmed(
                label = spec.cocoLabel,
                box = box,
                confidence = confidence,
                distanceCategory = distanceCategory,
                timestampMs = timestamp,
                frameWidth = imageWidth,
                frameHeight = imageHeight
            )
            if (!confirmed) continue

            val zone = spatialClassifier.classifyZone(box.centerX(), imageWidth)
            val label = labelFormatter.format(spec.cocoLabel)

            confirmedObstacles.add(
                DetectedObstacle(
                    id = 0,
                    label = label,
                    confidence = confidence,
                    boundingBox = box,
                    zone = zone,
                    estimatedDistanceCategory = distanceCategory,
                    dangerPriority = spec.dangerPriority
                )
            )
        }

        val sortedObstacles = confirmedObstacles
            .sortedWith(
                compareByDescending<DetectedObstacle> { it.dangerPriority }
                    .thenBy { it.estimatedDistanceCategory.ordinal }
                    .thenByDescending { it.confidence }
            )
            .mapIndexed { index, obstacle -> obstacle.copy(id = index) }

        val hazards = hazardClassifier.classify(
            imageLabels = imageLabels,
            hasPhysicalObjects = sortedObstacles.isNotEmpty()
        )

        return VisionFrameResult(
            obstacles = sortedObstacles,
            spatialHazards = hazards,
            rawLabels = rawLabels,
            frameWidth = imageWidth,
            frameHeight = imageHeight,
            timestamp = timestamp
        )
    }

    override fun reset() {
        temporalTracker.reset()
    }
}
