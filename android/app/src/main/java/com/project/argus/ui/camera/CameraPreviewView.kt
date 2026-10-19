package com.project.argus.ui.camera

import android.view.ViewGroup
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.project.argus.R
import com.project.argus.data.vision.VisionAnalyzer
import com.project.argus.domain.model.CaneDirection
import com.project.argus.domain.model.HorizontalZone
import com.project.argus.domain.model.SpatialHazard
import com.project.argus.domain.model.VisionFrameResult
import com.project.argus.ui.main.MainUiState
import com.project.argus.ui.main.components.HazardBadge
import java.util.concurrent.Executors

/**
 * Camera preview composable integrating CameraX, real-time object tracking overlay,
 * and contextual hazard indicators according to official Android Jetpack Compose guidelines.
 */
@Composable
fun CameraPreviewScreen(
    uiState: MainUiState,
    onFrameAnalyzed: (VisionFrameResult) -> Unit,
    onTestCalibrationClicked: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val visionAnalyzer = remember(context) {
        VisionAnalyzer(context) { result ->
            onFrameAnalyzed(result)
        }
    }

    DisposableEffect(visionAnalyzer) {
        onDispose {
            visionAnalyzer.close()
            cameraExecutor.shutdown()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        // 1. CameraX Preview Layer
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val previewView = PreviewView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }

                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()

                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }

                    val imageAnalysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                        .build()
                        .also { analysis ->
                            analysis.setAnalyzer(cameraExecutor, visionAnalyzer)
                        }

                    val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                    try {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            cameraSelector,
                            preview,
                            imageAnalysis
                        )
                    } catch (exc: Exception) {
                        exc.printStackTrace()
                    }
                }, ContextCompat.getMainExecutor(ctx))

                previewView
            }
        )

        // 2. Detection Overlay Canvas
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasWidth = size.width
            val canvasHeight = size.height

            val lineLeftX = canvasWidth * 0.33f
            val lineRightX = canvasWidth * 0.66f

            // Directional Zone Dividers (Left | Center | Right)
            drawLine(
                color = Color.White.copy(alpha = 0.20f),
                start = Offset(lineLeftX, 0f),
                end = Offset(lineLeftX, canvasHeight),
                strokeWidth = 2f
            )
            drawLine(
                color = Color.White.copy(alpha = 0.20f),
                start = Offset(lineRightX, 0f),
                end = Offset(lineRightX, canvasHeight),
                strokeWidth = 2f
            )

            val result = uiState.latestFrameResult
            if (result != null && result.frameWidth > 0 && result.frameHeight > 0) {
                val scaleX = canvasWidth / result.frameWidth
                val scaleY = canvasHeight / result.frameHeight

                result.obstacles.forEach { obstacle ->
                    val box = obstacle.boundingBox
                    val left = box.left * scaleX
                    val top = box.top * scaleY
                    val width = box.width() * scaleX
                    val height = box.height() * scaleY

                    val boxColor = if (obstacle.isArmrest) {
                        Color(0xFF29B6F6) // Light Blue for Armrest / Furniture
                    } else {
                        when (obstacle.zone) {
                            HorizontalZone.CENTER -> Color(0xFFFF5252) // Red: Direct collision path
                            HorizontalZone.LEFT -> Color(0xFFFFD740)   // Yellow: Left flank
                            HorizontalZone.RIGHT -> Color(0xFF69F0AE)  // Green/Teal: Right flank
                        }
                    }

                    drawRect(
                        color = boxColor,
                        topLeft = Offset(left, top),
                        size = Size(width, height),
                        style = Stroke(width = 5f)
                    )
                }
            }
        }

        // 3. Top Spatial Hazard Indicators & Cane Telemetry Overlay
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .align(Alignment.TopCenter)
        ) {
            uiState.validatedHazards.forEach { hazard ->
                when (hazard) {
                    is SpatialHazard.StairsAhead -> {
                        HazardBadge(
                            title = stringResource(R.string.hazard_stairs_title),
                            subtitle = stringResource(
                                R.string.hazard_stairs_sub,
                                (hazard.confidence * 100).toInt()
                            ),
                            backgroundColor = Color(0xFFD32F2F)
                        )
                    }
                    is SpatialHazard.ArmrestDetected -> {
                        HazardBadge(
                            title = stringResource(R.string.hazard_armrest_title),
                            subtitle = stringResource(
                                R.string.hazard_armrest_sub,
                                (hazard.confidence * 100).toInt()
                            ),
                            backgroundColor = Color(0xFF0288D1)
                        )
                    }
                    is SpatialHazard.WallProximity -> {
                        HazardBadge(
                            title = stringResource(R.string.hazard_wall_title),
                            subtitle = stringResource(R.string.hazard_wall_sub),
                            backgroundColor = Color(0xFFE64A19)
                        )
                    }
                    is SpatialHazard.DoorAhead -> {
                        HazardBadge(
                            title = stringResource(R.string.hazard_door_title),
                            subtitle = stringResource(
                                R.string.hazard_door_sub,
                                (hazard.confidence * 100).toInt()
                            ),
                            backgroundColor = Color(0xFF388E3C)
                        )
                    }
                    SpatialHazard.None -> Unit
                }
                Spacer(modifier = Modifier.height(6.dp))
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Cane Telemetry Panels
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Panel: Cane Distance
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(
                        containerColor = when {
                            uiState.caneDistanceCm == null -> Color(0xFF263238).copy(alpha = 0.9f)
                            uiState.caneDistanceCm < 35 -> Color(0xFFD32F2F)
                            uiState.caneDistanceCm > 50 -> Color(0xFF2E7D32)
                            else -> Color(0xFFE65100)
                        }
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "ДИСТАНЦИЯ ТРОСТИ",
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = uiState.caneDistanceCm?.let { "$it СМ" } ?: "--",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }

                // Panel: Cane Direction / Course
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(
                        containerColor = when (uiState.caneDirection) {
                            CaneDirection.LEFT -> Color(0xFF1565C0)
                            CaneDirection.RIGHT -> Color(0xFF00838F)
                            CaneDirection.CENTER -> Color(0xFF37474F)
                        }
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "КУРС",
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = when (uiState.caneDirection) {
                                CaneDirection.LEFT -> "ВЛЕВО"
                                CaneDirection.RIGHT -> "ВПРАВО"
                                CaneDirection.CENTER -> "ПРЯМО"
                            },
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Test / Calibration Button (Read-only on socket, triggers demo or reconnect)
            Button(
                onClick = onTestCalibrationClicked,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF00897B)
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = "ТЕСТ / КАЛИБРОВКА",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // 4. Bottom Environmental Summary Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .align(Alignment.BottomCenter),
            colors = CardDefaults.cardColors(
                containerColor = Color.Black.copy(alpha = 0.75f)
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = stringResource(R.string.status_scanning),
                    color = Color(0xFF81C784),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )

                val result = uiState.latestFrameResult
                if (result != null) {
                    Text(
                        text = stringResource(R.string.detected_objects, result.obstacles.size),
                        color = Color.White,
                        fontSize = 14.sp
                    )

                    if (result.obstacles.isNotEmpty()) {
                        val obstaclesSummary = result.obstacles.joinToString(", ") {
                            "${it.label} (${it.zone})"
                        }
                        Text(
                            text = stringResource(R.string.in_path, obstaclesSummary),
                            color = Color(0xFFFFE082),
                            fontSize = 13.sp
                        )
                    }

                    if (result.rawLabels.isNotEmpty()) {
                        Text(
                            text = stringResource(
                                R.string.scene_labels,
                                result.rawLabels.take(4).joinToString(", ")
                            ),
                            color = Color.LightGray,
                            fontSize = 11.sp
                        )
                    }
                } else {
                    Text(
                        text = stringResource(R.string.waiting_for_camera),
                        color = Color.Gray,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}
