package com.project.argus.data.vision

import com.project.argus.domain.model.BoundingBox2D
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BitmapAppearanceExtractorTest {

    @Test
    fun extractFromRgbPixels_returnsNullForInvalidDimensions() {
        val stats = BitmapAppearanceExtractor.extractFromRgbPixels(
            pixels = IntArray(10),
            width = 2,
            height = 5,
            box = BoundingBox2D(0f, 0f, 10f, 10f)
        )
        assertNull(stats)
    }

    @Test
    fun extractFromRgbPixels_uniformPatch_producesNearZeroLuminanceVariance() {
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
}
