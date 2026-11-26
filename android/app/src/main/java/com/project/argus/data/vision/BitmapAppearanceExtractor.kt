package com.project.argus.data.vision

import android.graphics.Bitmap
import com.project.argus.domain.model.BoundingBox2D
import kotlin.math.abs
import kotlin.math.sqrt

// Extracts low-cost appearance statistics on a 16x16 grid to distinguish shadows and fabric from real obstacles.
object BitmapAppearanceExtractor {

    private const val GRID_SIZE = 16

    fun extractFromBitmap(bitmap: Bitmap, box: BoundingBox2D): BoxAppearanceStats? {
        if (bitmap.isRecycled || bitmap.width <= 1 || bitmap.height <= 1) return null
        val w = bitmap.width
        val h = bitmap.height

        val left = box.left.toInt().coerceIn(0, w - 1)
        val top = box.top.toInt().coerceIn(0, h - 1)
        val right = box.right.toInt().coerceIn(left + 1, w)
        val bottom = box.bottom.toInt().coerceIn(top + 1, h)
        val boxW = right - left
        val boxH = bottom - top
        if (boxW < 4 || boxH < 4) return null

        val gridPixels = IntArray(GRID_SIZE * GRID_SIZE)
        for (gy in 0 until GRID_SIZE) {
            val py = (top + ((gy + 0.5f) * boxH / GRID_SIZE).toInt()).coerceIn(0, h - 1)
            for (gx in 0 until GRID_SIZE) {
                val px = (left + ((gx + 0.5f) * boxW / GRID_SIZE).toInt()).coerceIn(0, w - 1)
                gridPixels[gy * GRID_SIZE + gx] = bitmap.getPixel(px, py)
            }
        }

        val borderLumas = collectBorderLuminances(
            width = w,
            height = h,
            left = left,
            top = top,
            right = right,
            bottom = bottom
        ) { px, py -> luma(bitmap.getPixel(px, py)) }

        return computeStatsFromGrid(gridPixels, borderLumas)
    }

    // Pure-JVM entrypoint accepting ARGB pixel array so extraction logic can be unit tested without Android Bitmap.
    fun extractFromRgbPixels(
        pixels: IntArray,
        width: Int,
        height: Int,
        box: BoundingBox2D
    ): BoxAppearanceStats? {
        if (width <= 1 || height <= 1 || pixels.size < width * height) return null

        val left = box.left.toInt().coerceIn(0, width - 1)
        val top = box.top.toInt().coerceIn(0, height - 1)
        val right = box.right.toInt().coerceIn(left + 1, width)
        val bottom = box.bottom.toInt().coerceIn(top + 1, height)
        val boxW = right - left
        val boxH = bottom - top
        if (boxW < 4 || boxH < 4) return null

        val gridPixels = IntArray(GRID_SIZE * GRID_SIZE)
        for (gy in 0 until GRID_SIZE) {
            val py = (top + ((gy + 0.5f) * boxH / GRID_SIZE).toInt()).coerceIn(0, height - 1)
            val rowOffset = py * width
            for (gx in 0 until GRID_SIZE) {
                val px = (left + ((gx + 0.5f) * boxW / GRID_SIZE).toInt()).coerceIn(0, width - 1)
                gridPixels[gy * GRID_SIZE + gx] = pixels[rowOffset + px]
            }
        }

        val borderLumas = collectBorderLuminances(
            width = width,
            height = height,
            left = left,
            top = top,
            right = right,
            bottom = bottom
        ) { px, py -> luma(pixels[py * width + px]) }

        return computeStatsFromGrid(gridPixels, borderLumas)
    }

