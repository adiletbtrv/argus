package com.project.argus.data.vision

import com.project.argus.domain.model.BoundingBox2D
import com.project.argus.domain.model.DistanceCategory
import com.project.argus.domain.model.HorizontalZone
import com.project.argus.domain.model.ObstacleCatalog
import com.project.argus.domain.model.SpatialHazard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class VisionResultProcessorTest {

    private lateinit var hazardClassifier: HazardClassifier
    private lateinit var processor: VisionResultProcessor

    @Before
    fun setUp() {
        hazardClassifier = HazardClassifier()
        processor = VisionResultProcessor(hazardClassifier = hazardClassifier)
    }

    @Test
    fun hazardClassifier_furnitureContextSuppressesStairsWithoutArmrest() {
        val labels = listOf(
            RawLabel(text = "Couch", confidence = 0.70f),
            RawLabel(text = "Stairs", confidence = 0.68f)
        )

        val hazards = hazardClassifier.classify(labels, hasPhysicalObjects = false)

        assertFalse(hazards.any { it is SpatialHazard.StairsAhead })
    }

    @Test
    fun hazardClassifier_realStairsDetectedWhenExceedingFurniture() {
        val labels = listOf(
            RawLabel(text = "Stairs", confidence = 0.88f),
            RawLabel(text = "Chair", confidence = 0.20f)
        )

        val hazards = hazardClassifier.classify(labels, hasPhysicalObjects = false)

        val stairsHazard = hazards.filterIsInstance<SpatialHazard.StairsAhead>().firstOrNull()
        assertTrue(stairsHazard != null)
        assertEquals(0.88f, stairsHazard!!.confidence, 0.001f)
    }

    @Test
    fun hazardClassifier_wallProximityDetectedOnlyWhenNoObjects() {
        val labels = listOf(RawLabel(text = "Wall", confidence = 0.75f))

        val withoutObjects = hazardClassifier.classify(labels, hasPhysicalObjects = false)
        assertTrue(withoutObjects.any { it is SpatialHazard.WallProximity })

        val withObjects = hazardClassifier.classify(labels, hasPhysicalObjects = true)
        assertFalse(withObjects.any { it is SpatialHazard.WallProximity })
    }

    @Test
    fun hazardClassifier_doorAndHandrailAndBranchDetected() {
        val labels = listOf(
            RawLabel(text = "Door", confidence = 0.65f),
            RawLabel(text = "Handrail", confidence = 0.70f),
            RawLabel(text = "Branch", confidence = 0.72f)
        )

        val hazards = hazardClassifier.classify(labels, hasPhysicalObjects = false)

        assertTrue(hazards.any { it is SpatialHazard.DoorAhead })
        assertTrue(hazards.any { it is SpatialHazard.HandrailAhead })
        assertTrue(hazards.any { it is SpatialHazard.LowBranchAhead })
    }

    @Test
    fun spatialClassifier_compactScaleCorrectlyClassifiesRoundObjects() {
        val classifier = SpatialClassifier()
        val totalArea = 1_000_000f

        // Sports ball covering 16% (isCompact = true -> NEAR threshold is 14%)
        assertEquals(
            DistanceCategory.NEAR,
            classifier.classifyDistance(160_000f, totalArea, isCompact = true)
        )

        // Sports ball covering 3% (isCompact = true -> FAR, not ignored because threshold is 1.5%)
        assertFalse(classifier.isIgnored(30_000f, totalArea, isCompact = true))
        assertTrue(classifier.isIgnored(10_000f, totalArea, isCompact = true))

        // Standard object covering 3% is ignored (< 6%)
        assertTrue(classifier.isIgnored(30_000f, totalArea, isCompact = false))
    }

    @Test
    fun visionResultProcessor_expandedCatalogRecognizesVehiclesAndAnimalsAndRoundObjects() {
        assertTrue(processor.isAllowedObject("car"))
        assertTrue(processor.isAllowedObject("bus"))
        assertTrue(processor.isAllowedObject("dog"))
        assertTrue(processor.isAllowedObject("cat"))
        assertTrue(processor.isAllowedObject("sports ball"))
        assertTrue(processor.isAllowedObject("cup"))
        assertTrue(processor.isAllowedObject("clock"))
        assertTrue(processor.isAllowedObject("bottle"))
        assertTrue(processor.isAllowedObject("chair"))
        assertTrue(processor.isAllowedObject("couch"))

        assertFalse(processor.isAllowedObject("unregistered_random_item"))
    }

    @Test
    fun visionResultProcessor_roundObjectDetectionProcessedSuccessfully() {
        val objects = listOf(
            RawDetectedObject(
                boundingBox = BoundingBox2D(left = 400f, top = 400f, right = 600f, bottom = 600f),
                labels = listOf(RawLabel("sports ball", 0.75f))
            )
        )

        val result = processor.process(
            detectedObjects = objects,
            imageLabels = emptyList(),
            imageWidth = 1000,
            imageHeight = 1000,
            timestamp = 1000L
        )

        assertEquals(1, result.obstacles.size)
        assertEquals("Ball", result.obstacles[0].label)
        assertEquals(HorizontalZone.CENTER, result.obstacles[0].zone)
    }

    @Test
    fun obstacleCatalog_translatesChairsAndCouchesCleanly() {
        assertEquals("стул", ObstacleCatalog.translate("chair"))
        assertEquals("диван", ObstacleCatalog.translate("couch"))
        assertEquals("скамейка", ObstacleCatalog.translate("bench"))
        assertEquals("стол", ObstacleCatalog.translate("dining table"))
        assertEquals("мяч", ObstacleCatalog.translate("sports ball"))
        assertEquals("чашка", ObstacleCatalog.translate("cup"))
        assertEquals("часы", ObstacleCatalog.translate("clock"))
    }
}
