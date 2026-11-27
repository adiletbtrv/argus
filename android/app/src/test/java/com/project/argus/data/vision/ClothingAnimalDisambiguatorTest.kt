package com.project.argus.data.vision

import com.project.argus.domain.model.BoundingBox2D
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ClothingAnimalDisambiguatorTest {

    private lateinit var disambiguator: ClothingAnimalDisambiguator

    @Before
    fun setUp() {
        disambiguator = ClothingAnimalDisambiguator()
    }

    private fun createAnimalCandidate(
        label: String = "cat",
        confidence: Float = 0.62f,
        box: BoundingBox2D,
        stats: BoxAppearanceStats? = null
    ): RawDetectedObject {
        return RawDetectedObject(
            boundingBox = box,
            labels = listOf(RawLabel(label, confidence)),
            appearanceStats = stats
        )
    }

    @Test
    fun roughClothingInsidePersonTorso_isSuppressed() {
        val personBox = BoundingBox2D(left = 200f, top = 100f, right = 600f, bottom = 900f)
        val furJacketBox = BoundingBox2D(left = 240f, top = 250f, right = 560f, bottom = 650f)
        val candidate = createAnimalCandidate(box = furJacketBox)

        assertTrue(
            disambiguator.isLikelyClothingFalsePositive(
                candidate = candidate,
                confidence = 0.62f,
                personBoxes = listOf(personBox),
                imageLabels = listOf(RawLabel("Jacket", 0.70f)),
                totalFrameArea = 1000f * 1000f
            )
        )
    }

    @Test
    fun textileContextOutweighingAnimalContext_suppressesFalseAnimal() {
        val candidateBox = BoundingBox2D(left = 300f, top = 300f, right = 700f, bottom = 700f)
        val candidate = createAnimalCandidate(confidence = 0.60f, box = candidateBox)

        assertTrue(
            disambiguator.isLikelyClothingFalsePositive(
                candidate = candidate,
                confidence = 0.60f,
                personBoxes = emptyList(),
                imageLabels = listOf(
                    RawLabel("Wool", 0.75f),
                    RawLabel("Sweater", 0.70f),
                    RawLabel("Textile", 0.68f)
                ),
                totalFrameArea = 1000f * 1000f
            )
        )
    }

    @Test
    fun largeUniformTexturedFabricPatch_isSuppressedWhenNoAnimalSceneContext() {
        val fabricStats = BoxAppearanceStats(
            meanLuminance = 100f,
            luminanceStdDev = 18f,
            meanGradient = 15f,
            colorSpread = 12f,
            borderMeanLuminance = 95f,
            quadrantMeanSpread = 8f
        )
        // 600 x 500 = 300_000 / 1_000_000 = 30% >= 25%
        val largeFabricBox = BoundingBox2D(200f, 200f, 800f, 700f)
        val candidate = createAnimalCandidate(confidence = 0.65f, box = largeFabricBox, stats = fabricStats)

        assertTrue(
            disambiguator.isLikelyClothingFalsePositive(
                candidate = candidate,
                confidence = 0.65f,
                personBoxes = emptyList(),
                imageLabels = emptyList(),
                totalFrameArea = 1000f * 1000f
            )
        )
    }

    @Test
    fun highConfidenceRealAnimal_isNeverSuppressedEvenWithClothingLabels() {
        val personBox = BoundingBox2D(left = 200f, top = 100f, right = 600f, bottom = 900f)
        val petInArmsBox = BoundingBox2D(left = 260f, top = 300f, right = 540f, bottom = 620f)
        val candidate = createAnimalCandidate(
            label = "dog",
            confidence = ClothingAnimalDisambiguator.HIGH_ANIMAL_CONFIDENCE,
            box = petInArmsBox
        )

        assertFalse(
            "High-confidence animal must never be suppressed",
            disambiguator.isLikelyClothingFalsePositive(
                candidate = candidate,
                confidence = ClothingAnimalDisambiguator.HIGH_ANIMAL_CONFIDENCE,
                personBoxes = listOf(personBox),
                imageLabels = listOf(RawLabel("Jacket", 0.85f), RawLabel("Wool", 0.80f)),
                totalFrameArea = 1000f * 1000f
            )
        )
    }

    @Test
    fun realAnimalWithConfirmedAnimalSceneContext_isNotSuppressed() {
        val personBox = BoundingBox2D(left = 200f, top = 100f, right = 600f, bottom = 900f)
        val heldPetBox = BoundingBox2D(left = 260f, top = 300f, right = 540f, bottom = 620f)
        val candidate = createAnimalCandidate(label = "cat", confidence = 0.66f, box = heldPetBox)

        assertFalse(
            "Confirmed animal scene context must protect real animal",
            disambiguator.isLikelyClothingFalsePositive(
                candidate = candidate,
                confidence = 0.66f,
                personBoxes = listOf(personBox),
                imageLabels = listOf(
                    RawLabel("Cat", 0.82f),
                    RawLabel("Fur", 0.78f),
                    RawLabel("Jacket", 0.60f)
                ),
                totalFrameArea = 1000f * 1000f
            )
        )
    }

    @Test
    fun dogWalkingBesidePersonOutsideTorso_isNotSuppressed() {
        val personBox = BoundingBox2D(left = 100f, top = 100f, right = 400f, bottom = 800f)
        val realDogBox = BoundingBox2D(left = 550f, top = 600f, right = 850f, bottom = 950f)
        val candidate = createAnimalCandidate(label = "dog", confidence = 0.70f, box = realDogBox)

        assertFalse(
            "Dog beside person outside torso must remain detected",
            disambiguator.isLikelyClothingFalsePositive(
                candidate = candidate,
                confidence = 0.70f,
                personBoxes = listOf(personBox),
                imageLabels = listOf(RawLabel("Dog", 0.85f), RawLabel("Pet", 0.80f)),
                totalFrameArea = 1000f * 1000f
            )
        )
    }

    @Test
    fun dogWalkingBesidePersonWearingJacket_isNotSuppressedByPersonsJacketLabel() {
        val personBox = BoundingBox2D(left = 100f, top = 100f, right = 450f, bottom = 900f)
        val realDogBox = BoundingBox2D(left = 600f, top = 550f, right = 920f, bottom = 900f)
        val candidate = createAnimalCandidate(label = "dog", confidence = 0.72f, box = realDogBox)

        assertFalse(
            "Person's jacket label must not suppress a separate dog outside person box",
            disambiguator.isLikelyClothingFalsePositive(
                candidate = candidate,
                confidence = 0.72f,
                personBoxes = listOf(personBox),
                imageLabels = listOf(RawLabel("Jacket", 0.75f), RawLabel("Pet", 0.50f)),
                totalFrameArea = 1000f * 1000f
            )
        )
    }
}
