package com.project.argus.data.vision

import com.project.argus.domain.model.BoundingBox2D
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BitmapAppearanceExtractorTest {

    @Test
    fun extractFromRgbPixels_returnsNullForInvalidDimensionsOrTinyBox() {
        val invalidDimStats = BitmapAppearanceExtractor.extractFromRgbPixels(
            pixels = IntArray(10),
            width = 2,
            height = 5,
            box = BoundingBox2D(0f, 0f, 10f, 10f)
        )
        assertNull(invalidDimStats)

        val tinyBoxStats = BitmapAppearanceExtractor.extractFromRgbPixels(
            pixels = IntArray(100 * 100),
            width = 100,
            height = 100,
            box = BoundingBox2D(10f, 10f, 12f, 12f)
        )
        assertNull(tinyBoxStats)
    }

    @Test
    fun extractFromRgbPixels_uniformPatch_producesNearZeroVarianceAndColorSpread() {
        val width = 100
        val height = 100
        val uniformGray = (0xFF shl 24) or (0x40 shl 16) or (0x40 shl 8) or 0x40
        val pixels = IntArray(width * height) { uniformGray }

        val stats = BitmapAppearanceExtractor.extractFromRgbPixels(
            pixels = pixels,
            width = width,
            height = height,
            box = BoundingBox2D(20f, 20f, 80f, 80f)
        )

        assertNotNull(stats)
        assertTrue(stats!!.luminanceStdDev < 0.5f)
        assertTrue(stats.meanGradient < 0.5f)
        assertTrue(stats.colorSpread < 0.5f)
        assertTrue(stats.quadrantMeanSpread < 0.5f)
    }

    @Test
    fun extractFromRgbPixels_highContrastTexture_producesSignificantGradient() {
        val width = 100
        val height = 100
        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val isCheck = ((x / 4) + (y / 4)) % 2 == 0
                val c = if (isCheck) 0x10 else 0xE0
                pixels[y * width + x] = (0xFF shl 24) or (c shl 16) or (c shl 8) or c
            }
        }

        val stats = BitmapAppearanceExtractor.extractFromRgbPixels(
            pixels = pixels,
            width = width,
            height = height,
            box = BoundingBox2D(10f, 10f, 90f, 90f)
        )

        assertNotNull(stats)
        assertTrue(stats!!.luminanceStdDev > 50f)
        assertTrue(stats.meanGradient > 20f)
    }

    @Test
    fun extractFromRgbPixels_darkPatchOnBrightBackground_yieldsLowBrightnessRatio() {
        val width = 100
        val height = 100
        val brightBg = (0xFF shl 24) or (0xD0 shl 16) or (0xD0 shl 8) or 0xD0
        val darkBlob = (0xFF shl 24) or (0x28 shl 16) or (0x28 shl 8) or 0x28
        val pixels = IntArray(width * height) { brightBg }
        for (y in 25 until 75) {
            for (x in 25 until 75) {
                pixels[y * width + x] = darkBlob
            }
        }

        val stats = BitmapAppearanceExtractor.extractFromRgbPixels(
            pixels = pixels,
            width = width,
            height = height,
            box = BoundingBox2D(25f, 25f, 75f, 75f)
        )

        assertNotNull(stats)
        assertTrue("Expected low brightnessRatio for dark blob on bright floor", stats!!.brightnessRatio < 0.35f)
    }

    @Test
    fun extractFromRgbPixels_colorfulPatch_producesHighColorSpread() {
        val width = 100
        val height = 100
        val vividRed = (0xFF shl 24) or (0xE0 shl 16) or (0x20 shl 8) or 0x20
        val pixels = IntArray(width * height) { vividRed }

        val stats = BitmapAppearanceExtractor.extractFromRgbPixels(
            pixels = pixels,
            width = width,
            height = height,
            box = BoundingBox2D(20f, 20f, 80f, 80f)
        )

        assertNotNull(stats)
        assertTrue("Expected high colorSpread for saturated clothing", stats!!.colorSpread > 50f)
    }
}
