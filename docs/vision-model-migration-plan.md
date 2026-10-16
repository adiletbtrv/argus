# Vision model migration plan: COCO object detection

Branch: `feature/vision-model-upgrade` · Scope: `android/` · Status: steps 1 (asset), 5 and 7 are done; everything else is still open.

Follow this document exactly. Don't change the scope and don't touch the ImageLabeler pipeline or the
stairs/door/wall logic in `HazardClassifier.kt`.

---

## 0. Problem

`VisionAnalyzer.kt` uses the ML Kit default `ObjectDetectorOptions`. Its built-in classifier only knows
5 coarse categories (`Fashion good`, `Food good`, `Home good`, `Place`, `Plant`), so the app announces
"Home good ahead". The goal is to use a COCO-trained detector (80 classes) so the app says "chair ahead".

---

## 1. Model choice (Task 1)

### 1.1 Key correction: ML Kit Custom Object Detection cannot load EfficientDet

ML Kit's `CustomObjectDetectorOptions` + `LocalModel` (artifact `object-detection-custom`) only works with
**image-classification** models (output `[1, N]` class scores). ML Kit uses its own class-agnostic box
detector and then runs your classifier on each crop. **Detection models such as EfficientDet-Lite0
(outputs are boxes, classes, scores and count) are not supported** and fail at load time.

That means the migration **does not use** `CustomObjectDetectorOptions`. Instead, it switches the object
detector to **MediaPipe Tasks Vision `ObjectDetector`**. This is Google's official on-device runtime for
EfficientDet-Lite, it reads the embedded label metadata "out of the box", and it replaces the deprecated
TFLite Task Library.

| Option | Verdict |
|---|---|
| ML Kit custom OD + EfficientDet-Lite0 | ❌ Incompatible: needs a classifier, not a detector |
| ML Kit custom OD + ImageNet classifier (MobileNet/EfficientNet-Lite) | ❌ Labels like "studio couch" or "folding chair", and 1000 noisy classes. Boxes still come from the weak generic detector |
| **MediaPipe `ObjectDetector` + EfficientDet-Lite0 int8 (COCO)** | ✅ **Chosen** |

### 1.2 Model specification (verified 2026-10-16)

| Field | Value |
|---|---|
| Model | EfficientDet-Lite0, int8 quantized, COCO 2017 |
| Official source | MediaPipe model card / Object Detector docs (ai.google.dev/edge/mediapipe/solutions/vision/object_detector) |
| Download URL | `https://storage.googleapis.com/mediapipe-models/object_detector/efficientdet_lite0/int8/latest/efficientdet_lite0.tflite` |
| Size | **4 602 795 bytes (≈4.4 MB)** (HTTP `Content-Length`, Last-Modified 2023-04-27) |
| SHA-256 | `0720BF247BD76E6594EA28FA9C6F7C5242BE774818997DBBEFFC4DA460C723BB` |
| Input | 320×320 RGB |
| Metadata | Embedded `labels.txt`: 90 entries, of which 80 are real COCO classes plus 10 `???` placeholders |
| Repo path | `android/app/src/main/assets/efficientdet_lite0.tflite` ✅ committed |

