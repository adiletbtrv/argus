package com.project.argus.ui.main

import com.project.argus.domain.model.SpatialHazard
import com.project.argus.domain.model.VisionFrameResult

/**
 * Immutable UI state model for the Main screen adhering to Unidirectional Data Flow.
 */
data class MainUiState(
    val hasCameraPermission: Boolean = false,
    val latestFrameResult: VisionFrameResult? = null,
    val validatedHazards: List<SpatialHazard> = emptyList(),
    val isScanning: Boolean = false
)
