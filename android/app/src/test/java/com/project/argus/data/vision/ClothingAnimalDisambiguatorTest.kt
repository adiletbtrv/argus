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

    private fun createAnimalObject(
        box: BoundingBox2D,
        stats: BoxAppearanceStats? = null
    ): RawDetectedObject {
        return RawDetectedObject(
            boundingBox = box,
            labels = listOf(RawLabel("cat", 0.62f)),
            appearanceStats = stats
        )
    }

    @Test
    fun disambiguator_roughClothingInsidePersonTorso_isSuppressed() {
        val personBox = BoundingBox2D(left = 200f, top = 100f, right = 600f, bottom = 900f)
        val furJacketBox = BoundingBox2D(left = 240f, top = 250f, right = 560f, bottom = 650f)
        val candidate = createAnimalObject(furJacketBox)

        val isClothing = disambiguator.isLikelyClothingFalsePositive(
            candidate = candidate,
            confidence = 0.62f,
            personBoxes = listOf(personBox),
            imageLabels = listOf(RawLabel("Jacket", 0.70f)),
            totalFrameArea = 1000f * 1000f
        )

        assertTrue(isClothing)
    }

    @Test
    fun disambiguator_realPetOnFloor_isNotSuppressed() {
        val personBox = BoundingBox2D(left = 100f, top = 100f, right = 400f, bottom = 800f)
        val realDogBox = BoundingBox2D(left = 550f, top = 600f, right = 850f, bottom = 950f)
        val candidate = createAnimalObject(realDogBox)

        val isClothing = disambiguator.isLikelyClothingFalsePositive(
            candidate = candidate,
            confidence = 0.74f,
            personBoxes = listOf(personBox),
            imageLabels = listOf(RawLabel("Dog", 0.85f), RawLabel("Pet", 0.80f)),
            totalFrameArea = 1000f * 1000f
        )

        assertFalse(isClothing)
    }

    @Test
    fun disambiguator_textileContextOutweighingAnimalContext_suppressesFalseAnimal() {
        val candidateBox = BoundingBox2D(left = 300f, top = 300f, right = 700f, bottom = 700f)
        val candidate = createAnimalObject(candidateBox)

        val isClothing = disambiguator.isLikelyClothingFalsePositive(
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

        assertTrue(isClothing)
    }

    @Test
    fun disambiguator_highConfidenceRealAnimalWithoutPerson_isNotSuppressed() {
        val candidateBox = BoundingBox2D(left = 200f, top = 200f, right = 800f, bottom = 800f)
        val candidate = createAnimalObject(candidateBox)

        val isClothing = disambiguator.isLikelyClothingFalsePositive(
            candidate = candidate,
            confidence = 0.88f,
            personBoxes = emptyList(),
            imageLabels = listOf(RawLabel("Animal", 0.80f)),
            totalFrameArea = 1000f * 1000f
        )

        assertFalse(isClothing)
    }
}
