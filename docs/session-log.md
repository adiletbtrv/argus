# Session Log

## 2026-10-16: Vision Model Upgrade (COCO EfficientDet-Lite0)

- **Branch**: `feature/vision-model-upgrade`
- **Issue**: [#26](https://github.com/adiletbtrv/argus/issues/26) (Milestone: *App Bluetooth MVP*)
- **Pull Request**: [#27](https://github.com/adiletbtrv/argus/pull/27)

### Changes Summary
1. **Model & Dependencies**:
   - Added quantized `efficientdet_lite0.tflite` (int8, 4.4 MB, COCO 80 classes) to `android/app/src/main/assets/`.
   - Configured `androidResources { noCompress += "tflite" }` in `android/app/build.gradle.kts`.
   - Swapped `com.google.mlkit:object-detection` for `com.google.mediapipe:tasks-vision:1.1.1` in `libs.versions.toml` and `build.gradle.kts`. Kept `mlkit-image-labeling` intact.
   - Added R8 keep rules for MediaPipe Tasks in `android/app/src/main/keepRules/rules.keep`.
2. **Vision Pipeline**:
   - `VisionAnalyzer.kt`: Replaced ML Kit Object Detection with MediaPipe Tasks `ObjectDetector`. Added frame bitmap rotation matrix alignment and lifecycle `close()` implementation.
   - `CameraPreviewView.kt`: Integrated `VisionAnalyzer(context)` with Compose `DisposableEffect` for cleanup.
   - `HazardClassifier.kt`: Removed legacy `contains("good")` workaround, added direct matching for `chair`/`couch`.
   - `LabelFormatter.kt`: Removed `"Home good"` alias, added conversational mapping for ambiguous classes (`tv` -> `TV`, `mouse` -> `computer mouse`, `remote` -> `remote control`, `sports ball` -> `ball`, `hair drier` -> `hair dryer`).
   - `VisionResultProcessor.kt`: Filtered placeholder `"???"` labels.
3. **Alert Strategy (TTS)**:
   - `AlertStrategy.kt`: Updated `CenterObstacleAlertStrategy` for near obstacles to `"Careful, $spokenLabel right ahead"` with lowercase spoken label formatting (except `"TV"`).
4. **Verification & Quality**:
   - Cleaned all em-dashes and artificial commentary tags across Kotlin files.
   - Updated unit tests in `VisionResultProcessorTest.kt` and `EvaluateHazardsUseCaseTest.kt`. All 30 unit tests pass (`./gradlew testDebugUnitTest`).
   - Updated `VisionIntegrationTest.kt` for context-aware analyzer lifecycle.
   - APK built successfully (`./gradlew assembleDebug`), verified `assets/efficientdet_lite0.tflite` stored uncompressed in APK.