    private inline fun collectBorderLuminances(
        width: Int,
        height: Int,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        sampleLuma: (Int, Int) -> Float
    ): List<Float> {
        val marginX = ((right - left) * 0.12f).toInt().coerceAtLeast(3)
        val marginY = ((bottom - top) * 0.12f).toInt().coerceAtLeast(3)
        val samples = ArrayList<Float>(16)

        val outerLeft = (left - marginX).coerceAtLeast(0)
        val outerRight = (right + marginX).coerceAtMost(width - 1)
        val outerTop = (top - marginY).coerceAtLeast(0)
        val outerBottom = (bottom + marginY).coerceAtMost(height - 1)

        val steps = 4
        for (i in 0 until steps) {
            val frac = (i + 0.5f) / steps
            val x = (left + ((right - left) * frac).toInt()).coerceIn(0, width - 1)
            val y = (top + ((bottom - top) * frac).toInt()).coerceIn(0, height - 1)

            if (left > 2) samples.add(sampleLuma(outerLeft, y))
            if (right < width - 2) samples.add(sampleLuma(outerRight, y))
            if (top > 2) samples.add(sampleLuma(x, outerTop))
            if (bottom < height - 2) samples.add(sampleLuma(x, outerBottom))
        }
        return samples
    }

    private fun computeStatsFromGrid(
        gridPixels: IntArray,
        borderLumas: List<Float>
    ): BoxAppearanceStats {
        val count = gridPixels.size
        val lumas = FloatArray(count)
        var sumLuma = 0f
        var sumChroma = 0f

        val quadSums = FloatArray(4)
        val quadCounts = IntArray(4)
        val half = GRID_SIZE / 2

        for (gy in 0 until GRID_SIZE) {
            for (gx in 0 until GRID_SIZE) {
                val argb = gridPixels[gy * GRID_SIZE + gx]
                val r = (argb shr 16) and 0xFF
                val g = (argb shr 8) and 0xFF
                val b = argb and 0xFF
                val y = 0.299f * r + 0.587f * g + 0.114f * b
                val idx = gy * GRID_SIZE + gx
                lumas[idx] = y
                sumLuma += y
                sumChroma += (abs(r - g) + abs(g - b)) * 0.5f

                val qIdx = (if (gy >= half) 2 else 0) + (if (gx >= half) 1 else 0)
                quadSums[qIdx] += y
                quadCounts[qIdx]++
            }
        }

        val meanLuma = sumLuma / count
        var varianceSum = 0f
        var gradientSum = 0f
        var gradientCount = 0

        for (gy in 0 until GRID_SIZE) {
            for (gx in 0 until GRID_SIZE) {
                val idx = gy * GRID_SIZE + gx
                val diff = lumas[idx] - meanLuma
                varianceSum += diff * diff

                if (gx + 1 < GRID_SIZE) {
                    gradientSum += abs(lumas[idx] - lumas[idx + 1])
                    gradientCount++
                }
                if (gy + 1 < GRID_SIZE) {
                    gradientSum += abs(lumas[idx] - lumas[idx + GRID_SIZE])
                    gradientCount++
                }
            }
        }

        val stdDev = sqrt((varianceSum / count).toDouble()).toFloat()
        val meanGrad = if (gradientCount > 0) gradientSum / gradientCount else 0f
        val colorSpread = sumChroma / count

        val borderMean = if (borderLumas.isNotEmpty()) {
            borderLumas.sum() / borderLumas.size
        } else {
            meanLuma
        }

        var minQuad = Float.MAX_VALUE
        var maxQuad = -Float.MAX_VALUE
        for (q in 0 until 4) {
            if (quadCounts[q] > 0) {
                val qMean = quadSums[q] / quadCounts[q]
                if (qMean < minQuad) minQuad = qMean
                if (qMean > maxQuad) maxQuad = qMean
            }
        }
        val quadSpread = if (minQuad <= maxQuad) maxQuad - minQuad else 0f

        return BoxAppearanceStats(
            meanLuminance = meanLuma,
            luminanceStdDev = stdDev,
            meanGradient = meanGrad,
            colorSpread = colorSpread,
            borderMeanLuminance = borderMean,
            quadrantMeanSpread = quadSpread
        )
    }

    private fun luma(argb: Int): Float {
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        return 0.299f * r + 0.587f * g + 0.114f * b
    }
}
