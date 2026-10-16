package com.project.argus.data.vision

import android.annotation.SuppressLint
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import com.project.argus.domain.model.BoundingBox2D
import com.project.argus.domain.model.VisionFrameResult

/**
 * CameraX ImageAnalysis analyzer utilizing Google ML Kit Object Detection and Image Labeling.
 * Delegates detection post-processing and spatial heuristics to [VisionProcessor].
 */
class VisionAnalyzer(
    private val processor: VisionProcessor = VisionResultProcessor(),
    private val onResult: (VisionFrameResult) -> Unit
) : ImageAnalysis.Analyzer {

    // 1. Object Detector: identifies physical obstacles with Bounding Boxes
    private val objectDetector = ObjectDetection.getClient(
        ObjectDetectorOptions.Builder()
            .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
            .enableMultipleObjects()
            .enableClassification()
            .build()
    )

    // 2. Image Labeler: detects environmental context (Stairs, Armrest, Furniture, Wall, Door)
    private val imageLabeler = ImageLabeling.getClient(
        ImageLabelerOptions.Builder()
            .setConfidenceThreshold(0.40f)
            .build()
    )

    private var lastAnalyzedTimestamp = 0L
    private val frameIntervalMs = 180L // ~5.5 FPS for optimal battery and performance

    @SuppressLint("UnsafeOptInUsageError")
    override fun analyze(imageProxy: ImageProxy) {
        val currentTimestamp = System.currentTimeMillis()
        if (currentTimestamp - lastAnalyzedTimestamp < frameIntervalMs) {
            imageProxy.close()
            return
        }

        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        lastAnalyzedTimestamp = currentTimestamp
        val rotationDegrees = imageProxy.imageInfo.rotationDegrees

        val isRotated = rotationDegrees == 90 || rotationDegrees == 270
        val imageWidth = if (isRotated) imageProxy.height else imageProxy.width
        val imageHeight = if (isRotated) imageProxy.width else imageProxy.height

        val inputImage = InputImage.fromMediaImage(mediaImage, rotationDegrees)

        val objectTask = objectDetector.process(inputImage)
        val labelTask = imageLabeler.process(inputImage)

        Tasks.whenAllComplete(objectTask, labelTask)
            .addOnSuccessListener {
                val detectedObjects = if (objectTask.isSuccessful) objectTask.result ?: emptyList() else emptyList()
                val imageLabels = if (labelTask.isSuccessful) labelTask.result ?: emptyList() else emptyList()

                val rawObjects = detectedObjects.map { mlObj ->
                    val mlBox = mlObj.boundingBox
                    RawDetectedObject(
                        boundingBox = BoundingBox2D(
                            left = mlBox.left.toFloat(),
                            top = mlBox.top.toFloat(),
                            right = mlBox.right.toFloat(),
                            bottom = mlBox.bottom.toFloat()
                        ),
                        labels = mlObj.labels.map { RawLabel(it.text, it.confidence) }
                    )
                }

                val rawLabels = imageLabels.map { RawLabel(it.text, it.confidence) }

                val frameResult = processor.process(
                    detectedObjects = rawObjects,
                    imageLabels = rawLabels,
                    imageWidth = imageWidth,
                    imageHeight = imageHeight,
                    timestamp = currentTimestamp
                )

                onResult(frameResult)
            }
            .addOnFailureListener {
                // Dropped or failed frame handled gracefully
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    }
}
