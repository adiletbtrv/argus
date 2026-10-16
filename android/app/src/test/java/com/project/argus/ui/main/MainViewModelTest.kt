package com.project.argus.ui.main

import com.project.argus.domain.model.BoundingBox2D
import com.project.argus.domain.model.DetectedObstacle
import com.project.argus.domain.model.DistanceCategory
import com.project.argus.domain.model.HorizontalZone
import com.project.argus.domain.model.SpatialHazard
import com.project.argus.domain.model.VisionFrameResult
import com.project.argus.domain.tts.SpeechOutput
import com.project.argus.domain.usecase.EvaluateHazardsUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Fake depends only on [SpeechOutput], not on lifecycle management methods.
 */
class FakeSpeechOutput : SpeechOutput {
    val spokenMessages = mutableListOf<Pair<String, Boolean>>()
    var isStopped = false

    override fun speak(text: String, urgent: Boolean) {
        spokenMessages.add(text to urgent)
    }

    override fun stop() {
        isStopped = true
    }
}

/**
 * Unit tests for [MainViewModel] verifying UI state management,
 * permission flows, vision frame handling, and speech synthesis dispatching.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var fakeSpeechOutput: FakeSpeechOutput
    private lateinit var evaluateHazardsUseCase: EvaluateHazardsUseCase
    private lateinit var viewModel: MainViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeSpeechOutput = FakeSpeechOutput()
        evaluateHazardsUseCase = EvaluateHazardsUseCase()
        viewModel = MainViewModel(
            evaluateHazardsUseCase = evaluateHazardsUseCase,
            speechOutput = fakeSpeechOutput,
            defaultDispatcher = testDispatcher
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial uiState has no permission and is not scanning`() {
        val state = viewModel.uiState.value
        assertFalse(state.hasCameraPermission)
        assertFalse(state.isScanning)
        assertNull(state.latestFrameResult)
        assertTrue(state.validatedHazards.isEmpty())
    }

    @Test
    fun `camera permission granted enables scanning and speaks activation prompt`() {
        viewModel.onCameraPermissionResult(granted = true)

        val state = viewModel.uiState.value
        assertTrue(state.hasCameraPermission)
        assertTrue(state.isScanning)

        assertEquals(1, fakeSpeechOutput.spokenMessages.size)
        assertEquals("Camera activated. Environmental scan started.", fakeSpeechOutput.spokenMessages[0].first)
        assertFalse(fakeSpeechOutput.spokenMessages[0].second) // isUrgent = false
    }

    @Test
    fun `camera permission denied disables scanning and speaks warning prompt`() {
        viewModel.onCameraPermissionResult(granted = false)

        val state = viewModel.uiState.value
        assertFalse(state.hasCameraPermission)
        assertFalse(state.isScanning)

        assertEquals(1, fakeSpeechOutput.spokenMessages.size)
        assertEquals("Camera permission is required for the Argus system to operate.", fakeSpeechOutput.spokenMessages[0].first)
    }

    @Test
    fun `initial permission check when granted speaks ready only once`() {
        viewModel.onInitialPermissionCheck(granted = true)
        viewModel.onInitialPermissionCheck(granted = true) // duplicate check

        val state = viewModel.uiState.value
        assertTrue(state.hasCameraPermission)
        assertTrue(state.isScanning)

        assertEquals(1, fakeSpeechOutput.spokenMessages.size)
        assertEquals("Argus system ready.", fakeSpeechOutput.spokenMessages[0].first)
    }

    @Test
    fun `onFrameAnalyzed dispatches speech and updates uiState with latest frame and validated hazards`() = runTest {
        val frame = VisionFrameResult(
            spatialHazards = listOf(SpatialHazard.WallProximity(0.85f)),
            obstacles = listOf(
                DetectedObstacle(
                    id = 0,
                    label = "Wall",
                    confidence = 0.85f,
                    boundingBox = BoundingBox2D(0f, 0f, 800f, 800f),
                    zone = HorizontalZone.CENTER,
                    estimatedDistanceCategory = DistanceCategory.NEAR
                )
            ),
            frameWidth = 1000,
            frameHeight = 1000
        )

        viewModel.onFrameAnalyzed(frame)

        val state = viewModel.uiState.value
        assertEquals(frame, state.latestFrameResult)
        assertEquals(1, state.validatedHazards.size)
        assertTrue(state.validatedHazards[0] is SpatialHazard.WallProximity)

        // Spoken prompt verified
        assertEquals(1, fakeSpeechOutput.spokenMessages.size)
        assertEquals("Warning, wall directly ahead!", fakeSpeechOutput.spokenMessages[0].first)
        assertTrue("Wall warning should be urgent", fakeSpeechOutput.spokenMessages[0].second)
    }
}
