package com.project.argus.data.vision

import com.project.argus.domain.model.BoundingBox2D
import com.project.argus.domain.model.DetectedObstacle
import com.project.argus.domain.model.ObstacleCatalog
import com.project.argus.domain.model.ObstacleGroup
import com.project.argus.domain.model.ObstacleSpec
import com.project.argus.domain.model.VisionFrameResult
import java.util.Locale

// Orchestrates post-processing: allowlist filtering, shadow/clothing disambiguation,
// temporal stabilization, NMS deduplication, spatial zoning, and threat prioritization.
class VisionResultProcessor(
    private val hazardClassifier: HazardClassifier = HazardClassifier(),
    private val spatialClassifier: SpatialClassifier = SpatialClassifier(),
    private val labelFormatter: LabelFormatter = LabelFormatter(),
    private val shadowFilter: PersonShadowFilter = PersonShadowFilter(),
    private val clothingDisambiguator: ClothingAnimalDisambiguator = ClothingAnimalDisambiguator(),
    private val temporalTracker: TemporalObjectTracker = TemporalObjectTracker()
) : VisionProcessor {

    companion object {
        val ALLOWED_OBJECT_CLASSES: Set<String> = ObstacleCatalog.allowedCocoClasses
        private const val PERSON_IMMEDIATE_CONFIDENCE = 0.62f
        private const val ANIMAL_IMMEDIATE_CONFIDENCE = 0.62f
        private const val COMPACT_IMMEDIATE_CONFIDENCE = 0.48f
        private const val STANDARD_IMMEDIATE_CONFIDENCE = 0.45f
        private const val DUPLICATE_IOU_THRESHOLD = 0.55f
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

        val totalArea = (imageWidth * imageHeight).toFloat()
        if (totalArea <= 0f) {
            return VisionFrameResult(
                obstacles = emptyList(),
                spatialHazards = hazardClassifier.classify(imageLabels, hasPhysicalObjects = false),
                rawLabels = rawLabels,
                frameWidth = imageWidth,
                frameHeight = imageHeight,
                timestamp = timestamp
            )
        }

        temporalTracker.pruneExpired(timestamp)

        // Stage 1: match COCO catalog spec, minimum confidence, and size-aware ignore threshold.
        data class Candidate(
            val raw: RawDetectedObject,
            val spec: ObstacleSpec,
            val confidence: Float
        )

        val candidates = detectedObjects.mapNotNull { obj ->
            val topLabel = obj.labels.firstOrNull() ?: return@mapNotNull null
            val spec = ObstacleCatalog.findSpec(topLabel.text) ?: return@mapNotNull null
            if (topLabel.confidence < spec.minConfidence) return@mapNotNull null
            if (spatialClassifier.isIgnored(obj.boundingBox.area(), totalArea, spec.isCompact)) {
                return@mapNotNull null
            }
            Candidate(obj, spec, topLabel.confidence)
        }

        // Stage 2: deduplicate overlapping boxes of the same COCO class before contextual filtering.
        val dedupedCandidates = mutableListOf<Candidate>()
        for (candidate in candidates.sortedByDescending { it.confidence }) {
            val isDuplicate = dedupedCandidates.any { kept ->
                kept.spec.cocoLabel == candidate.spec.cocoLabel &&
                    kept.raw.boundingBox.iou(candidate.raw.boundingBox) >= DUPLICATE_IOU_THRESHOLD
            }
            if (!isDuplicate) {
                dedupedCandidates.add(candidate)
            }
        }

        // Pre-identify strong non-shadow person candidates for attached-shadow and torso-clothing checks.
        val strongPersonBoxes = dedupedCandidates
            .filter { it.spec.group == ObstacleGroup.PERSON && it.confidence >= 0.65f }
            .filterNot {
                shadowFilter.isLikelyShadow(
                    candidate = it.raw,
                    confidence = it.confidence,
                    primaryPersonBoxes = emptyList(),
                    imageLabels = imageLabels,
                    imageHeight = imageHeight
                )
            }
            .map { it.raw.boundingBox to it.confidence }

        // Stage 3: apply shadow filter, clothing-animal disambiguator, and temporal consistency.
        val acceptedCandidates = mutableListOf<Candidate>()
        for (candidate in dedupedCandidates) {
            val spec = candidate.spec
            val box = candidate.raw.boundingBox

            if (spec.group == ObstacleGroup.PERSON) {
                val otherStrongPersons = strongPersonBoxes.filter { it.first != box }
                if (shadowFilter.isLikelyShadow(
                        candidate = candidate.raw,
                        confidence = candidate.confidence,
                        primaryPersonBoxes = otherStrongPersons,
                        imageLabels = imageLabels,
                        imageHeight = imageHeight
                    )
                ) {
                    continue
                }
            } else if (spec.group == ObstacleGroup.ANIMAL) {
                val personBoxes = strongPersonBoxes.map { it.first }
                if (clothingDisambiguator.isLikelyClothingFalsePositive(
                        candidate = candidate.raw,
                        confidence = candidate.confidence,
                        personBoxes = personBoxes,
                        imageLabels = imageLabels,
                        totalFrameArea = totalArea
                    )
                ) {
                    continue
                }
            }

            val immediateThreshold = when {
                spec.group == ObstacleGroup.PERSON -> PERSON_IMMEDIATE_CONFIDENCE
                spec.group == ObstacleGroup.ANIMAL -> ANIMAL_IMMEDIATE_CONFIDENCE
                spec.isCompact -> COMPACT_IMMEDIATE_CONFIDENCE
                else -> STANDARD_IMMEDIATE_CONFIDENCE
            }

            val confirmed = temporalTracker.isConfirmed(
                label = spec.cocoLabel,
                box = box,
                confidence = candidate.confidence,
                immediateThreshold = immediateThreshold,
                timestampMs = timestamp,
                frameWidth = imageWidth,
                frameHeight = imageHeight
            )
            if (confirmed) {
                acceptedCandidates.add(candidate)
            }
        }

        val hasFurniture = acceptedCandidates.any { it.spec.group == ObstacleGroup.FURNITURE }
        val hasVehicles = acceptedCandidates.any { it.spec.group == ObstacleGroup.VEHICLE }
        val hasTableware = acceptedCandidates.any {
            it.spec.cocoLabel in setOf("cup", "bowl", "wine glass", "bottle", "fork", "knife", "spoon")
        }

        val hazards = hazardClassifier.classify(
            imageLabels = imageLabels,
            hasPhysicalObjects = acceptedCandidates.isNotEmpty(),
            hasFurnitureObjects = hasFurniture,
            hasVehicleObjects = hasVehicles,
            hasTablewareObjects = hasTableware
        )

        val obstacles = acceptedCandidates.map { candidate ->
            val box = candidate.raw.boundingBox
            val zone = spatialClassifier.classifyZone(box.centerX(), imageWidth)
            val distanceCategory = spatialClassifier.classifyDistance(
                boxArea = box.area(),
                totalArea = totalArea,
                isCompact = candidate.spec.isCompact
            )
            val label = labelFormatter.format(candidate.spec.cocoLabel)

            DetectedObstacle(
                id = 0,
                label = label,
                confidence = candidate.confidence,
                boundingBox = box,
                zone = zone,
                estimatedDistanceCategory = distanceCategory,
                dangerPriority = candidate.spec.dangerPriority
            )
        }
            // Sort by proximity (NEAR -> MEDIUM -> FAR), then threat priority, area, and confidence.
            .sortedWith(
                compareBy<DetectedObstacle> { it.estimatedDistanceCategory.ordinal }
                    .thenByDescending { it.dangerPriority }
                    .thenByDescending { it.boundingBox.area() }
                    .thenByDescending { it.confidence }
            )
            .mapIndexed { index, obstacle -> obstacle.copy(id = index) }

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
