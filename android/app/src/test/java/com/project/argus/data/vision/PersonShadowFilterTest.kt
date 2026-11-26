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

    private fun createObject(
        box: BoundingBox2D,
        stats: BoxAppearanceStats?
    ): RawDetectedObject {
        return RawDetectedObject(
            boundingBox = box,
            labels = listOf(RawLabel("person", 0.55f)),
            appearanceStats = stats
        )
    }

    @Test
    fun shadowFilter_darkFlatMonochromeBlob_isClassifiedAsShadow() {
        val shadowStats = BoxAppearanceStats(
            meanLuminance = 40f,
            luminanceStdDev = 12f,
            meanGradient = 7f,
            colorSpread = 6f,
            borderMeanLuminance = 90f,
            quadrantMeanSpread = 8f
        )
        val candidate = createObject(
            box = BoundingBox2D(100f, 400f, 300f, 600f),
            stats = shadowStats
        )

        val isShadow = filter.isLikelyShadow(
            candidate = candidate,
            confidence = 0.58f,
            primaryPersonBoxes = emptyList(),
            imageLabels = emptyList(),
            imageHeight = 1000
        )

        assertTrue(isShadow)
    }

    @Test
    fun shadowFilter_realPersonWithRichTextureAndColor_isNotShadow() {
        val personStats = BoxAppearanceStats(
            meanLuminance = 125f,
            luminanceStdDev = 38f,
            meanGradient = 24f,
            colorSpread = 32f,
            borderMeanLuminance = 110f,
            quadrantMeanSpread = 35f
        )
        val candidate = createObject(
            box = BoundingBox2D(200f, 100f, 600f, 800f),
            stats = personStats
        )

        val isShadow = filter.isLikelyShadow(
            candidate = candidate,
            confidence = 0.85f,
            primaryPersonBoxes = emptyList(),
            imageLabels = emptyList(),
            imageHeight = 1000
        )

        assertFalse(isShadow)
    }

    @Test
    fun shadowFilter_attachedCastShadowNearRealPerson_isClassifiedAsShadow() {
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

        val candidate = createObject(shadowBox, flatStats)

        val isShadow = filter.isLikelyShadow(
            candidate = candidate,
            confidence = 0.52f,
            primaryPersonBoxes = listOf(primaryPersonBox to 0.88f),
            imageLabels = emptyList(),
            imageHeight = 1000
        )

        assertTrue(isShadow)
    }

    @Test
    fun shadowFilter_twoRealPeopleNearEachOther_areNotSuppressed() {
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
        val candidate = createObject(person2Box, person2Stats)

        val isShadow = filter.isLikelyShadow(
            candidate = candidate,
            confidence = 0.82f,
            primaryPersonBoxes = listOf(person1Box to 0.85f),
            imageLabels = emptyList(),
            imageHeight = 1000
        )

        assertFalse(isShadow)
    }
}
