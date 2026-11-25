package com.project.argus

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.DisposableEffect
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

                DisposableEffect(uiState.isScanning) {
                    if (uiState.isScanning) {
                        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    }
                    onDispose {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    }
                }

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MainScreen(
                        uiState = uiState,
                        onPermissionResult = viewModel::onCameraPermissionResult,
                        onBluetoothPermissionResult = viewModel::onBluetoothPermissionResult,
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