package com.project.argus.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ObstacleCatalogTest {

    @Test
    fun `catalog contains exactly 23 curated navigation COCO classes`() {
        val expectedClasses = setOf(
            // dangerPriority = 3 (6 classes)
            "person", "car", "bus", "truck", "motorcycle", "bicycle",
            // dangerPriority = 2 (14 classes)
            "dog", "cat", "chair", "couch", "bench", "dining table", "bed",
            "potted plant", "suitcase", "backpack", "fire hydrant",
            "stop sign", "traffic light", "parking meter",
            // dangerPriority = 1 (3 classes)
            "handbag", "umbrella", "sports ball"
        )

        assertEquals(23, ObstacleCatalog.specs.size)
        assertEquals(expectedClasses, ObstacleCatalog.allowedCocoClasses)
        expectedClasses.forEach { cocoClass ->
            assertTrue("Expected $cocoClass to be allowed", ObstacleCatalog.isAllowedCocoClass(cocoClass))
            assertNotNull(ObstacleCatalog.findSpec(cocoClass))
        }
    }

    @Test
    fun `catalog excludes household clutter food electronics and non coco door`() {
        val excludedNoise = listOf(
            "cup", "bowl", "fork", "spoon", "knife",
            "donut", "pizza", "cake", "apple", "orange", "banana",
            "clock", "remote", "mouse", "book", "teddy bear",
            "cell phone", "saucer", "bottle", "tv", "door"
        )

        excludedNoise.forEach { noisyLabel ->
            assertFalse("Noise class '$noisyLabel' must be excluded", ObstacleCatalog.isAllowedCocoClass(noisyLabel))
            assertNull(ObstacleCatalog.findSpec(noisyLabel))
            assertEquals(0, ObstacleCatalog.dangerPriority(noisyLabel))
        }
    }

    @Test
    fun `high risk moving classes have danger priority 3`() {
        val priority3 = listOf("person", "car", "bus", "truck", "motorcycle", "bicycle")
        priority3.forEach { label ->
            assertEquals("Expected priority 3 for $label", 3, ObstacleCatalog.dangerPriority(label))
        }
    }

    @Test
    fun `physical obstacles animals furniture and street poles have danger priority 2`() {
        val priority2 = listOf(
            "dog", "cat", "chair", "couch", "bench", "dining table", "bed",
            "potted plant", "suitcase", "backpack", "fire hydrant",
            "stop sign", "traffic light", "parking meter"
        )
        priority2.forEach { label ->
            assertEquals("Expected priority 2 for $label", 2, ObstacleCatalog.dangerPriority(label))
        }
    }

    @Test
    fun `low risk compact items have danger priority 1`() {
        val priority1 = listOf("handbag", "umbrella", "sports ball")
        priority1.forEach { label ->
            assertEquals("Expected priority 1 for $label", 1, ObstacleCatalog.dangerPriority(label))
        }
    }

    @Test
    fun `only sports ball uses compact area profile`() {
        assertTrue(ObstacleCatalog.isCompact("sports ball"))
        assertTrue(ObstacleCatalog.isCompact("Ball"))
        assertFalse(ObstacleCatalog.isCompact("chair"))
        assertFalse(ObstacleCatalog.isCompact("person"))
        assertFalse(ObstacleCatalog.isCompact("umbrella"))
        assertFalse(ObstacleCatalog.isCompact("handbag"))
    }

    @Test
    fun `russian translations match navigation dictionary and aliases`() {
        assertEquals("человек", ObstacleCatalog.translate("person"))
        assertEquals("машина", ObstacleCatalog.translate("car"))
        assertEquals("автобус", ObstacleCatalog.translate("bus"))
        assertEquals("грузовик", ObstacleCatalog.translate("truck"))
        assertEquals("мотоцикл", ObstacleCatalog.translate("motorcycle"))
        assertEquals("велосипед", ObstacleCatalog.translate("bicycle"))
        assertEquals("собака", ObstacleCatalog.translate("dog"))
        assertEquals("кошка", ObstacleCatalog.translate("cat"))
        assertEquals("стул", ObstacleCatalog.translate("chair"))
        assertEquals("диван", ObstacleCatalog.translate("couch"))
        assertEquals("скамейка", ObstacleCatalog.translate("bench"))
        assertEquals("стол", ObstacleCatalog.translate("dining table"))
        assertEquals("стол", ObstacleTranslator.translate("table"))
        assertEquals("кровать", ObstacleCatalog.translate("bed"))
        assertEquals("растение", ObstacleCatalog.translate("potted plant"))
        assertEquals("растение", ObstacleTranslator.translate("plant"))
        assertEquals("чемодан", ObstacleCatalog.translate("suitcase"))
        assertEquals("рюкзак", ObstacleCatalog.translate("backpack"))
        assertEquals("гидрант", ObstacleCatalog.translate("fire hydrant"))
        assertEquals("дорожный знак", ObstacleCatalog.translate("stop sign"))
        assertEquals("светофор", ObstacleCatalog.translate("traffic light"))
        assertEquals("столбик", ObstacleCatalog.translate("parking meter"))
        assertEquals("сумка", ObstacleCatalog.translate("handbag"))
        assertEquals("зонт", ObstacleCatalog.translate("umbrella"))
        assertEquals("мяч", ObstacleCatalog.translate("sports ball"))
        assertEquals("мяч", ObstacleTranslator.translate("Ball"))
    }

    @Test
    fun `dangerPriority resolves formatted labels and aliases case insensitively`() {
        assertEquals(3, ObstacleCatalog.dangerPriority("  Person "))
        assertEquals(3, ObstacleCatalog.dangerPriority("CAR"))
        assertEquals(2, ObstacleCatalog.dangerPriority("Dining table"))
        assertEquals(2, ObstacleCatalog.dangerPriority("table"))
        assertEquals(1, ObstacleCatalog.dangerPriority("Ball"))
        assertEquals(1, ObstacleCatalog.dangerPriority("Handbag"))
    }
}
