package com.project.argus.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.project.argus.ArgusApplication
import com.project.argus.domain.bluetooth.CaneBluetoothClient
import com.project.argus.domain.model.CaneDirection
import com.project.argus.domain.model.VisionFrameResult
import com.project.argus.domain.tts.SpeechOutput
import com.project.argus.domain.usecase.EvaluateHazardsUseCase
import com.project.argus.domain.usecase.EvaluateSensorFusionUseCase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Main ViewModel coordinating Vision analysis results, hazard arbitration,
 * cane Bluetooth telemetry, Sensor Fusion alerts, and Russian audio guidance.
 */
class MainViewModel(
    private val evaluateHazardsUseCase: EvaluateHazardsUseCase,
    private val evaluateSensorFusionUseCase: EvaluateSensorFusionUseCase,
    private val bluetoothClient: CaneBluetoothClient,
    private val speechOutput: SpeechOutput,
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private var initialReadySpoken = false

    init {
        // Collect cane telemetry
        viewModelScope.launch(defaultDispatcher) {
            bluetoothClient.telemetryFlow.collect { telemetry ->
                _uiState.update { current ->
                    current.copy(
                        caneDistanceCm = telemetry?.distanceCm,
                        caneDirection = telemetry?.direction ?: CaneDirection.CENTER
                    )
                }

                if (telemetry != null && telemetry.isCriticalDistance) {
                    val currentFrame = _uiState.value.latestFrameResult ?: VisionFrameResult()
                    val decision = evaluateSensorFusionUseCase(currentFrame, telemetry)
                    if (decision.spokenText != null) {
                        speechOutput.speak(decision.spokenText, decision.isUrgent)
                    }
                }
            }
        }

        // Collect connection status
        viewModelScope.launch(defaultDispatcher) {
            bluetoothClient.connectionStatus.collect { status ->
                _uiState.update { current ->
                    current.copy(bluetoothStatus = status)
                }
            }
        }

        bluetoothClient.connect()
    }

    fun onCameraPermissionResult(granted: Boolean) {
        _uiState.update { it.copy(hasCameraPermission = granted, isScanning = granted) }
        if (granted) {
            speechOutput.speak("Камера активирована. Сканирование окружения запущено.")
        } else {
            speechOutput.speak("Для работы системы Argus требуется разрешение на использование камеры.")
        }
    }

    fun onInitialPermissionCheck(granted: Boolean) {
        _uiState.update { it.copy(hasCameraPermission = granted, isScanning = granted) }
        if (granted && !initialReadySpoken) {
            initialReadySpoken = true
            speechOutput.speak("Система Argus готова к работе.")
        }
    }

    fun onFrameAnalyzed(result: VisionFrameResult) {
        viewModelScope.launch(defaultDispatcher) {
            val hazardsDecision = evaluateHazardsUseCase(result)
            val currentTelemetry = bluetoothClient.telemetryFlow.value
            val fusionDecision = evaluateSensorFusionUseCase(result, currentTelemetry)

            val textToSpeak: String?
            val isUrgent: Boolean

            when {
                hazardsDecision.spokenText != null && hazardsDecision.isUrgent -> {
                    textToSpeak = hazardsDecision.spokenText
                    isUrgent = true
                }
                fusionDecision.spokenText != null -> {
                    textToSpeak = fusionDecision.spokenText
                    isUrgent = fusionDecision.isUrgent
                }
                hazardsDecision.spokenText != null -> {
                    textToSpeak = hazardsDecision.spokenText
                    isUrgent = hazardsDecision.isUrgent
                }
                else -> {
                    textToSpeak = null
                    isUrgent = false
                }
            }

            if (textToSpeak != null) {
                speechOutput.speak(textToSpeak, isUrgent)
            }

            _uiState.update { current ->
                current.copy(
                    latestFrameResult = result,
                    validatedHazards = hazardsDecision.validatedHazards
                )
            }
        }
    }

    fun onTestCalibrationClicked() {
        bluetoothClient.triggerDemoOrReconnect()
    }

    override fun onCleared() {
        super.onCleared()
        evaluateHazardsUseCase.reset()
        speechOutput.stop()
        bluetoothClient.disconnect()
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as ArgusApplication
                MainViewModel(
                    evaluateHazardsUseCase = app.evaluateHazardsUseCase,
                    evaluateSensorFusionUseCase = app.evaluateSensorFusionUseCase,
                    bluetoothClient = app.caneBluetoothClient,
                    speechOutput = app.voiceAlertService
                )
            }
        }
    }
}
