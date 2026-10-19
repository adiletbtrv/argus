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

## 2026-10-19: Pre-Midterm Firmware Upgrade (Non-blocking Vibration, Autocalibration, Periodic Telemetry, Charging Detector)

- **Branch**: `feature/pre-midterm-upgrade`
- **Issue**: [#11](https://github.com/adiletbtrv/argus/issues/11), [#40](https://github.com/adiletbtrv/argus/issues/40)
- **Role**: Embedded Developer

### Changes Summary
1. **Non-blocking Haptic Vibration (Phase 2)**:
   - Replaced blocking `delay(20)` in `loop()` with a two-phase millis() state machine (`updateVibrationFSM`).
   - Implemented dynamic pulse pause calculation based on obstacle distance: `pulseIntervalMs = map(constrain(currentDist, 5, 50), 5, 50, 40, 400)`.
   - Used state machine variables `lastPulseToggle`, `pulseState`, and `pulseIntervalMs`.
   - Commit: `6eb6e45` (`feat(firmware): implement non-blocking haptic vibration state machine`).

2. **I2C Bus Protection & Startup Roll Auto-Calibration (Phase 3)**:
   - Added hardware I2C timeout protection: `Wire.setWireTimeout(3000, true)` right after `Wire.begin()`.
   - Implemented zero-point roll auto-calibration at boot: averaged first 20 readings of `mpu.getAngleX()` into `rollOffset`.
   - Subtracted `rollOffset` from roll measurements in `loop()` to eliminate zero drift.
   - Closed Issue #11 with architectural rationale.
   - Commit: `a2b8af6` (`feat(firmware): add i2c bus timeout protection and startup roll auto-calibration`).

3. **Periodic Telemetry (Phase 4)**:
   - Decoupled HC-05 Bluetooth telemetry from obstacle detection events.
   - Added periodic 180 ms non-blocking millis() timer (`TELEMETRY_INTERVAL_MS = 180`).
   - Retained strict telemetry payload format: `DIST:<dist>,DIR:<dir>\n`.
   - Commit: `b7bb94d` (`feat(firmware): send telemetry periodically independent of obstacle detection`).

4. **Charging Connection Detector (Phase 5)**:
   - Configured pin A1 (`PIN_CHARGING = A1`) as input for Battery Shield (TP4056) charge detection.
   - Implemented rising edge detection (`currentChargingState && !lastChargingState`) using analog threshold > 512 (> 2.5V).
   - Implemented non-blocking double tactile pulse confirmation (`updateChargingVibro`), triggering only once per connection cycle.
   - Commit: `752f504` (`feat(firmware): add charging connection edge detector with haptic double-pulse`).

5. **Verification & Cleanliness (Phase 6 & 8)**:
   - Ran `firmware/direction_logic/test_direction_logic.cpp` test suite: 5/5 tests passed.
   - Verified code cleanliness: no em-dashes, no ASSUMPTION keywords, short concise student-style comments.
   - Documented pin A1 and charging detection logic in `docs/pinout.md`.
   - Verified build with Arduino CLI for `arduino:avr:nano`: Flash 13288 bytes (43%), RAM 697 bytes (34%).

