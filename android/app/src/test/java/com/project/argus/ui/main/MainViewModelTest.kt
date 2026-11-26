package com.project.argus.ui.main

import com.project.argus.domain.bluetooth.BluetoothConnectionStatus
import com.project.argus.domain.bluetooth.CaneBluetoothClient
import com.project.argus.domain.haptic.HapticFeedbackService
import com.project.argus.domain.model.BoundingBox2D
import com.project.argus.domain.model.CaneDirection
import com.project.argus.domain.model.CaneTelemetry
import com.project.argus.domain.model.DetectedObstacle
import com.project.argus.domain.model.DistanceCategory
import com.project.argus.domain.model.HorizontalZone
import com.project.argus.domain.model.SpatialHazard
import com.project.argus.domain.model.VisionFrameResult
import com.project.argus.domain.tts.SpeechOutput
import com.project.argus.domain.usecase.EvaluateHazardsUseCase
import com.project.argus.domain.usecase.EvaluateSensorFusionUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

class FakeHapticFeedbackService : HapticFeedbackService {
    var urgentVibrationCount = 0

    override fun vibrateUrgent() {
        urgentVibrationCount++
    }
}

class FakeCaneBluetoothClient : CaneBluetoothClient {
    val _telemetryFlow = MutableStateFlow<CaneTelemetry?>(null)
    override val telemetryFlow: StateFlow<CaneTelemetry?> = _telemetryFlow.asStateFlow()

    val _connectionStatus = MutableStateFlow(BluetoothConnectionStatus.DISCONNECTED)
    override val connectionStatus: StateFlow<BluetoothConnectionStatus> = _connectionStatus.asStateFlow()

    var connectCalled = false
    var disconnectCalled = false
    var demoCalled = false

    override fun connect(targetAddress: String?) {
        connectCalled = true
        _connectionStatus.value = BluetoothConnectionStatus.CONNECTED
    }

    override fun disconnect() {
        disconnectCalled = true
        _connectionStatus.value = BluetoothConnectionStatus.DISCONNECTED
    }

    override fun triggerDemoOrReconnect() {
        demoCalled = true
        _telemetryFlow.value = CaneTelemetry(distanceCm = 28, direction = CaneDirection.LEFT)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var fakeSpeechOutput: FakeSpeechOutput
    private lateinit var fakeHapticFeedbackService: FakeHapticFeedbackService
    private lateinit var fakeBluetoothClient: FakeCaneBluetoothClient
    private lateinit var evaluateHazardsUseCase: EvaluateHazardsUseCase
    private lateinit var evaluateSensorFusionUseCase: EvaluateSensorFusionUseCase
    private lateinit var viewModel: MainViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeSpeechOutput = FakeSpeechOutput()
        fakeHapticFeedbackService = FakeHapticFeedbackService()
        fakeBluetoothClient = FakeCaneBluetoothClient()
        evaluateHazardsUseCase = EvaluateHazardsUseCase()
        evaluateSensorFusionUseCase = EvaluateSensorFusionUseCase()
        viewModel = MainViewModel(
            evaluateHazardsUseCase = evaluateHazardsUseCase,
            evaluateSensorFusionUseCase = evaluateSensorFusionUseCase,
            bluetoothClient = fakeBluetoothClient,
            speechOutput = fakeSpeechOutput,
            hapticFeedbackService = fakeHapticFeedbackService,
            defaultDispatcher = testDispatcher
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialUiState_hasNoCameraPermission_andConnectCalled() {
        val state = viewModel.uiState.value
        assertFalse(state.hasCameraPermission)
        assertFalse(state.isScanning)
        assertNull(state.latestFrameResult)
        assertTrue(state.validatedHazards.isEmpty())
        assertTrue(fakeBluetoothClient.connectCalled)
    }

    @Test
    fun cameraPermissionGranted_speaksActivationPrompt() {
        viewModel.onCameraPermissionResult(granted = true)
        val state = viewModel.uiState.value
        assertTrue(state.hasCameraPermission)
        assertTrue(state.isScanning)
        assertEquals(1, fakeSpeechOutput.spokenMessages.size)
        assertEquals("Камера активирована. Сканирование окружения запущено.", fakeSpeechOutput.spokenMessages[0].first)
    }

    @Test
    fun onFrameAnalyzed_dispatchesSpeechAndUpdatesUiState() = runTest {
        val frame = VisionFrameResult(
            spatialHazards = listOf(SpatialHazard.WallProximity(0.85f)),
            obstacles = emptyList(),
            frameWidth = 1000,
            frameHeight = 1000
        )

        viewModel.onFrameAnalyzed(frame)

        val state = viewModel.uiState.value
        assertEquals(frame, state.latestFrameResult)
        assertEquals(1, state.validatedHazards.size)
        assertTrue(state.validatedHazards[0] is SpatialHazard.WallProximity)
        assertEquals(1, fakeSpeechOutput.spokenMessages.size)
        assertEquals("Внимание, стена прямо по курсу!", fakeSpeechOutput.spokenMessages[0].first)
        assertTrue(fakeSpeechOutput.spokenMessages[0].second)
    }

    @Test
    fun criticalSonarDistance_triggersImmediateUrgentAlertAndHaptic() = runTest {
        val criticalTelemetry = CaneTelemetry(distanceCm = 24, direction = CaneDirection.CENTER)
        fakeBluetoothClient._telemetryFlow.value = criticalTelemetry

        assertTrue(fakeSpeechOutput.spokenMessages.any {
            it.first.contains("24 см") && it.second
        })
        assertEquals(1, fakeHapticFeedbackService.urgentVibrationCount)
    }

    @Test
    fun nonUrgentAlert_doesNotTriggerHaptic() = runTest {
        val frame = VisionFrameResult(
            spatialHazards = listOf(SpatialHazard.DoorAhead(0.80f)),
            frameWidth = 1000,
            frameHeight = 1000
        )

        viewModel.onFrameAnalyzed(frame)

        assertEquals(1, fakeSpeechOutput.spokenMessages.size)
        assertFalse(fakeSpeechOutput.spokenMessages[0].second)
        assertEquals(0, fakeHapticFeedbackService.urgentVibrationCount)
    }
}
