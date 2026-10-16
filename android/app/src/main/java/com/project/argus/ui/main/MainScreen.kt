package com.project.argus.ui.main

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.project.argus.domain.model.VisionFrameResult
import com.project.argus.ui.camera.CameraPreviewScreen
import com.project.argus.ui.main.components.CameraPermissionView

/**
 * Screen-level composable managing permission workflow and viewfinder presentation.
 */
@Composable
fun MainScreen(
    uiState: MainUiState,
    onPermissionResult: (Boolean) -> Unit,
    onInitialCheck: (Boolean) -> Unit,
    onFrameAnalyzed: (VisionFrameResult) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        onPermissionResult(isGranted)
    }

    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        onInitialCheck(granted)
        if (!granted) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    if (uiState.hasCameraPermission) {
        CameraPreviewScreen(
            uiState = uiState,
            onFrameAnalyzed = onFrameAnalyzed,
            modifier = modifier
        )
    } else {
        CameraPermissionView(
            onRequestPermission = {
                permissionLauncher.launch(Manifest.permission.CAMERA)
            },
            modifier = modifier
        )
    }
}
