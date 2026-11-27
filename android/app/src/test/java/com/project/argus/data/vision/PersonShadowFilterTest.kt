package com.project.argus.data.vision

import com.project.argus.domain.model.BoundingBox2D
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PersonShadowFilterTest {

    private lateinit var filter: PersonShadowFilter

    @Before
    fun setUp() {
        filter = PersonShadowFilter()
    }

    private fun createPersonCandidate(
        box: BoundingBox2D,
        confidence: Float,
        stats: BoxAppearanceStats?
    ): RawDetectedObject {
        return RawDetectedObject(
            boundingBox = box,
            labels = listOf(RawLabel("person", confidence)),
            appearanceStats = stats
        )
    }

    @Test
    fun darkFlatMonochromeBlob_isClassifiedAsShadow() {
        val shadowStats = BoxAppearanceStats(
            meanLuminance = 40f,
            luminanceStdDev = 12f,
            meanGradient = 7f,
            colorSpread = 6f,
            borderMeanLuminance = 90f,
            quadrantMeanSpread = 8f
        )
        val candidate = createPersonCandidate(
            box = BoundingBox2D(100f, 400f, 300f, 600f),
            confidence = 0.58f,
            stats = shadowStats
        )

        assertTrue(
            filter.isLikelyShadow(
                candidate = candidate,
                confidence = 0.58f,
                primaryPersonBoxes = emptyList(),
                imageLabels = emptyList(),
                imageHeight = 1000
            )
        )
    }

    @Test
    fun attachedCastShadowNearRealPerson_isClassifiedAsShadow() {
        val flatStats = BoxAppearanceStats(
            meanLuminance = 45f,
            luminanceStdDev = 14f,
            meanGradient = 9f,
            colorSpread = 8f,
            borderMeanLuminance = 85f,
            quadrantMeanSpread = 9f
        )
        val shadowBox = BoundingBox2D(left = 420f, top = 550f, right = 600f, bottom = 850f)
        val primaryPersonBox = BoundingBox2D(left = 250f, top = 200f, right = 450f, bottom = 850f)
        val candidate = createPersonCandidate(shadowBox, 0.52f, flatStats)

        assertTrue(
            filter.isLikelyShadow(
                candidate = candidate,
                confidence = 0.52f,
                primaryPersonBoxes = listOf(primaryPersonBox to 0.88f),
                imageLabels = emptyList(),
                imageHeight = 1000
            )
        )
    }

    @Test
    fun strongConfidencePerson_isNeverSuppressedEvenIfDarkAndFlat() {
        val darkFlatStats = BoxAppearanceStats(
            meanLuminance = 35f,
            luminanceStdDev = 8f,
            meanGradient = 5f,
            colorSpread = 4f,
            borderMeanLuminance = 100f,
            quadrantMeanSpread = 5f
        )
        val candidate = createPersonCandidate(
            box = BoundingBox2D(200f, 550f, 650f, 800f),
            confidence = PersonShadowFilter.STRONG_PERSON_CONFIDENCE,
            stats = darkFlatStats
        )

        assertFalse(
            "High-confidence person must never be suppressed",
            filter.isLikelyShadow(
                candidate = candidate,
                confidence = PersonShadowFilter.STRONG_PERSON_CONFIDENCE,
                primaryPersonBoxes = listOf(BoundingBox2D(10f, 200f, 210f, 800f) to 0.95f),
                imageLabels = listOf(RawLabel("Asphalt", 0.90f)),
                imageHeight = 1000
            )
        )
    }

    @Test
    fun realPersonInDarkClothingWithNormalTextureAndContrast_isNotSuppressed() {
        val darkCoatWithTexture = BoxAppearanceStats(
            meanLuminance = 46f,
            luminanceStdDev = 26f,
            meanGradient = 16f,
            colorSpread = 9f,
            borderMeanLuminance = 135f,
            quadrantMeanSpread = 24f
        )
        val candidate = createPersonCandidate(
            box = BoundingBox2D(280f, 150f, 560f, 820f),
            confidence = 0.62f,
            stats = darkCoatWithTexture
        )

        assertFalse(
            "Real person in dark clothing with normal internal contrast must not be dropped",
            filter.isLikelyShadow(
                candidate = candidate,
                confidence = 0.62f,
                primaryPersonBoxes = emptyList(),
                imageLabels = listOf(RawLabel("Asphalt", 0.75f)),
                imageHeight = 1000
            )
        )
    }

    @Test
    fun realPersonWithRichTextureAndColor_isNotSuppressed() {
        val personStats = BoxAppearanceStats(
            meanLuminance = 125f,
            luminanceStdDev = 38f,
            meanGradient = 24f,
            colorSpread = 32f,
            borderMeanLuminance = 110f,
            quadrantMeanSpread = 35f
        )
        val candidate = createPersonCandidate(
            box = BoundingBox2D(200f, 100f, 600f, 800f),
            confidence = 0.85f,
            stats = personStats
        )

        assertFalse(
            filter.isLikelyShadow(
                candidate = candidate,
                confidence = 0.85f,
                primaryPersonBoxes = emptyList(),
                imageLabels = emptyList(),
                imageHeight = 1000
            )
        )
    }

    @Test
    fun twoRealPeopleStandingNearEachOther_areNotSuppressed() {
        val person1Box = BoundingBox2D(left = 150f, top = 150f, right = 400f, bottom = 800f)
        val person2Box = BoundingBox2D(left = 420f, top = 150f, right = 680f, bottom = 800f)
        val person2Stats = BoxAppearanceStats(
            meanLuminance = 110f,
            luminanceStdDev = 35f,
            meanGradient = 22f,
            colorSpread = 28f,
            borderMeanLuminance = 105f,
            quadrantMeanSpread = 25f
        )
        val candidate = createPersonCandidate(person2Box, 0.82f, person2Stats)

        assertFalse(
            filter.isLikelyShadow(
                candidate = candidate,
                confidence = 0.82f,
                primaryPersonBoxes = listOf(person1Box to 0.85f),
                imageLabels = emptyList(),
                imageHeight = 1000
            )
        )
    }

    @Test
    fun surfaceSceneConflictWithFlatDarkBlob_suppressesShadow() {
        val flatSurfaceShadow = BoxAppearanceStats(
            meanLuminance = 60f,
            luminanceStdDev = 20f,
            meanGradient = 10f,
            colorSpread = 15f,
            borderMeanLuminance = 85f,
            quadrantMeanSpread = 10f
        )
        val candidate = createPersonCandidate(
            box = BoundingBox2D(250f, 300f, 450f, 700f),
            confidence = 0.56f,
            stats = flatSurfaceShadow
        )

        assertTrue(
            filter.isLikelyShadow(
                candidate = candidate,
                confidence = 0.56f,
                primaryPersonBoxes = emptyList(),
                imageLabels = listOf(RawLabel("Floor", 0.78f)),
                imageHeight = 1000
            )
        )
    }
}
