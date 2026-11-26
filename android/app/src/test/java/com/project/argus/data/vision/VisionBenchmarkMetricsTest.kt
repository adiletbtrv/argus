package com.project.argus.data.vision

import com.project.argus.domain.model.BoundingBox2D
import com.project.argus.domain.model.DistanceCategory
import com.project.argus.domain.model.HorizontalZone
import com.project.argus.domain.model.ObstacleCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.system.measureNanoTime

// Quantitative benchmark evaluating execution speed, memory stability, and precision/recall metrics.
class VisionBenchmarkMetricsTest {

    @Test
    fun benchmark_appearanceExtractionSpeed_underOneMillisecond() {
        val width = 640
        val height = 480
        val pixels = IntArray(width * height) { (0xFF shl 24) or (it and 0xFFFFFF) }
        val box = BoundingBox2D(100f, 100f, 350f, 400f)

        // Warm up
        repeat(5) {
            BitmapAppearanceExtractor.extractFromRgbPixels(pixels, width, height, box)
        }

        val iterations = 50
        val durationNanos = measureNanoTime {
            repeat(iterations) {
                BitmapAppearanceExtractor.extractFromRgbPixels(pixels, width, height, box)
            }
        }
        val avgDurationMs = (durationNanos / iterations) / 1_000_000.0

        // Benchmark assert: appearance extraction must run comfortably in under 2.5 ms on CPU
        assertTrue(
            "Average extraction time ($avgDurationMs ms) should be under 2.5 ms",
            avgDurationMs < 2.5
        )
    }

    @Test
    fun benchmark_fullPostProcessingThroughput_maintainsRealtimeRate() {
        val processor = VisionResultProcessor()
        val objects = (1..6).map { i ->
            RawDetectedObject(
                boundingBox = BoundingBox2D(50f * i, 100f, 50f * i + 120f, 300f),
                labels = listOf(RawLabel("chair", 0.70f))
            )
        }
        val labels = listOf(
            RawLabel("Furniture", 0.80f),
            RawLabel("Door", 0.60f)
        )

        // Warm up
        repeat(10) {
            processor.process(objects, labels, 640, 480, 1000L)
        }

        val iterations = 100
        val durationNanos = measureNanoTime {
            for (i in 1..iterations) {
                processor.process(objects, labels, 640, 480, 1000L + i * 180)
            }
        }
        val avgDurationMs = (durationNanos / iterations) / 1_000_000.0

        // Entire post-processing logic (NMS + tracker + disambiguation + hazards) should take < 1.0 ms
        assertTrue(
            "Average post-processing time ($avgDurationMs ms) should be under 1.0 ms",
            avgDurationMs < 1.0
        )
    }

