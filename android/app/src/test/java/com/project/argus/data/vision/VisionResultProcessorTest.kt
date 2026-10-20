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

    // SpatialClassifier: geometry and unified scale

    @Test
    fun `SpatialClassifier - classifyZone returns LEFT CENTER and RIGHT`() {
        val classifier = SpatialClassifier()
        val width = 900

        assertEquals(HorizontalZone.LEFT, classifier.classifyZone(centerX = 100f, imageWidth = width))
        assertEquals(HorizontalZone.CENTER, classifier.classifyZone(centerX = 450f, imageWidth = width))
        assertEquals(HorizontalZone.RIGHT, classifier.classifyZone(centerX = 800f, imageWidth = width))
    }

    @Test
    fun `SpatialClassifier - classifyDistance maps area fractions to NEAR MEDIUM FAR and ignores below 6 percent`() {
        val classifier = SpatialClassifier()
        val totalArea = 1_000_000f // 1000 x 1000

        // >= 0.28 -> NEAR
        assertEquals(DistanceCategory.NEAR, classifier.classifyDistance(boxArea = 300_000f, totalArea = totalArea))
        // 0.12..0.28 -> MEDIUM
        assertEquals(DistanceCategory.MEDIUM, classifier.classifyDistance(boxArea = 150_000f, totalArea = totalArea))
        // 0.06..0.12 -> FAR
        assertEquals(DistanceCategory.FAR, classifier.classifyDistance(boxArea = 80_000f, totalArea = totalArea))

        // < 0.06 -> Ignored completely
        assertTrue(classifier.isIgnored(boxArea = 50_000f, totalArea = totalArea))
        assertFalse(classifier.isIgnored(boxArea = 70_000f, totalArea = totalArea))
    }

    // LabelFormatter: string sanitization in isolation

    @Test
    fun `LabelFormatter - maps COCO labels to display form`() {
        val formatter = LabelFormatter()
        assertEquals("Person", formatter.format("person"))
        assertEquals("Dining table", formatter.format("dining table"))
        assertEquals("TV", formatter.format("tv"))
        assertEquals("Computer mouse", formatter.format("mouse"))
        assertEquals("Remote control", formatter.format("remote"))
        assertEquals("Ball", formatter.format("sports ball"))
        assertEquals("Hair dryer", formatter.format("hair drier"))
        assertEquals("Armrest", formatter.format("armrest"))
    }

    @Test
    fun `HazardClassifier - legacy ML Kit category is not treated as armrest`() {
        assertFalse(hazardClassifier.isArmrestObject("Home good", isArmrestDominant = true))
        assertTrue(hazardClassifier.isArmrestObject("couch", isArmrestDominant = true))
        assertTrue(hazardClassifier.isArmrestObject("chair", isArmrestDominant = true))
    }

    // VisionResultProcessor: allow-list and area filter

    @Test
    fun `VisionResultProcessor - filters allowed COCO classes only`() {
        val processor = VisionResultProcessor()
        assertTrue(processor.isAllowedObject("person"))
        assertTrue(processor.isAllowedObject("chair"))
        assertTrue(processor.isAllowedObject("dining table"))
        assertTrue(processor.isAllowedObject("bench"))
        assertTrue(processor.isAllowedObject("backpack"))
        assertTrue(processor.isAllowedObject("suitcase"))
        assertTrue(processor.isAllowedObject("potted plant"))
        assertTrue(processor.isAllowedObject("car"))
        assertTrue(processor.isAllowedObject("bicycle"))
        assertTrue(processor.isAllowedObject("dog"))

        assertFalse(processor.isAllowedObject("bottle"))
        assertFalse(processor.isAllowedObject("door"))
        assertFalse(processor.isAllowedObject("tv"))
    }

    @Test
    fun `VisionResultProcessor - drops unallowed classes and small objects below 6 percent`() {
        val processor = VisionResultProcessor()
        val objects = listOf(
            // Allowed class but area < 6% (30x30 = 900 on 1000x1000 = 0.09%) -> dropped
            RawDetectedObject(
                boundingBox = BoundingBox2D(100f, 100f, 130f, 130f),
                labels = listOf(RawLabel("chair", 0.90f))
            ),
            // Unallowed class (bottle) -> dropped
            RawDetectedObject(
                boundingBox = BoundingBox2D(100f, 100f, 500f, 500f),
                labels = listOf(RawLabel("bottle", 0.90f))
            ),
            // Allowed class (person) with area >= 6% (300x300 = 90000 on 1000x1000 = 9%) -> kept
            RawDetectedObject(
                boundingBox = BoundingBox2D(400f, 100f, 700f, 400f),
                labels = listOf(RawLabel("person", 0.85f))
            )
        )

        val result = processor.process(
            detectedObjects = objects,
            imageLabels = emptyList(),
            imageWidth = 1000,
            imageHeight = 1000
        )

        assertEquals(1, result.obstacles.size)
        assertEquals("Person", result.obstacles[0].label)
    }

    @Test
    fun `VisionResultProcessor - obstacle label remapped to Armrest when armrest dominant`() {
        val processor = VisionResultProcessor()
        val labels = listOf(RawLabel("Armrest", 0.50f))
        val objects = listOf(
            RawDetectedObject(
                boundingBox = BoundingBox2D(100f, 100f, 400f, 400f),
                labels = listOf(RawLabel("chair", 0.70f))
            )
        )

        val result = processor.process(
            detectedObjects = objects,
            imageLabels = labels,
            imageWidth = 1000,
            imageHeight = 1000
        )

        assertEquals(1, result.obstacles.size)
        assertEquals("Armrest", result.obstacles[0].label)
        assertTrue(result.obstacles[0].isArmrest)
    }

    @Test
    fun `VisionResultProcessor - full pipeline produces correct frame result`() {
        val processor = VisionResultProcessor()
        val objects = listOf(
            RawDetectedObject(
                boundingBox = BoundingBox2D(left = 50f, top = 100f, right = 350f, bottom = 400f),
                labels = listOf(RawLabel("chair", 0.8f))
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
