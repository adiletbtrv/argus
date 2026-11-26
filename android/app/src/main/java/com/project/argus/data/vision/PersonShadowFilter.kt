package com.project.argus.data.vision

import com.project.argus.domain.model.BoundingBox2D
import java.util.Locale
import kotlin.math.abs

// Suppresses false-positive "person" detections caused by human shadows without raising global confidence thresholds.
class PersonShadowFilter {

    fun isLikelyShadow(
        candidate: RawDetectedObject,
        confidence: Float,
        primaryPersonBoxes: List<Pair<BoundingBox2D, Float>>,
        imageLabels: List<RawLabel>,
        imageHeight: Int
    ): Boolean {
        val box = candidate.boundingBox
        val stats = candidate.appearanceStats

        // 1. Attached cast-shadow check: secondary lower-confidence box adjacent to a real person.
        if (confidence < ATTACHED_SHADOW_MAX_CONFIDENCE && primaryPersonBoxes.isNotEmpty()) {
            val attachedToRealPerson = primaryPersonBoxes.any { (primaryBox, primaryConf) ->
                primaryConf > confidence + 0.10f && isAdjacentGroundShadow(box, primaryBox, stats)
            }
            if (attachedToRealPerson) {
                return true
            }
        }

        // 2. Pixel appearance cues: cast shadows are darker than surroundings and internally flat/monochrome.
        if (stats != null) {
            val isDarkerThanBorder = stats.brightnessRatio < SHADOW_BRIGHTNESS_RATIO_MAX
            val isFlatTexture = stats.luminanceStdDev < SHADOW_MAX_LUMA_STDDEV &&
                stats.meanGradient < SHADOW_MAX_MEAN_GRADIENT
            val isMonochrome = stats.colorSpread < SHADOW_MAX_COLOR_SPREAD

            // Classic cast shadow on lit floor/wall (works across lighting levels via relative brightnessRatio).
            if (isDarkerThanBorder && isFlatTexture && isMonochrome && confidence < STRONG_PERSON_CONFIDENCE) {
                return true
            }

            // Very uniform, edge-less blob even under diffuse/indoor lighting when confidence is moderate.
            if (stats.luminanceStdDev < ULTRA_FLAT_STDDEV &&
                stats.meanGradient < ULTRA_FLAT_GRADIENT &&
                confidence < MODERATE_PERSON_CONFIDENCE
            ) {
                return true
            }

            // Elongated horizontal shadow lying in the lower ground region of the frame.
            val width = box.width()
            val height = box.height().coerceAtLeast(1f)
            val aspectRatio = width / height
            val isGroundRegion = imageHeight > 0 && box.centerY() > imageHeight * 0.55f
            if (isGroundRegion &&
                aspectRatio > 1.55f &&
                stats.meanGradient < 14f &&
                confidence < STRONG_PERSON_CONFIDENCE
            ) {
                return true
            }
        }

        // 3. Scene label corroboration: floor/asphalt/tile dominant with no human scene evidence and weak structure.
        if (confidence < SCENE_CONFLICT_MAX_CONFIDENCE && stats != null && stats.meanGradient < 13f) {
            var surfaceScore = 0f
            var humanSceneScore = 0f
            for (label in imageLabels) {
                val text = label.text.lowercase(Locale.ROOT)
                when {
                    text.contains("asphalt") || text.contains("tile") ||
                        text.contains("floor") || text.contains("road") ||
                        text.contains("sand") -> {
                        if (label.confidence > surfaceScore) surfaceScore = label.confidence
                    }
                    text.contains("person") || text.contains("human") ||
                        text.contains("dude") || text.contains("child") ||
                        text.contains("clothing") || text.contains("jacket") -> {
                        if (label.confidence > humanSceneScore) humanSceneScore = label.confidence
                    }
                }
            }
            if (surfaceScore >= 0.65f && humanSceneScore < 0.40f && stats.brightnessRatio < 0.85f) {
                return true
            }
        }

        return false
    }

    private fun isAdjacentGroundShadow(
        candidateBox: BoundingBox2D,
        primaryBox: BoundingBox2D,
        stats: BoxAppearanceStats?
    ): Boolean {
        val iou = candidateBox.iou(primaryBox)
        if (iou > 0.45f) return false // Same person duplicate box, not a separate cast shadow

        val touchesOrNearBottom = candidateBox.top >= primaryBox.centerY() &&
            candidateBox.centerY() > primaryBox.centerY()
        val horizontalGap = maxOf(
            0f,
            maxOf(candidateBox.left - primaryBox.right, primaryBox.left - candidateBox.right)
        )
        val closeHorizontally = horizontalGap <= primaryBox.width() * 0.4f
        val flatOrDark = stats == null || (stats.meanGradient < 14f && stats.brightnessRatio < 0.85f)

        return touchesOrNearBottom && closeHorizontally && flatOrDark
    }

    companion object {
        private const val STRONG_PERSON_CONFIDENCE = 0.78f
        private const val MODERATE_PERSON_CONFIDENCE = 0.70f
        private const val ATTACHED_SHADOW_MAX_CONFIDENCE = 0.66f
        private const val SCENE_CONFLICT_MAX_CONFIDENCE = 0.62f

        private const val SHADOW_BRIGHTNESS_RATIO_MAX = 0.76f
        private const val SHADOW_MAX_LUMA_STDDEV = 19f
        private const val SHADOW_MAX_MEAN_GRADIENT = 11f
        private const val SHADOW_MAX_COLOR_SPREAD = 13f

        private const val ULTRA_FLAT_STDDEV = 12f
        private const val ULTRA_FLAT_GRADIENT = 7.5f
    }
}