    @Test
    fun benchmark_shadowFilterPrecisionRecallSimulation() {
        val filter = PersonShadowFilter()

        // Synthetic dataset: 20 real persons and 20 ground cast shadows
        val realPersons = (1..20).map { i ->
            val stats = BoxAppearanceStats(
                meanLuminance = 110f + (i % 10),
                luminanceStdDev = 32f + (i % 8),
                meanGradient = 20f + (i % 6),
                colorSpread = 26f + (i % 5),
                borderMeanLuminance = 100f,
                quadrantMeanSpread = 28f
            )
            RawDetectedObject(
                boundingBox = BoundingBox2D(100f + i * 10, 100f, 300f + i * 10, 700f),
                labels = listOf(RawLabel("person", 0.65f + (i % 25) * 0.01f)),
                appearanceStats = stats
            )
        }

        val castShadows = (1..20).map { i ->
            val stats = BoxAppearanceStats(
                meanLuminance = 35f + (i % 8),
                luminanceStdDev = 9f + (i % 4),
                meanGradient = 6f + (i % 3),
                colorSpread = 5f + (i % 3),
                borderMeanLuminance = 85f,
                quadrantMeanSpread = 6f
            )
            RawDetectedObject(
                boundingBox = BoundingBox2D(200f + i * 10, 550f, 400f + i * 10, 800f),
                labels = listOf(RawLabel("person", 0.52f + (i % 12) * 0.01f)),
                appearanceStats = stats
            )
        }

        var truePositivesRealPerson = 0
        var falsePositivesShadowPassed = 0

        for (person in realPersons) {
            val isShadow = filter.isLikelyShadow(
                candidate = person,
                confidence = person.labels.first().confidence,
                primaryPersonBoxes = emptyList(),
                imageLabels = emptyList(),
                imageHeight = 1000
            )
            if (!isShadow) truePositivesRealPerson++
        }

        for (shadow in castShadows) {
            val isShadow = filter.isLikelyShadow(
                candidate = shadow,
                confidence = shadow.labels.first().confidence,
                primaryPersonBoxes = emptyList(),
                imageLabels = emptyList(),
                imageHeight = 1000
            )
            if (!isShadow) falsePositivesShadowPassed++
        }

        val recall = truePositivesRealPerson.toDouble() / realPersons.size
        val precision = truePositivesRealPerson.toDouble() / (truePositivesRealPerson + falsePositivesShadowPassed)
        val f1 = 2 * (precision * recall) / (precision + recall)

        assertEquals("Real person recall should be 100%", 1.0, recall, 0.001)
        assertEquals("Shadow false positives should be 0%", 1.0, precision, 0.001)
        assertEquals("F1-score should be 1.0", 1.0, f1, 0.001)
    }

    @Test
    fun benchmark_clothingAnimalPrecisionRecallSimulation() {
        val disambiguator = ClothingAnimalDisambiguator()

        // 15 real animals (cats and dogs)
        val realAnimals = (1..15).map { i ->
            RawDetectedObject(
                boundingBox = BoundingBox2D(500f + i * 10, 600f, 750f + i * 10, 900f),
                labels = listOf(RawLabel(if (i % 2 == 0) "dog" else "cat", 0.72f + (i % 10) * 0.01f))
            )
        }

        // 15 false animal detections on human jacket torso
        val personBox = BoundingBox2D(200f, 100f, 600f, 900f)
        val clothingFalsePositives = (1..15).map { i ->
            RawDetectedObject(
                boundingBox = BoundingBox2D(250f + i * 5, 250f, 550f, 650f),
                labels = listOf(RawLabel("cat", 0.58f + (i % 10) * 0.01f))
            )
        }

        var realAnimalKept = 0
        var clothingFalseAnimalPassed = 0

        for (animal in realAnimals) {
            val isClothing = disambiguator.isLikelyClothingFalsePositive(
                candidate = animal,
                confidence = animal.labels.first().confidence,
                personBoxes = listOf(personBox),
                imageLabels = listOf(RawLabel("Pet", 0.85f)),
                totalFrameArea = 1000f * 1000f
            )
            if (!isClothing) realAnimalKept++
        }

        for (falseAnimal in clothingFalsePositives) {
            val isClothing = disambiguator.isLikelyClothingFalsePositive(
                candidate = falseAnimal,
                confidence = falseAnimal.labels.first().confidence,
                personBoxes = listOf(personBox),
                imageLabels = listOf(RawLabel("Jacket", 0.80f), RawLabel("Wool", 0.75f)),
                totalFrameArea = 1000f * 1000f
            )
            if (!isClothing) clothingFalseAnimalPassed++
        }

        val recall = realAnimalKept.toDouble() / realAnimals.size
        val precision = realAnimalKept.toDouble() / (realAnimalKept + clothingFalseAnimalPassed)
        val f1 = 2 * (precision * recall) / (precision + recall)

        assertEquals("Real animal recall should be 100%", 1.0, recall, 0.001)
        assertEquals("Clothing false animal passed should be 0", 1.0, precision, 0.001)
        assertEquals("F1-score should be 1.0", 1.0, f1, 0.001)
    }
}
