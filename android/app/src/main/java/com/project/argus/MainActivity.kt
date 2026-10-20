package com.project.argus

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.project.argus.ui.main.MainScreen
import com.project.argus.ui.main.MainViewModel
import com.project.argus.ui.theme.ArgusTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels { MainViewModel.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            ArgusTheme {
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MainScreen(
                        uiState = uiState,
                        onPermissionResult = viewModel::onCameraPermissionResult,
                        onInitialCheck = viewModel::onInitialPermissionCheck,
                        onFrameAnalyzed = viewModel::onFrameAnalyzed,
                        onTestCalibrationClicked = viewModel::onTestCalibrationClicked,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}