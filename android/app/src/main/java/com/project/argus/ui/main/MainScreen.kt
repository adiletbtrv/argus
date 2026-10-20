package com.project.argus.ui.main

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.project.argus.domain.model.VisionFrameResult
import com.project.argus.ui.camera.CameraPreviewScreen
import com.project.argus.ui.main.components.CameraPermissionView

/**
 * Screen-level composable managing permission workflow, status panels, and viewfinder presentation.
 */
@Composable
fun MainScreen(
    uiState: MainUiState,
    onPermissionResult: (Boolean) -> Unit,
    onInitialCheck: (Boolean) -> Unit,
    onFrameAnalyzed: (VisionFrameResult) -> Unit,
    onTestCalibrationClicked: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val permissionsToRequest = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.CAMERA, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.CAMERA)
        }
    }

    val multiplePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val cameraGranted = permissions[Manifest.permission.CAMERA] ?: false
        onPermissionResult(cameraGranted)
    }

    LaunchedEffect(Unit) {
        val cameraGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        onInitialCheck(cameraGranted)
        if (!cameraGranted) {
            multiplePermissionLauncher.launch(permissionsToRequest)
        }
    }

    if (uiState.hasCameraPermission) {
        CameraPreviewScreen(
            uiState = uiState,
            onFrameAnalyzed = onFrameAnalyzed,
            onTestCalibrationClicked = onTestCalibrationClicked,
            modifier = modifier
        )
    } else {
        CameraPermissionView(
            onRequestPermission = {
                multiplePermissionLauncher.launch(permissionsToRequest)
            },
            modifier = modifier
        )
    }
}
