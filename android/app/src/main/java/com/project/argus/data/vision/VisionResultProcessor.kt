package com.project.argus.data.vision

import com.project.argus.domain.model.DetectedObstacle
import com.project.argus.domain.model.ObstacleCatalog
import com.project.argus.domain.model.ObstacleSpec
import com.project.argus.domain.model.VisionFrameResult

/**
 * Orchestrates the vision post-processing pipeline:
 * filters curated COCO navigation classes, applies compact/standard area gates,
 * suppresses cast person shadows and fur/wool clothing false animals,
 * stabilizes moderate detections via [TemporalObjectTracker], classifies scene hazards
 * via [HazardClassifier], and orders obstacles by threat priority.
 */
class VisionResultProcessor(
    private val hazardClassifier: HazardClassifier = HazardClassifier(),
    private val spatialClassifier: SpatialClassifier = SpatialClassifier(),
    private val labelFormatter: LabelFormatter = LabelFormatter(),
    private val temporalTracker: TemporalObjectTracker = TemporalObjectTracker(),
    private val personShadowFilter: PersonShadowFilter = PersonShadowFilter(),
    private val clothingAnimalDisambiguator: ClothingAnimalDisambiguator = ClothingAnimalDisambiguator()
) : VisionProcessor {

    companion object {
        val ALLOWED_OBJECT_CLASSES: Set<String> = ObstacleCatalog.allowedCocoClasses
        const val MIN_DETECTION_CONFIDENCE = 0.45f
        private const val STRONG_PRIMARY_PERSON_CONFIDENCE = 0.65f
        private val ANIMAL_COCO_CLASSES = setOf("dog", "cat", "bird", "horse", "sheep", "cow", "bear")
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

        data class Candidate(
            val raw: RawDetectedObject,
            val spec: ObstacleSpec,
            val confidence: Float
        )

        val candidates = detectedObjects.mapNotNull { mlObject ->
            val topLabel = mlObject.labels.firstOrNull() ?: return@mapNotNull null
            val spec = ObstacleCatalog.findSpec(topLabel.text) ?: return@mapNotNull null
            val confidence = topLabel.confidence
            if (confidence < MIN_DETECTION_CONFIDENCE) return@mapNotNull null
            val box = mlObject.boundingBox
            if (spatialClassifier.isIgnored(box.area(), totalArea, isCompact = spec.isCompact)) {
                return@mapNotNull null
            }
            Candidate(mlObject, spec, confidence)
        }

        // Pre-identify strong non-shadow persons for attached-shadow and torso-clothing checks.
        val strongPersonBoxes = candidates
            .filter { it.spec.cocoLabel == "person" && it.confidence >= STRONG_PRIMARY_PERSON_CONFIDENCE }
            .filterNot { candidate ->
                personShadowFilter.isLikelyShadow(
                    candidate = candidate.raw,
                    confidence = candidate.confidence,
                    primaryPersonBoxes = emptyList(),
                    imageLabels = imageLabels,
                    imageHeight = imageHeight
                )
            }
            .map { it.raw.boundingBox to it.confidence }

        val confirmedObstacles = mutableListOf<DetectedObstacle>()

        for (candidate in candidates) {
            val spec = candidate.spec
            val box = candidate.raw.boundingBox
            val confidence = candidate.confidence

            if (spec.cocoLabel == "person") {
                val otherStrongPersons = strongPersonBoxes.filter { it.first != box }
                if (personShadowFilter.isLikelyShadow(
                        candidate = candidate.raw,
                        confidence = confidence,
                        primaryPersonBoxes = otherStrongPersons,
                        imageLabels = imageLabels,
                        imageHeight = imageHeight
                    )
                ) {
                    continue
                }
            } else if (spec.cocoLabel in ANIMAL_COCO_CLASSES) {
                val personBoxes = strongPersonBoxes.map { it.first }
                if (clothingAnimalDisambiguator.isLikelyClothingFalsePositive(
                        candidate = candidate.raw,
                        confidence = confidence,
                        personBoxes = personBoxes,
                        imageLabels = imageLabels,
                        totalFrameArea = totalArea
                    )
                ) {
                    continue
                }
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
