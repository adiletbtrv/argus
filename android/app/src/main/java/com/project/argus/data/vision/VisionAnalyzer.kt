package com.project.argus.data.vision

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.objectdetector.ObjectDetector
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.project.argus.domain.model.BoundingBox2D
import com.project.argus.domain.model.VisionFrameResult
import java.io.Closeable

/**
 * CameraX ImageAnalysis analyzer utilizing MediaPipe Tasks Object Detection (EfficientDet-Lite0 COCO)
 * and Google ML Kit Image Labeling.
 * Delegates detection post-processing and spatial heuristics to [VisionProcessor].
 */
class VisionAnalyzer(
    context: Context,
    private val processor: VisionProcessor = VisionResultProcessor(),
    private val onResult: (VisionFrameResult) -> Unit
) : ImageAnalysis.Analyzer, Closeable {

    // 1. Object Detector: identifies physical COCO obstacles with Bounding Boxes
    private val objectDetector = ObjectDetector.createFromOptions(
        context,
        ObjectDetector.ObjectDetectorOptions.builder()
            .setBaseOptions(
                BaseOptions.builder()
                    .setModelAssetPath("efficientdet_lite0.tflite")
                    .build()
            )
            .setRunningMode(RunningMode.IMAGE)
            .setScoreThreshold(0.5f)
            .setMaxResults(5)
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

        val rawBitmap = imageProxy.toBitmap()
        val bitmap = if (rotationDegrees != 0) {
            val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
            Bitmap.createBitmap(rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true)
        } else {
            rawBitmap
        }

        val detections = runCatching {
            val mpImage = BitmapImageBuilder(bitmap).build()
            objectDetector.detect(mpImage).detections()
        }.getOrDefault(emptyList())

        val rawObjects = detections.map { detection ->
            val box = detection.boundingBox()
            RawDetectedObject(
                boundingBox = BoundingBox2D(
                    left = box.left,
                    top = box.top,
                    right = box.right,
                    bottom = box.bottom
                ),
                labels = detection.categories().map { RawLabel(it.categoryName(), it.score()) }
            )
        }

        val inputImage = InputImage.fromMediaImage(mediaImage, rotationDegrees)

        imageLabeler.process(inputImage)
            .addOnSuccessListener { labels ->
                val rawLabels = labels.map { RawLabel(it.text, it.confidence) }

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

    override fun close() {
        runCatching { objectDetector.close() }
        runCatching { imageLabeler.close() }
    }
}
