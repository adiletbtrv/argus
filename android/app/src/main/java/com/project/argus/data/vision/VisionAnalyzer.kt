package com.project.argus.data.vision

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
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
import com.project.argus.domain.model.ObstacleCatalog
import com.project.argus.domain.model.VisionFrameResult
import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean

// CameraX analyzer running on-device EfficientDet-Lite0 and ML Kit Image Labeling.
class VisionAnalyzer(
    context: Context,
    private val processor: VisionProcessor = VisionResultProcessor(),
    private val onResult: (VisionFrameResult) -> Unit
) : ImageAnalysis.Analyzer, Closeable {

    // Lower score threshold (0.38) and higher maxResults (10) improve recall on compact round items;
    // VisionResultProcessor applies per-class thresholds, shadow/clothing filters, and temporal smoothing.
    private val objectDetector = ObjectDetector.createFromOptions(
        context,
        ObjectDetector.ObjectDetectorOptions.builder()
            .setBaseOptions(
                BaseOptions.builder()
                    .setModelAssetPath("efficientdet_lite0.tflite")
                    .build()
            )
            .setRunningMode(RunningMode.IMAGE)
            .setScoreThreshold(0.38f)
            .setMaxResults(10)
            .build()
    )

    private val imageLabeler = ImageLabeling.getClient(
        ImageLabelerOptions.Builder()
            .setConfidenceThreshold(0.40f)
            .build()
    )

    private var lastAnalyzedTimestamp = 0L
    private val frameIntervalMs = 180L
    private val isClosed = AtomicBoolean(false)
    private val nativeClosed = AtomicBoolean(false)
    private val isProcessing = AtomicBoolean(false)
    private val detectorLock = Any()

    fun markClosing() {
        isClosed.set(true)
    }

    @SuppressLint("UnsafeOptInUsageError")
    override fun analyze(imageProxy: ImageProxy) {
        if (isClosed.get()) {
            imageProxy.close()
            return
        }

        val currentTimestamp = System.currentTimeMillis()
        if (currentTimestamp - lastAnalyzedTimestamp < frameIntervalMs) {
            imageProxy.close()
            return
        }

        // Prevent overlapping async ML Kit tasks from queuing multiple open ImageProxies.
        if (!isProcessing.compareAndSet(false, true)) {
            imageProxy.close()
            return
        }

        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            isProcessing.set(false)
            imageProxy.close()
            return
        }

        var delegatedToLabeler = false
        var rawBitmap: Bitmap? = null
        var bitmap: Bitmap? = null

        try {
            lastAnalyzedTimestamp = currentTimestamp
            val rotationDegrees = imageProxy.imageInfo.rotationDegrees

            val isRotated = rotationDegrees == 90 || rotationDegrees == 270
            val imageWidth = if (isRotated) imageProxy.height else imageProxy.width
            val imageHeight = if (isRotated) imageProxy.width else imageProxy.height

            val rawObjects = try {
                val createdRaw = imageProxy.toBitmap()
                rawBitmap = createdRaw
                val preparedBitmap = if (rotationDegrees != 0) {
                    val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
                    val rotated = Bitmap.createBitmap(
                        createdRaw,
                        0,
                        0,
                        createdRaw.width,
                        createdRaw.height,
                        matrix,
                        true
                    )
                    if (rotated !== createdRaw) {
                        createdRaw.recycle()
                        rawBitmap = null
                    }
                    rotated
                } else {
                    createdRaw
                }
                bitmap = preparedBitmap

                if (isClosed.get() || nativeClosed.get()) {
                    emptyList()
                } else {
                    val mpImage = BitmapImageBuilder(preparedBitmap).build()
                    try {
                        synchronized(detectorLock) {
                            if (isClosed.get() || nativeClosed.get()) {
                                emptyList()
                            } else {
                                objectDetector.detect(mpImage).detections().map { detection ->
                                    val box = detection.boundingBox()
                                    val domainBox = BoundingBox2D(
                                        left = box.left,
                                        top = box.top,
                                        right = box.right,
                                        bottom = box.bottom
                                    )
                                    val labels = detection.categories().map {
                                        RawLabel(it.categoryName(), it.score())
                                    }
                                    val topName = labels.firstOrNull()?.text.orEmpty()
                                    val needsAppearanceStats =
                                        topName.equals("person", ignoreCase = true) ||
                                            ObstacleCatalog.isAnimalClass(topName)
                                    val appearanceStats = if (needsAppearanceStats) {
                                        BitmapAppearanceExtractor.extractFromBitmap(preparedBitmap, domainBox)
                                    } else {
                                        null
                                    }
                                    RawDetectedObject(
                                        boundingBox = domainBox,
                                        labels = labels,
                                        appearanceStats = appearanceStats
                                    )
                                }
                            }
                        }
                    } finally {
                        mpImage.close()
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Object detection failed for frame", e)
                emptyList()
            } finally {
                bitmap?.let { bmp ->
                    if (!bmp.isRecycled) {
                        bmp.recycle()
                    }
                }
                rawBitmap?.let { raw ->
                    if (!raw.isRecycled) {
                        raw.recycle()
                    }
                }
                bitmap = null
                rawBitmap = null
            }

            if (isClosed.get()) {
                return
            }

            val inputImage = InputImage.fromMediaImage(mediaImage, rotationDegrees)

            imageLabeler.process(inputImage)
                .addOnSuccessListener { labels ->
                    if (isClosed.get()) return@addOnSuccessListener
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
                .addOnFailureListener { e ->
                    Log.w(TAG, "Image labeling failed for frame", e)
                }
                .addOnCompleteListener {
                    isProcessing.set(false)
                    imageProxy.close()
                }
            delegatedToLabeler = true
        } catch (e: Exception) {
            Log.w(TAG, "Frame analysis aborted", e)
        } finally {
            if (!delegatedToLabeler) {
                isProcessing.set(false)
                imageProxy.close()
            }
        }
    }

    override fun close() {
        isClosed.set(true)
        if (!nativeClosed.compareAndSet(false, true)) return
        synchronized(detectorLock) {
            runCatching { objectDetector.close() }
        }
        runCatching { imageLabeler.close() }
    }

    companion object {
        private const val TAG = "VisionAnalyzer"
    }
}
