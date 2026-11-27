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
    fun `HazardClassifier - armrest label suppresses false stairs when stairs confidence below 72 percent`() {
        val labels = listOf(
            RawLabel(text = "Armrest", confidence = 0.45f),
            RawLabel(text = "Stairs", confidence = 0.68f)
        )

        val hazards = hazardClassifier.classify(labels, hasPhysicalObjects = false)

        assertFalse("Stairs should be suppressed by armrest furniture context", hazards.any { it is SpatialHazard.StairsAhead })
        assertTrue("No spatial hazard should be emitted for furniture alone", hazards.isEmpty())
    }

    @Test
    fun `HazardClassifier - high furniture confidence suppresses moderate false stairs`() {
        val labels = listOf(
            RawLabel(text = "Couch", confidence = 0.65f),
            RawLabel(text = "Stairs", confidence = 0.68f)
        )

        val hazards = hazardClassifier.classify(labels, hasPhysicalObjects = false)

        assertFalse("Striped furniture upholstery must not trigger StairsAhead", hazards.any { it is SpatialHazard.StairsAhead })
        assertTrue(hazards.isEmpty())
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
    }

    @Test
    fun `HazardClassifier - strong stairs at or above 72 percent is not suppressed by background furniture`() {
        val labels = listOf(
            RawLabel(text = "Stairs", confidence = 0.82f),
            RawLabel(text = "Couch", confidence = 0.60f)
        )

        val hazards = hazardClassifier.classify(labels, hasPhysicalObjects = false)

        assertTrue(hazards.any { it is SpatialHazard.StairsAhead })
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

        assertFalse(
            "Wall proximity should be suppressed when objects are present",
            hazards.any { it is SpatialHazard.WallProximity }
        )
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

    @Test
    fun `SpatialClassifier - compact objects use 14 5 and 2 percent area thresholds`() {
        val classifier = SpatialClassifier()
        val totalArea = 1_000_000f

        assertEquals(DistanceCategory.NEAR, classifier.classifyDistance(150_000f, totalArea, isCompact = true))
        assertEquals(DistanceCategory.MEDIUM, classifier.classifyDistance(60_000f, totalArea, isCompact = true))
        assertEquals(DistanceCategory.FAR, classifier.classifyDistance(30_000f, totalArea, isCompact = true))
        assertTrue(classifier.isIgnored(15_000f, totalArea, isCompact = true))
        assertFalse(classifier.isIgnored(25_000f, totalArea, isCompact = true))
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
    }

    // VisionResultProcessor: curated allow-list, area filter, temporal tracking, and priority sorting

    @Test
    fun `VisionResultProcessor - filters curated 23 navigation COCO classes only`() {
        val processor = VisionResultProcessor()
        assertTrue(processor.isAllowedObject("person"))
        assertTrue(processor.isAllowedObject("car"))
        assertTrue(processor.isAllowedObject("bus"))
        assertTrue(processor.isAllowedObject("truck"))
        assertTrue(processor.isAllowedObject("motorcycle"))
        assertTrue(processor.isAllowedObject("bicycle"))
        assertTrue(processor.isAllowedObject("dog"))
        assertTrue(processor.isAllowedObject("cat"))
        assertTrue(processor.isAllowedObject("chair"))
        assertTrue(processor.isAllowedObject("couch"))
        assertTrue(processor.isAllowedObject("dining table"))
        assertTrue(processor.isAllowedObject("bench"))
        assertTrue(processor.isAllowedObject("bed"))
        assertTrue(processor.isAllowedObject("backpack"))
        assertTrue(processor.isAllowedObject("suitcase"))
        assertTrue(processor.isAllowedObject("potted plant"))
        assertTrue(processor.isAllowedObject("fire hydrant"))
        assertTrue(processor.isAllowedObject("stop sign"))
        assertTrue(processor.isAllowedObject("traffic light"))
        assertTrue(processor.isAllowedObject("parking meter"))
        assertTrue(processor.isAllowedObject("handbag"))
        assertTrue(processor.isAllowedObject("umbrella"))
        assertTrue(processor.isAllowedObject("sports ball"))

        assertFalse(processor.isAllowedObject("bottle"))
        assertFalse(processor.isAllowedObject("cup"))
        assertFalse(processor.isAllowedObject("donut"))
        assertFalse(processor.isAllowedObject("cell phone"))
        assertFalse(processor.isAllowedObject("saucer"))
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
    fun `VisionResultProcessor - sorts obstacles by dangerPriority desc distanceCategory asc confidence desc`() {
        val processor = VisionResultProcessor()
        val objects = listOf(
            // Priority 1 (sports ball, NEAR via compact >= 14%: 400x400 = 16%)
            RawDetectedObject(
                boundingBox = BoundingBox2D(300f, 300f, 700f, 700f),
                labels = listOf(RawLabel("sports ball", 0.95f))
            ),
            // Priority 2 (chair, NEAR >= 28%: 600x600 = 36%)
            RawDetectedObject(
                boundingBox = BoundingBox2D(200f, 200f, 800f, 800f),
                labels = listOf(RawLabel("chair", 0.92f))
            ),
            // Priority 3 (person, MEDIUM 12..28%: 400x400 = 16%)
            RawDetectedObject(
                boundingBox = BoundingBox2D(300f, 200f, 700f, 600f),
                labels = listOf(RawLabel("person", 0.78f))
            ),
            // Priority 3 (car, NEAR >= 28%: 600x500 = 30%)
            RawDetectedObject(
                boundingBox = BoundingBox2D(200f, 250f, 800f, 750f),
                labels = listOf(RawLabel("car", 0.80f))
            )
        )

        val result = processor.process(
            detectedObjects = objects,
            imageLabels = emptyList(),
            imageWidth = 1000,
            imageHeight = 1000,
            timestamp = 1000L
        )

        assertEquals(4, result.obstacles.size)
        assertEquals(listOf("Car", "Person", "Chair", "Ball"), result.obstacles.map { it.label })
        assertEquals(listOf(0, 1, 2, 3), result.obstacles.map { it.id })
    }

    @Test
    fun `VisionResultProcessor - weak non near detection requires two frames while near confirms immediately`() {
        val processor = VisionResultProcessor()
        val weakMediumChair = RawDetectedObject(
            boundingBox = BoundingBox2D(300f, 200f, 700f, 600f), // 16% -> MEDIUM
            labels = listOf(RawLabel("chair", 0.48f))
        )
        val weakNearChair = RawDetectedObject(
            boundingBox = BoundingBox2D(200f, 150f, 800f, 750f), // 36% -> NEAR
            labels = listOf(RawLabel("chair", 0.48f))
        )

        // Frame 1: weak MEDIUM chair is held pending confirmation
        val r1 = processor.process(listOf(weakMediumChair), emptyList(), 1000, 1000, timestamp = 1000L)
        assertTrue(r1.obstacles.isEmpty())

        // Frame 2 (within 750 ms): second matching hit confirms MEDIUM chair
        val r2 = processor.process(listOf(weakMediumChair), emptyList(), 1000, 1000, timestamp = 1180L)
        assertEquals(1, r2.obstacles.size)
        assertEquals("Chair", r2.obstacles[0].label)

        // Reset processor and verify weak NEAR chair confirms on very first frame
        processor.reset()
        val rNear = processor.process(listOf(weakNearChair), emptyList(), 1000, 1000, timestamp = 2000L)
        assertEquals(1, rNear.obstacles.size)
        assertEquals(DistanceCategory.NEAR, rNear.obstacles[0].estimatedDistanceCategory)
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

    @Test
    fun `VisionResultProcessor - filters cast shadow person while keeping real person`() {
        val processor = VisionResultProcessor()
        val realPerson = RawDetectedObject(
            boundingBox = BoundingBox2D(150f, 150f, 450f, 800f),
            labels = listOf(RawLabel("person", 0.88f)),
            appearanceStats = BoxAppearanceStats(
                meanLuminance = 115f,
                luminanceStdDev = 36f,
                meanGradient = 22f,
                colorSpread = 28f,
                borderMeanLuminance = 105f,
                quadrantMeanSpread = 30f
            )
        )
        val attachedShadow = RawDetectedObject(
            boundingBox = BoundingBox2D(460f, 520f, 720f, 820f),
            labels = listOf(RawLabel("person", 0.56f)),
            appearanceStats = BoxAppearanceStats(
                meanLuminance = 38f,
                luminanceStdDev = 11f,
                meanGradient = 7f,
                colorSpread = 6f,
                borderMeanLuminance = 92f,
                quadrantMeanSpread = 7f
            )
        )

        val result = processor.process(
            detectedObjects = listOf(realPerson, attachedShadow),
            imageLabels = emptyList(),
            imageWidth = 1000,
            imageHeight = 1000,
            timestamp = 1000L
        )

        assertEquals(1, result.obstacles.size)
        assertEquals("Person", result.obstacles[0].label)
        assertEquals(0.88f, result.obstacles[0].confidence, 0.001f)
    }

    @Test
    fun `VisionResultProcessor - filters clothing animal false positive inside person torso while keeping real dog`() {
        val processor = VisionResultProcessor()
        val person = RawDetectedObject(
            boundingBox = BoundingBox2D(100f, 100f, 500f, 900f),
            labels = listOf(RawLabel("person", 0.90f))
        )
        val furCoatFalseCat = RawDetectedObject(
            boundingBox = BoundingBox2D(150f, 250f, 450f, 650f),
            labels = listOf(RawLabel("cat", 0.62f))
        )
        val realDogOnFloor = RawDetectedObject(
            boundingBox = BoundingBox2D(600f, 550f, 920f, 900f),
            labels = listOf(RawLabel("dog", 0.72f))
        )

        val result = processor.process(
            detectedObjects = listOf(person, furCoatFalseCat, realDogOnFloor),
            imageLabels = listOf(RawLabel("Jacket", 0.72f), RawLabel("Pet", 0.50f)),
            imageWidth = 1000,
            imageHeight = 1000,
            timestamp = 1000L
        )

        assertEquals(listOf("Person", "Dog"), result.obstacles.map { it.label })
    }
}
