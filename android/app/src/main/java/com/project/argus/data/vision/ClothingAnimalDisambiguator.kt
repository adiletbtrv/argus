package com.project.argus.data.vision

import com.project.argus.domain.model.BoundingBox2D
import java.util.Locale

/**
 * Filters false animal detections caused by rough, furry, wool, or fleece clothing.
 * Never suppresses high-confidence animals (>= HIGH_ANIMAL_CONFIDENCE), animals confirmed by
 * scene labels, or pets walking next to a person outside the torso region.
 */
class ClothingAnimalDisambiguator {

    fun isLikelyClothingFalsePositive(
        candidate: RawDetectedObject,
        confidence: Float,
        personBoxes: List<BoundingBox2D>,
        imageLabels: List<RawLabel>,
        totalFrameArea: Float
    ): Boolean {
        // Hard safety invariant: never suppress high-confidence real animal detections.
        if (confidence >= HIGH_ANIMAL_CONFIDENCE) {
            return false
        }

        val box = candidate.boundingBox
        val animalArea = box.area()
        if (animalArea <= 0f) return false

        val (clothingScore, animalSceneScore) = computeSceneScores(imageLabels)

        // 1. Torso containment: fur/wool garment worn by a detected person.
        if (personBoxes.isNotEmpty() && animalSceneScore < 0.65f) {
            val insidePersonTorso = personBoxes.any { personBox ->
                val intersection = box.intersectionArea(personBox)
                val containment = intersection / animalArea
                val inUpperOrMidBody = box.centerY() <= personBox.top + personBox.height() * 0.78f
                containment >= 0.60f && inUpperOrMidBody
            }
            if (insidePersonTorso) {
                return true
            }
        }

        val maxPersonContainment = personBoxes.maxOfOrNull { personBox ->
            box.intersectionArea(personBox) / animalArea
        } ?: 0f

        // 2. Semantic scene disambiguation: clothing/textile context outweighs animal evidence.
        // When a person is present, clothing labels belong to the person unless the animal overlaps them.
        val clothingAppliesToCandidate = personBoxes.isEmpty() || maxPersonContainment >= 0.25f
        if (clothingAppliesToCandidate &&
            clothingScore >= 0.50f &&
            clothingScore > animalSceneScore + 0.10f
        ) {
            return true
        }

        // 3. Structural check: close-up uniform textured fabric fills a large patch without head/body contrast.
        val stats = candidate.appearanceStats
        val areaFraction = if (totalFrameArea > 0f) animalArea / totalFrameArea else 0f
        if (stats != null &&
            areaFraction >= 0.25f &&
            confidence < 0.72f &&
            animalSceneScore < 0.55f
        ) {
            val hasUniformMacroLuminance = stats.quadrantMeanSpread < 12f
            val hasRoughMicroTexture = stats.meanGradient >= 11f
            if (hasUniformMacroLuminance && hasRoughMicroTexture) {
                return true
            }
        }

        return false
    }

    private fun computeSceneScores(imageLabels: List<RawLabel>): Pair<Float, Float> {
        var explicitClothingScore = 0f
        var furScore = 0f
        var animalScore = 0f

        for (label in imageLabels) {
            val text = label.text.lowercase(Locale.ROOT)
            val conf = label.confidence

            when {
                isExplicitClothingLabel(text) -> {
                    if (conf > explicitClothingScore) explicitClothingScore = conf
                }
                text == "fur" || text.contains("fleece") -> {
                    if (conf > furScore) furScore = conf
                }
                isAnimalSceneLabel(text) -> {
                    if (conf > animalScore) animalScore = conf
                }
            }
        }

        // "Fur" alone also fires on real dogs/cats, so combine with clothing only when textile exists or animal is absent.
        val effectiveClothingScore = if (explicitClothingScore >= 0.40f || animalScore < 0.45f) {
            maxOf(explicitClothingScore, furScore)
        } else {
            explicitClothingScore
        }

        return effectiveClothingScore to animalScore
    }

    private fun isExplicitClothingLabel(text: String): Boolean {
        return text.contains("jacket") ||
            text.contains("outerwear") ||
            text.contains("coat") ||
            text.contains("sweater") ||
            text.contains("knit") ||
            text.contains("crochet") ||
            text.contains("textile") ||
            text.contains("wool") ||
            text.contains("cotton") ||
            text.contains("denim") ||
            text.contains("jeans") ||
            text.contains("blazer") ||
            text.contains("scarf") ||
            text.contains("dress") ||
            text.contains("jersey") ||
            text.contains("clothing") ||
            text.contains("apparel")
    }

    private fun isAnimalSceneLabel(text: String): Boolean {
        return text == "dog" ||
            text == "cat" ||
            text == "pet" ||
            text == "animal" ||
            text == "bird" ||
            text == "horse" ||
            text == "cattle" ||
            text == "bear" ||
            text == "duck" ||
            text == "waterfowl" ||
            text.contains("sheepdog") ||
            text.contains("terrier") ||
            text.contains("dalmatian") ||
            text.contains("hound") ||
            text.contains("ragdoll") ||
            text.contains("sphynx") ||
            text.contains("pixie-bob")
    }

    companion object {
        internal const val HIGH_ANIMAL_CONFIDENCE = 0.78f
    }
}
