package com.project.argus

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.project.argus.data.tts.AndroidVoiceAlertService
import com.project.argus.data.vision.RawDetectedObject
import com.project.argus.data.vision.RawLabel
import com.project.argus.data.vision.VisionAnalyzer
import com.project.argus.data.vision.VisionResultProcessor
import com.project.argus.domain.model.BoundingBox2D
import com.project.argus.domain.model.DistanceCategory
import com.project.argus.domain.model.HorizontalZone
import com.project.argus.domain.model.SpatialHazard
import com.project.argus.domain.usecase.EvaluateHazardsUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VisionIntegrationTest {

    @Test
    fun testAppContextAndVoiceServiceInitialization() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertNotNull(appContext)
        assertEquals("com.project.argus", appContext.packageName)

        // Verify TTS Service instantiation with real Android Context
        val ttsService = AndroidVoiceAlertService(appContext)
        assertNotNull(ttsService)
        ttsService.shutdown()
    }

    @Test
    fun testVisionAnalyzerInitialization() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        var callbackInvoked = false
        val analyzer = VisionAnalyzer(appContext) {
            callbackInvoked = true
        }
        assertNotNull(analyzer)
        analyzer.close()
    }

    @Test
    fun testEndToEndVisionAndHazardEvaluation() {
        val processor = VisionResultProcessor()
        val evaluateUseCase = EvaluateHazardsUseCase()

        // 1. Simulate MediaPipe object detection of a medium chair in center and ML Kit Door label
        val detectedObject = RawDetectedObject(
            boundingBox = BoundingBox2D(left = 350f, top = 250f, right = 650f, bottom = 750f),
            labels = listOf(RawLabel("chair", 0.90f))
        )
        val imageLabels = listOf(
            RawLabel("Door", 0.65f)
        )

        val frameResult = processor.process(
            detectedObjects = listOf(detectedObject),
            imageLabels = imageLabels,
            imageWidth = 1000,
            imageHeight = 1000
        )

        // Verify Vision Processing
        assertEquals(1, frameResult.obstacles.size)
        assertEquals(HorizontalZone.CENTER, frameResult.obstacles[0].zone)
        assertEquals(DistanceCategory.MEDIUM, frameResult.obstacles[0].estimatedDistanceCategory)
        assertTrue(frameResult.spatialHazards.any { it is SpatialHazard.DoorAhead })

        // Evaluate Hazards via Use Case
        val decision = evaluateUseCase(frameResult)
        assertNotNull(decision)
        // DoorAhead (Priority 4) has priority over medium center obstacle (Priority 5)
        assertEquals("Впереди дверной проем", decision.spokenText)
    }
}
