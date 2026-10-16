package com.project.argus.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.project.argus.ArgusApplication
import com.project.argus.domain.model.VisionFrameResult
import com.project.argus.domain.tts.SpeechOutput
import com.project.argus.domain.usecase.EvaluateHazardsUseCase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Main ViewModel coordinating Vision analysis results, hazard arbitration,
 * UI state updates, and English audio alerts.
 *
 * [SRP] Single responsibility: coordinates UI state and delegates decisions to the use case.
 * [DIP] Depends on [SpeechOutput] abstraction, not the concrete Android TTS implementation.
 * [ISP] Only receives the speaking capability it uses — not lifecycle management (shutdown).
 */
class MainViewModel(
    private val evaluateHazardsUseCase: EvaluateHazardsUseCase,
    private val speechOutput: SpeechOutput,
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private var initialReadySpoken = false

    fun onCameraPermissionResult(granted: Boolean) {
        _uiState.update { it.copy(hasCameraPermission = granted, isScanning = granted) }
        if (granted) {
            speechOutput.speak("Camera activated. Environmental scan started.")
        } else {
            speechOutput.speak("Camera permission is required for the Argus system to operate.")
        }
    }

    fun onInitialPermissionCheck(granted: Boolean) {
        _uiState.update { it.copy(hasCameraPermission = granted, isScanning = granted) }
        if (granted && !initialReadySpoken) {
            initialReadySpoken = true
            speechOutput.speak("Argus system ready.")
        }
    }

    fun onFrameAnalyzed(result: VisionFrameResult) {
        viewModelScope.launch(defaultDispatcher) {
            val decision = evaluateHazardsUseCase(result)

            if (decision.spokenText != null) {
                speechOutput.speak(decision.spokenText, decision.isUrgent)
            }

            _uiState.update { current ->
                current.copy(
                    latestFrameResult = result,
                    validatedHazards = decision.validatedHazards
                )
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        evaluateHazardsUseCase.reset()
        speechOutput.stop()
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as ArgusApplication
                MainViewModel(
                    evaluateHazardsUseCase = app.evaluateHazardsUseCase,
                    speechOutput = app.voiceAlertService
                )
            }
        }
    }
}