Embedded labels (verified by reading the model's zip metadata):
`person, bicycle, car, motorcycle, airplane, bus, train, truck, boat, traffic light, fire hydrant, stop sign,
parking meter, bench, bird, cat, dog, horse, sheep, cow, elephant, bear, zebra, giraffe, backpack, umbrella,
handbag, tie, suitcase, frisbee, skis, snowboard, sports ball, kite, baseball bat, baseball glove, skateboard,
surfboard, tennis racket, bottle, wine glass, cup, fork, knife, spoon, bowl, banana, apple, sandwich, orange,
broccoli, carrot, hot dog, pizza, donut, cake, chair, couch, potted plant, bed, dining table, toilet, tv,
laptop, mouse, remote, keyboard, cell phone, microwave, oven, toaster, sink, refrigerator, book, clock, vase,
scissors, teddy bear, hair drier, toothbrush` (+ `???`)

---

## 2. Speech vocabulary and phrases (Task 2)

### 2.1 Rules

- English only. The locale stays the same.
- Confidence numbers are **never spoken**. `DetectedObstacle.label` already has no `%`. Percentages stay
  only in `VisionFrameResult.rawLabels`, which is UI-only.
- Labels the model returns as is are spoken as is. Multi-word classes (`dining table`, `potted plant`,
  `cell phone`, `fire hydrant`, `traffic light`, `stop sign`, …) stay unchanged.
- `LabelFormatter` keeps capitalizing the first letter for the UI. TTS ignores case.
- Detections labelled `???` are **dropped** and never shown or spoken.

### 2.2 COCO → spoken form

Only the rows marked **override** need code. Everything else is identity.

| COCO label(s) | Spoken / displayed | Why |
|---|---|---|
| `tv` | **TV** | Spelling it in caps makes TTS read the letters "T V" |
| `mouse` | **computer mouse** | Otherwise it's ambiguous with the animal |
| `remote` | **remote control** | "Remote" alone is unclear |
| `sports ball` | **ball** | More natural |
| `hair drier` | **hair dryer** | Fixes the COCO spelling |
| `???` | *(drop detection)* | Metadata placeholder |
| `person`, `bicycle`, `car`, `motorcycle`, `bus`, `truck`, `train`, `boat`, `airplane` | identity | Mobility-critical |
| `traffic light`, `fire hydrant`, `stop sign`, `parking meter`, `bench` | identity | Street furniture |
| `chair`, `couch`, `bed`, `dining table`, `toilet`, `potted plant`, `sink`, `refrigerator`, `oven`, `microwave`, `toaster` | identity | Indoor obstacles |
| `dog`, `cat`, `bird`, `horse`, `sheep`, `cow`, `elephant`, `bear`, `zebra`, `giraffe` | identity | Animals |
| `backpack`, `umbrella`, `handbag`, `suitcase`, `tie`, `book`, `clock`, `vase`, `scissors`, `teddy bear`, `toothbrush`, `laptop`, `keyboard`, `cell phone` | identity | Objects |
| `bottle`, `wine glass`, `cup`, `fork`, `knife`, `spoon`, `bowl`, food classes, sports gear | identity | Objects |

### 2.3 `CenterObstacleAlertStrategy` phrase templates

Current templates (`AlertStrategy.kt` L116-122): NEAR → `"Close: $label"`, MEDIUM → `"$label ahead"`.

- MEDIUM, `"$label ahead"`: **keep as is**. It's short and matches `"Doorway ahead"` and `"Armrest ahead"`.
- NEAR, `"Close: $label"`: **replace with `"Careful, $label right ahead"`**. Reason: when "Close" starts the
  sentence, Android TTS engines often pronounce it as the verb /kloʊz/ ("close the door"). That's ambiguous
  for a blind user, and the colon adds nothing to the meaning. "Careful," marks urgency the same way
  `"Caution, stairs ahead!"` and `"Warning, wall…"` do, and "right ahead" gives the distance.

Final templates (use the label in lowercase for speech: `label.lowercase()`, except `TV`):

| Zone | Distance | Phrase | Example |
|---|---|---|---|
| CENTER | NEAR | `Careful, {label} right ahead` | "Careful, chair right ahead" |
| CENTER | MEDIUM | `{label} ahead` | "Person ahead" |
| CENTER | FAR | *(silent, unchanged)* | — |

Side zones stay silent for generic objects (behavior unchanged, out of scope). If side zones are added later,
reuse `"{label} on the left/right"` to match `ArmrestAlertStrategy`.

Test impact: `EvaluateHazardsUseCaseTest.kt:185` expects `"Close: Table"`. Change it to `"Careful, table right ahead"`.

---

## 3. Migration steps (Task 3)

### Step 1: Model asset ✅ DONE
- `android/app/src/main/assets/efficientdet_lite0.tflite` (4.4 MB) is committed as a regular file, since it's under 10 MB.
  Git LFS isn't needed. Don't add it to `.gitignore`: the app needs it at build time.
- **Remaining:** add this to the `android {}` block in `app/build.gradle.kts` so the asset stays mmap-able:
  ```kotlin
  androidResources { noCompress += "tflite" }
  ```

### Step 2: Swap the detector in `VisionAnalyzer.kt` (instead of `CustomObjectDetectorOptions`, see §1.1)
1. Remove `ObjectDetection` / `ObjectDetectorOptions` imports and the `objectDetector` field.
   **Keep `imageLabeler` exactly as it is.**
2. Constructor: add `context: Context` as the first parameter. Update the single call site in
   `ui/camera/CameraPreviewView.kt:96` (`VisionAnalyzer(context) { result -> … }`, using `LocalContext.current`).
3. Create the detector:
   ```kotlin
   private val objectDetector = ObjectDetector.createFromOptions(
       context,
       ObjectDetector.ObjectDetectorOptions.builder()
           .setBaseOptions(BaseOptions.builder().setModelAssetPath("efficientdet_lite0.tflite").build())
           .setRunningMode(RunningMode.IMAGE)
           .setScoreThreshold(0.5f)   // equivalent of classificationConfidenceThreshold
           .setMaxResults(5)
           .build()
   )
   ```
   Imports: `com.google.mediapipe.tasks.core.BaseOptions`, `com.google.mediapipe.tasks.vision.core.RunningMode`,
   `com.google.mediapipe.tasks.vision.objectdetector.ObjectDetector`, `com.google.mediapipe.framework.image.BitmapImageBuilder`.
4. In `analyze()`:
   - Build the upright bitmap: `imageProxy.toBitmap()` (CameraX ≥1.3), then rotate it by `rotationDegrees`
     with a `Matrix`. Boxes then come back in the same upright frame that `imageWidth`/`imageHeight` already
     describe. Don't use `ImageProcessingOptions` rotation.
   - `val detections = objectDetector.detect(BitmapImageBuilder(bitmap).build()).detections()` runs
     synchronously on the analyzer executor, which is fine at the existing 180 ms throttle. Wrap it in
     `runCatching` so a failure yields an empty list.
   - Map each one to `RawDetectedObject(BoundingBox2D(box.left, box.top, box.right, box.bottom),
     categories().map { RawLabel(it.categoryName(), it.score()) })`, where `box = detection.boundingBox()` (a `RectF`).
   - Keep `imageLabeler.process(inputImage)` and call `processor.process(...)` in its completion listener
     (replaces `Tasks.whenAllComplete`). Keep `imageProxy.close()` in `addOnCompleteListener`.
5. Add `fun close() { objectDetector.close(); imageLabeler.close() }` and call it when the analyzer is
   cleared or unbound in `CameraPreviewView` (`DisposableEffect`/`onDispose`).

### Step 3: Threshold
- `setScoreThreshold(0.5f)` (Step 2.3). Tune it within 0.45–0.6 on device if needed. Don't add a second
  filter in the processor.

### Step 4: Dependencies (`gradle/libs.versions.toml`, `app/build.gradle.kts`)
- Add:
  ```toml
  [versions]
  mediapipeTasksVision = "1.1.1"   # latest listed in Google Maven index on 2026-10-16; fallback 0.10.35
  [libraries]
  mediapipe-tasks-vision = { group = "com.google.mediapipe", name = "tasks-vision", version.ref = "mediapipeTasksVision" }
  ```
  and `implementation(libs.mediapipe.tasks.vision)`.
- Remove `mlkit-objectdetection` (toml line 42, version `mlkitObjectDetection`) and `implementation(libs.mlkit.objectdetection)`.
  **Keep `mlkit-image-labeling`.**
- `object-detection-custom` is **not** needed (§1.1).
- Optional APK size: `defaultConfig { ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") } }`.
- minSdk 26 is compatible with MediaPipe Tasks (needs 24 or higher).

### Step 5: Remove the `"good"` hack in `HazardClassifier.kt` ✅ DONE
- Removed `rawText.contains("good")` from `isArmrestObject()`. Added `"couch"`, since COCO returns
  `chair`/`couch` directly. All other logic is unchanged.
- **Remaining (with Step 6):** remove the `"Home good" → "Furniture"` branch and its KDoc mention from
  `LabelFormatter.format()`. Add an alias map from §2.2 (`tv→TV`, `mouse→computer mouse`,
  `remote→remote control`, `sports ball→ball`, `hair drier→hair dryer`) applied before capitalization.
- In `VisionResultProcessor.process()`, filter out objects whose top label is `"???"` before `mapIndexed`.

### Step 6: Tests (`VisionResultProcessorTest.kt`)
| Test | Action |
|---|---|
| `VisionResultProcessor - obstacle label remapped to Armrest when armrest dominant` | ✅ DONE: the fixture now uses `"couch"` instead of `"Home good"` |
| `LabelFormatter - formats Home good to Furniture and capitalizes words` | **Replace** with `LabelFormatter - maps COCO labels to display form`: `"person"→"Person"`, `"dining table"→"Dining table"`, `"tv"→"TV"`, `"mouse"→"Computer mouse"`, `"remote"→"Remote control"`, `"armrest"→"Armrest"`. No `Home good` assertions remain. |
| *(new)* `HazardClassifier - legacy ML Kit category is not treated as armrest` | `assertFalse(isArmrestObject("Home good", true))`, `assertTrue(isArmrestObject("couch", true))` |
| *(new)* `VisionResultProcessor - drops placeholder labels` | an object with label `"???"` produces no obstacle |
| `EvaluateHazardsUseCaseTest.kt:185` | `"Close: Table"` → `"Careful, table right ahead"` (after the §2.3 change in `AlertStrategy.kt`) |

All HazardClassifier stairs/door/wall/armrest tests stay unchanged.

### Step 7: R8 keep rules ✅ DONE (preventive)
- `isMinifyEnabled` is currently off. The rules were added to `app/src/main/keepRules/rules.keep` anyway, so
  that enabling R8 later doesn't break MediaPipe's JNI or protobuf reflection:
  ```
  -keep class com.google.mediapipe.** { *; }
  -keep class * extends com.google.protobuf.GeneratedMessageLite { *; }
  -dontwarn com.google.mediapipe.**
  -dontwarn com.google.protobuf.**
  ```

### Step 8: Verification
1. `./gradlew :app:testDebugUnitTest` passes.
2. `./gradlew :app:assembleDebug`, then check that the APK contains `assets/efficientdet_lite0.tflite` stored uncompressed.
3. On-device: point the camera at a chair, a person and a bottle. Expect "Chair ahead" / "Careful, person right ahead",
   and never "Home good" or "Fashion good". Stairs, door and wall alerts behave as before.

## Commit plan (Conventional Commits)
- `feat(vision): add EfficientDet-Lite0 COCO model asset` ✅
- `refactor(vision): drop legacy "good" category workaround` ✅
- `build(android): add R8 keep rules for MediaPipe Tasks` ✅
- `build(android): replace ML Kit object detection with MediaPipe Tasks Vision`
- `feat(vision): detect COCO objects with MediaPipe EfficientDet-Lite0`
- `feat(vision): map COCO labels to spoken form and drop placeholders`
- `feat(alerts): clearer near-obstacle phrasing`
- `test(vision): cover COCO labels instead of ML Kit coarse categories`
