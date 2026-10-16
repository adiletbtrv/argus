package com.project.argus.data.vision

import com.project.argus.domain.model.BoundingBox2D
import com.project.argus.domain.model.DistanceCategory
import com.project.argus.domain.model.HorizontalZone
import com.project.argus.domain.model.SpatialHazard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for the vision pipeline components:
 * [HazardClassifier], [SpatialClassifier], [LabelFormatter], and [VisionResultProcessor].
 * Each class is tested in isolation per [SRP].
 */
class VisionResultProcessorTest {

    private lateinit var hazardClassifier: HazardClassifier

    @Before
    fun setUp() {
        hazardClassifier = HazardClassifier()
    }

    @Test
    fun `HazardClassifier - armrest confidence at or above 40 percent marks armrest dominant`() {
        val labels = listOf(
            RawLabel(text = "Armrest", confidence = 0.45f),
            RawLabel(text = "Stairs", confidence = 0.60f)
        )

        val hazards = hazardClassifier.classify(labels, hasPhysicalObjects = false)

        val armrestHazard = hazards.filterIsInstance<SpatialHazard.ArmrestDetected>().firstOrNull()
        val stairsHazard = hazards.filterIsInstance<SpatialHazard.StairsAhead>().firstOrNull()

        assertTrue("Armrest hazard should be detected", armrestHazard != null)
        assertEquals(0.45f, armrestHazard!!.confidence, 0.001f)
        assertTrue("Stairs should be suppressed by armrest dominance", stairsHazard == null)
    }

    @Test
    fun `HazardClassifier - high furniture confidence with moderate stairs confidence favors armrest`() {
        val labels = listOf(
            RawLabel(text = "Couch", confidence = 0.65f),
            RawLabel(text = "Stairs", confidence = 0.68f)
        )

        val hazards = hazardClassifier.classify(labels, hasPhysicalObjects = false)

        assertTrue(hazards.any { it is SpatialHazard.ArmrestDetected })
        assertFalse(hazards.any { it is SpatialHazard.StairsAhead })
    }

    @Test
    fun `HazardClassifier - real stairs detected when confidence high and exceeds armrest by 20 percent`() {
        val labels = listOf(
            RawLabel(text = "Stairs", confidence = 0.85f),
            RawLabel(text = "Chair", confidence = 0.20f)
        )

        val hazards = hazardClassifier.classify(labels, hasPhysicalObjects = false)

        val stairsHazard = hazards.filterIsInstance<SpatialHazard.StairsAhead>().firstOrNull()
        assertTrue("Stairs should be detected", stairsHazard != null)
        assertEquals(0.85f, stairsHazard!!.confidence, 0.001f)
        assertFalse(hazards.any { it is SpatialHazard.ArmrestDetected })
    }

    @Test
    fun `HazardClassifier - wall proximity detected when confidence high and no physical objects`() {
        val labels = listOf(RawLabel(text = "Wall", confidence = 0.75f))

        val hazards = hazardClassifier.classify(labels, hasPhysicalObjects = false)

        val wallHazard = hazards.filterIsInstance<SpatialHazard.WallProximity>().firstOrNull()
        assertTrue("Wall proximity should be detected", wallHazard != null)
        assertEquals(0.75f, wallHazard!!.confidence, 0.001f)
    }

    @Test
    fun `HazardClassifier - wall proximity suppressed when physical objects are present`() {
        val labels = listOf(RawLabel(text = "Wall", confidence = 0.80f))

        val hazards = hazardClassifier.classify(labels, hasPhysicalObjects = true)

        assertFalse("Wall proximity should be suppressed when objects are present",
            hazards.any { it is SpatialHazard.WallProximity })
    }

    @Test
    fun `HazardClassifier - door ahead detected when confidence at or above 55 percent`() {
        val labels = listOf(RawLabel(text = "Door", confidence = 0.60f))

        val hazards = hazardClassifier.classify(labels, hasPhysicalObjects = false)

        val doorHazard = hazards.filterIsInstance<SpatialHazard.DoorAhead>().firstOrNull()
        assertTrue("Door ahead should be detected", doorHazard != null)
        assertEquals(0.60f, doorHazard!!.confidence, 0.001f)
    }

    //SpatialClassifier — geometry in isolation

    @Test
    fun `SpatialClassifier - classifyZone returns LEFT CENTER and RIGHT`() {
        val classifier = SpatialClassifier()
        val width = 900

        assertEquals(HorizontalZone.LEFT, classifier.classifyZone(centerX = 100f, imageWidth = width))
        assertEquals(HorizontalZone.CENTER, classifier.classifyZone(centerX = 450f, imageWidth = width))
        assertEquals(HorizontalZone.RIGHT, classifier.classifyZone(centerX = 800f, imageWidth = width))
    }

    @Test
    fun `SpatialClassifier - classifyDistance maps area fractions to NEAR MEDIUM FAR`() {
        val classifier = SpatialClassifier()
        val totalArea = 1_000_000f // 1000 x 1000

        assertEquals(DistanceCategory.NEAR, classifier.classifyDistance(boxArea = 360_000f, totalArea = totalArea))   // 36%
        assertEquals(DistanceCategory.MEDIUM, classifier.classifyDistance(boxArea = 160_000f, totalArea = totalArea)) // 16%
        assertEquals(DistanceCategory.FAR, classifier.classifyDistance(boxArea = 40_000f, totalArea = totalArea))     // 4%
    }

    // LabelFormatter — string sanitization in isolation

    @Test
    fun `LabelFormatter - formats Home good to Furniture and capitalizes words`() {
        val formatter = LabelFormatter()
        assertEquals("Furniture", formatter.format("Home good"))
        assertEquals("Furniture", formatter.format("home good"))
        assertEquals("Table", formatter.format("table"))
        assertEquals("Armrest", formatter.format("armrest"))
    }

    // [SRP] VisionResultProcessor — orchestration integration test

    @Test
    fun `VisionResultProcessor - obstacle label remapped to Armrest when armrest dominant`() {
        val processor = VisionResultProcessor()
        val labels = listOf(RawLabel("Armrest", 0.50f))
        val objects = listOf(
            RawDetectedObject(
                boundingBox = BoundingBox2D(100f, 100f, 300f, 300f),
                labels = listOf(RawLabel("couch", 0.70f))
            ),
            RawDetectedObject(
                boundingBox = BoundingBox2D(400f, 100f, 600f, 300f),
                labels = listOf(RawLabel("Bottle", 0.70f))
            )
        )

        val result = processor.process(
            detectedObjects = objects,
            imageLabels = labels,
            imageWidth = 1000,
            imageHeight = 1000
        )

        assertEquals("Armrest", result.obstacles[0].label)
        assertTrue(result.obstacles[0].isArmrest)
        assertEquals("Bottle", result.obstacles[1].label)
        assertFalse(result.obstacles[1].isArmrest)
    }

    @Test
    fun `VisionResultProcessor - full pipeline produces correct frame result`() {
        val processor = VisionResultProcessor()
        val objects = listOf(
            RawDetectedObject(
                boundingBox = BoundingBox2D(left = 50f, top = 100f, right = 200f, bottom = 300f),
                labels = listOf(RawLabel("Chair", 0.8f))
            )
        )
        val labels = listOf(RawLabel("Door", 0.65f))

        val result = processor.process(
            detectedObjects = objects,
            imageLabels = labels,
            imageWidth = 900,
            imageHeight = 900
        )

        assertEquals(1, result.obstacles.size)
        assertEquals(HorizontalZone.LEFT, result.obstacles[0].zone)
        assertTrue(result.spatialHazards.any { it is SpatialHazard.DoorAhead })
    }
}
