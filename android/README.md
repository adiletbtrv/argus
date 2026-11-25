# 📱 Argus Android Application

> Автономный мобильный ассистент компьютерного зрения и тактильно-голосовой навигации для незрячих и слабовидящих пользователей на базе CameraX, MediaPipe Tasks и ML Kit.

[![Kotlin](https://img.shields.io/badge/Kotlin-2.2.10-7F52FF?style=flat&logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![Android](https://img.shields.io/badge/Android-8.0%2B%20(API%2026%2B)-3DDC84?style=flat&logo=android&logoColor=white)](https://developer.android.com/)
[![Compose](https://img.shields.io/badge/Jetpack%20Compose-BOM%202026.02.01-4285F4?style=flat&logo=jetpackcompose&logoColor=white)](https://developer.android.com/compose)
[![MediaPipe](https://img.shields.io/badge/MediaPipe-Tasks%20Vision%201.1.1-00796B?style=flat&logo=google&logoColor=white)](https://ai.google.dev/edge/mediapipe/solutions/vision/object_detector)
[![ML Kit](https://img.shields.io/badge/ML%20Kit-Image%20Labeling-FBBC04?style=flat&logo=google&logoColor=white)](https://developers.google.com/ml-kit)
[![License: MIT](https://img.shields.io/badge/License-MIT-green.svg?style=flat)](../LICENSE)

---

Android-приложение системы **Argus** в реальном времени анализирует видеопоток с камеры смартфона, распознает 10 целевых классов физических препятствий COCO (`person`, `chair`, `dining table`, `bench`, `backpack`, `suitcase`, `potted plant`, `car`, `bicycle`, `dog`) и опасные элементы окружения (лестницы, стены, дверные проемы, мебель и подлокотники), объединяет их с данными сонара и гироскопа трости по одностороннему каналу Bluetooth SPP, озвучивает русские голосовые подсказки и дублирует срочные угрозы тактильным виброоткликом смартфона.

Все нейросетевые вычисления и геометрическая фильтрация выполняются **локально на устройстве** без обращения к внешним серверам и без подключения к интернету. Приложение входит в состав общего репозитория [Argus](../README.md) и может работать как самостоятельно (включая демонстрационный режим), так и в паре с аппаратной тростью.

---

## Содержание

- [Статус проекта](#статус-проекта)
- [Возможности](#возможности)
- [Требования к окружению](#требования-к-окружению)
- [Установка и запуск](#установка-и-запуск)
  - [Android Studio](#android-studio)
  - [Командная строка](#командная-строка)
  - [Сборка Release APK](#сборка-release-apk)
- [Использование и голосовой интерфейс](#использование-и-голосовой-интерфейс)
- [Как это работает](#как-это-работает)
  - [Конвейер обработки кадров](#конвейер-обработки-кадров)
  - [Пространственные зоны и оценка дистанции](#пространственные-зоны-и-оценка-дистанции)
  - [Классификация опасностей и дедупликация речи](#классификация-опасностей-и-дедупликация-речи)
- [Архитектура](#архитектура)
  - [Структура каталогов](#структура-каталогов)
  - [Стек технологий](#стек-технологий)
- [Разрешения Android](#разрешения-android)
- [Конфиденциальность и безопасность](#конфиденциальность-и-безопасность)
- [Тестирование](#тестирование)
- [Планы развития](#планы-развития)
- [Полезные ссылки](#полезные-ссылки)
- [Лицензия](#лицензия)

---

## Статус проекта

Текущая стадия: готовность к финальной защите (**версия 1.0, versionCode 1**, 67 модульных JVM-тестов + инструментальные тесты).

| Функция | Реализация | Состояние |
|:---|:---|:---:|
| **Видеопоток с камеры и защита памяти** | CameraX ImageAnalysis (`YUV_420_888`, ~5.5 FPS), безопасное закрытие `VisionAnalyzer` / `Bitmap` / `MPImage` / `ImageProxy` без блокировки UI-потока | Реализовано |
| **Детекция объектов (10 классов COCO)** | MediaPipe Tasks Vision (`EfficientDet-Lite0` int8, ~4.4 МБ) с белым списком 10 навигационных классов | Реализовано, покрыто тестами |
| **Классификация окружения (сцены)** | Google ML Kit Image Labeling (порог 0.40) + `HazardClassifier` | Реализовано, покрыто тестами |
| **Приоритизация угроз и Sensor Fusion** | Единая 5-уровневая иерархия (`EvaluateHazardsUseCase` + `EvaluateSensorFusionUseCase` + `MainViewModel`) | Реализовано, покрыто тестами |
| **Озвучивание подсказок** | Android Text-to-Speech (`ru-RU` с системным фолбэком, кулдаун 4 с на повторы) | Реализовано |
| **Прием телеметрии с трости по Bluetooth SPP** | Односторонний прием от `HC-05`/`HC-06`/`ARGUS` (`AtomicReference` + `connectionGeneration`, проверка `BLUETOOTH_SCAN`, демо-режим) | Реализовано, покрыто тестами |
| **Тактильный виброотклик смартфона** | `HapticFeedbackService` + `HapticCooldownGate` (`1500 мс` на `SystemClock::elapsedRealtime`) + двойной импульс `AndroidHapticFeedbackService` на срочных угрозах | Реализовано, покрыто тестами |
| **Доступность TalkBack и удержание экрана** | Семантика Compose (`semantics`, полные единицы в сантиметрах, `LiveRegionMode.Polite`, `heading()`, кнопки `>= 48.dp`, скрытие декоративного `Canvas`) и `FLAG_KEEP_SCREEN_ON` | Реализовано, покрыто тестами |

---

## Возможности

- **Потокобезопасный конвейер компьютерного зрения**: анализ кадров с пропуском устаревших буферов (`STRATEGY_KEEP_ONLY_LATEST`, ~5.5 FPS), синхронизацией нативного детектора по `detectorLock` / `nativeClosed`, гарантированным однократным `imageProxy.close()` и `Bitmap.recycle()` в `try/finally` (включая сбои `OutOfMemoryError`) и неблокирующей остановкой `cameraExecutor` при выходе с экрана.
- **Детекция 10 целевых классов COCO**: выделение значимых физических препятствий (`person`, `chair`, `dining table`, `bench`, `backpack`, `suitcase`, `potted plant`, `car`, `bicycle`, `dog`) с переводом на русский язык (`ObstacleTranslator`).
- **Разделение на пространственные зоны**: классификация препятствий по горизонтальным секторам (`LEFT`, `CENTER`, `RIGHT`) и трем категориям дистанции (`NEAR >= 28%`, `MEDIUM 12%-28%`, `FAR 6%-12%`, объекты `< 6%` отсекаются).
- **Распознавание опасных элементов инфраструктуры**: классификатор лестниц (подтверждение на 2 кадрах в окне `< 750 мс`), глухих стен, дверных проемов и подлокотников (`HazardClassifier`).
- **Sensor Fusion, голосовое сопровождение и виброотклик смартфона**: объединение ультразвукового сонара и крена трости со зрением камеры; срочные предупреждения (`isUrgent == true`: сонар `< 35 см`, лестница, боковой конфликт, стена, объект `NEAR`) мгновенно прерывают текущую речь (`QUEUE_FLUSH`) и сопровождаются двойным тактильным импульсом смартфона (`0/120/80/180 мс`) с защитой от непрерывного дребезга (`1500 мс`).
- **Корректная индикация дистанции и потокобезопасный Bluetooth**: при отсутствии препятствий ближе 2 м (`DIST:-1`) карточка дистанции отображает зеленый статус `"> 200 СМ"` вместо ложной тревоги `"-1 СМ"`; управление сокетом защищено счетчиком поколений `connectionGeneration` и проверкой `BLUETOOTH_SCAN` перед опциональным `cancelDiscovery()` на Android 12+.
- **Поддержка скринридера TalkBack и защита от гашения экрана**: объединенные семантические карточки с естественным произношением дистанции в сантиметрах, кнопки высотой не менее `48.dp`, исключение декоративного оверлея `Canvas` из дерева фокуса и автоматическое удержание `FLAG_KEEP_SCREEN_ON` на время сканирования маршрута.
- **Локальный демо-режим для проверки и защиты**: кнопка `"ТЕСТ / КАЛИБРОВКА"` сначала пробует подключиться к сопряженной трости `HC-05`/`HC-06`/`ARGUS`, а при ее отсутствии запускает локальную демонстрационную последовательность телеметрии на смартфоне без записи в сокет.

---

## Требования к окружению

| Компонент | Требование | Примечание |
|:---|:---|:---|
| **Android Studio** | Ladybug / Meerkat или новее | Поддержка Android Gradle Plugin 9.4.1 |
| **Android SDK** | Platform 37, Build-Tools 36.0.0 | Min SDK 26 (Android 8.0 Oreo), Target SDK 37 |
| **JDK** | Java 17 или Java 21 | Gradle daemon настроен на `toolchainVersion=21` (`gradle-daemon-jvm.properties`) |
| **Gradle** | 9.6.0 | Запускается через встроенный Gradle Wrapper (`./gradlew`) |
| **Устройство** | Android 8.0+ с задней камерой | Аппаратная камера обязательна (`android.hardware.camera`) |
| **Синтезатор речи** | Google TTS или аналогичный движок | Голосовой пакет русского языка (`ru-RU`) или системный фолбэк |

---

## Установка и запуск

### Android Studio

1. Откройте каталог `android/` через меню **File -> Open**.
2. Дождитесь окончания синхронизации Gradle и загрузки зависимостей.
3. Подключите реальное Android-устройство по USB (включите режим "Отладка по USB" в настройках разработчика) либо запустите эмулятор с эмуляцией задней камеры (**VirtualScene** или **Webcam**).
4. Выберите модуль `app` в списке конфигураций и нажмите **Run** (`Shift + F10`).

### Командная строка

Сборка Debug APK и установка на подключенное устройство:

```bash
# Перейдите в каталог проекта
cd android

# Linux / macOS:
./gradlew assembleDebug
./gradlew installDebug

# Windows (PowerShell / CMD):
.\gradlew.bat assembleDebug
.\gradlew.bat installDebug
```

Собранный установочный пакет формируется по пути:
```text
android/app/build/outputs/apk/debug/app-debug.apk
```

### Сборка Release APK

```bash
./gradlew assembleRelease
```

В конфигурации release активирована оптимизация R8 для библиотек `androidx.**`, `kotlin.**` и `kotlinx.**`, а также включены keep-правила для JNI-библиотек MediaPipe Tasks (`rules.keep`). Для распространения APK подпишите файл вашим release-ключом в соответствии с [официальным руководством по подписи](https://developer.android.com/studio/publish/app-signing).

---

## Использование и голосовой интерфейс

1. **Запуск и выдача разрешений**: при первом открытии приложение запрашивает доступ к камере и Bluetooth (`BLUETOOTH_CONNECT` на Android 12+). Если доступ к камере не предоставлен, отображается поясняющий экран с заголовком `heading()` и доступной кнопкой повторного запроса (`>= 48.dp`).
2. **Активация сканера и панели статуса**: после предоставления доступа включается флаг `FLAG_KEEP_SCREEN_ON` и звучит фраза `"Камера активирована. Сканирование окружения запущено."` (либо `"Система Argus готова к работе."` при повторном запуске). В верхней части экрана отображаются:
   - плашки подтвержденных пространственных угроз (`HazardBadge` со стабильными ключами `key(hazard::class)`);
   - карточка `"ДИСТАНЦИЯ ТРОСТИ"` (`"--"` до приема данных, зеленая `"> 200 СМ"` на свободном пути `<= 0`, зеленая `"> 50 СМ"`, оранжевая `35..50 СМ`, красная `1..34 СМ`; для TalkBack озвучивается полным словом `"сантиметров"`);
   - карточка `"КУРС"` (`"ПРЯМО"`, `"ВЛЕВО"`, `"ВПРАВО"`);
   - индикатор статуса связи (`"БЛЮТУЗ: ПОДКЛЮЧЕНО"`, `"БЛЮТУЗ: ПОИСК HC-05..."`, `"БЛЮТУЗ: ДЕМО"`, `"БЛЮТУЗ: НЕТ СВЯЗИ"` с `LiveRegionMode.Polite`);
   - кнопка `"ТЕСТ / КАЛИБРОВКА"` (`>= 48.dp`) для переподключения сопряженной трости или запуска локальной демонстрации.
3. **Навигация**: держите телефон вертикально задней камерой вперед по направлению движения. На декоративном оверлее `Canvas` (скрытом от фокуса TalkBack) отображаются контуры объектов с цветовой дифференциацией:
   - **Красный контур**: объект прямо по курсу движения (зона `CENTER`);
   - **Желтый контур**: объект на левом фланге (зона `LEFT`);
   - **Зеленый контур**: объект на правом фланге (зона `RIGHT`);
   - **Голубой контур**: мебель, диван, кресло или подлокотник.

### Единая 5-уровневая таблица приоритетов, речи и виброотклика

Предупреждения оцениваются в каждом цикле обработки кадра и при поступлении критических пакетов телеметрии. Срабатывает **условие с наивысшим приоритетом**:

| Приоритет | Ситуация | Голосовое сообщение | Срочность (прерывание + вибро смартфона) |
|:---:|:---|:---|:---:|
| **1** | **Сонар трости `< 35 см` ИЛИ `StairsAhead`** | `"Внимание, близко препятствие: XX см!"` / `"Осторожно, впереди лестница!"` | **Да** (`QUEUE_FLUSH` + двойной виброимпульс) |
| **2** | **Боковой Sensor Fusion** (`LEFT`/`RIGHT` + объект в той же зоне) | `"Осторожно, слева преграда!"` / `"Осторожно, справа преграда!"` | **Да** (`QUEUE_FLUSH` + двойной виброимпульс) |
| **3** | **`WallProximity` ИЛИ близкий объект `NEAR` (`>= 28%`) по центру** | `"Внимание, стена прямо по курсу!"` / `"Вплотную, <объект>!"` | **Да** (`QUEUE_FLUSH` + двойной виброимпульс) |
| **4** | **`ArmrestDetected` ИЛИ `DoorAhead`** | `"Подлокотник <слева/справа/впереди>"` / `"Впереди дверной проем"` | **Нет** (`QUEUE_ADD`, без вибро смартфона) |
| **5** | **Препятствие `MEDIUM` (`12%-28%`) по центру с анализом коридора** | `"Прямо <объект>, обойдите слева"` / `"Прямо <объект>, обойдите справа"` / `"Впереди <объект>"` | **Нет** (`QUEUE_ADD`, без вибро смартфона) |

> **Перевод объектов и подавление повторов:** 10 целевых классов COCO переводятся на русский язык через `ObstacleTranslator` (`person` -> `"человек"`, `chair` -> `"стул"`, `dining table` -> `"стол"`, `potted plant` -> `"цветок"` и др.). Повторы одинаковых несрочных фраз в `AndroidVoiceAlertService` подавляются на 4 секунды (`SAME_TEXT_COOLDOWN_MS = 4000L`), а частота срочной вибрации смартфона ограничена окном 1.5 секунды (`HapticCooldownGate(cooldownMs = 1500L)`).

---

## Как это работает

### Конвейер обработки кадров

```text
               [ Видеопоток камеры CameraX ]
                 (ImageAnalysis, YUV_420_888)
                             │
                             ▼
                    ┌─────────────────┐
                    │  VisionAnalyzer │ -- throttle: 180 мс (~5.5 FPS)
                    └────────┬────────┘
                             │
            ┌────────────────┴────────────────┐
            ▼                                 ▼
┌───────────────────────────────┐ ┌───────────────────────────────┐
│     MediaPipe Tasks Vision    │ │     ML Kit Image Labeling     │
│   EfficientDet-Lite0 (int8)   │ │      Общий контекст сцены     │
│ 10 классов COCO, BoundingBox  │ │   (лестница, стена, дверь)    │
└───────────────┬───────────────┘ └───────────────┬───────────────┘
                │                                 │
                └────────────────┬────────────────┘
                                 │
                                 ▼
                   ┌───────────────────────────┐
                   │   VisionResultProcessor   │
                   ├───────────────────────────┤
                   │  • HazardClassifier       │
                   │  • SpatialClassifier      │
                   │  • LabelFormatter         │
                   └─────────────┬─────────────┘
                                 │ VisionFrameResult
                                 ▼
                   ┌───────────────────────────┐   ┌───────────────────────────┐
                   │  EvaluateHazardsUseCase   │   │    BluetoothCaneClient    │
                   │   (AlertStrategy Chain)   │   │  (Односторонний SPP HC-05)│
                   └─────────────┬─────────────┘   └─────────────┬─────────────┘
                                 │                               │ CaneTelemetry
                                 │     ┌─────────────────────────┴──────┐
                                 │     │  EvaluateSensorFusionUseCase   │
                                 │     └──────────────┬─────────────────┘
                                 ▼                    ▼
                     ┌───────────────────────────────────────────┐
                     │    MainViewModel (5-уровневый арбитраж)   │
                     └───┬───────────────────┬───────────────┬───┘
                         │                   │               │
           StateFlow<MainUiState>    VoiceAlertService   HapticFeedbackService
                         │               .speak()         .vibrateUrgent()
                         ▼                   ▼               ▼
                 [ Compose UI ]     [ Android TTS ]   [ Двойной импульс ]
```

### Пространственные зоны и оценка дистанции

- **Горизонтальные зоны (`HorizontalZone`)**: вычисляются по горизонтальному центру рамки объекта (`centerX`):
  - `LEFT`: центр рамки левее 33% ширины кадра;
  - `CENTER`: центр рамки в диапазоне от 33% до 66% ширины кадра;
  - `RIGHT`: центр рамки правее 66% ширины кадра.
- **Категории дистанции (`DistanceCategory`)**: рассчитываются эвристически по доле площади кадра, занимаемой рамкой объекта (`SpatialClassifier`):
  - `NEAR` (близко): объект занимает **>= 28%** площади кадра (`>= 0.28`);
  - `MEDIUM` (средняя дистанция): объект занимает **от 12% до 28%** площади кадра (`0.12 <= area < 0.28`);
  - `FAR` (далеко): объект занимает **от 6% до 12%** площади кадра (`0.06 <= area < 0.12`, отслеживается без прямой озвучки);
  - Менее **6%** площади кадра: объект игнорируется.

### Классификация опасностей и дедупликация речи

1. **Лестницы и ступени**: распознаются по меткам `stair`, `step`, `tread` (уверенность `>= 0.65`) и подтверждаются только при появлении минимум на двух кадрах подряд в окне `< 750 мс`.
2. **Диваны, кресла и подлокотники**: метки `armrest` (`>= 0.40`) и `chair`/`couch`/`sofa`/`furniture` (`>= 0.55` при `stairs < 0.72`) подавляют ложные срабатывания ступеней на обивке мебели.
3. **Глухие стены и дверные проемы**: метка `wall` (`>= 0.65`) срабатывает только при отсутствии физических объектов переднего плана (`!hasPhysicalObjects`); метка `door` (`>= 0.55`) формирует подсказку `DoorAhead`.
4. **Разделение ролей TTS и TalkBack**: живые голосовые предупреждения с кулдаунами ведет `AndroidVoiceAlertService`, тогда как Compose-карточки размечены семантикой для навигации жестами TalkBack без агрессивного перебивания речи.

---

## Архитектура

Проект построен по принципам **Clean Architecture** и **UDF (Unidirectional Data Flow)** (подробные обоснования см. в [`docs/adr.md`](../docs/adr.md)):
- **UI-слой** подписывается на неизменяемый поток `StateFlow<MainUiState>`, управляет `FLAG_KEEP_SCREEN_ON` и семантикой доступности.
- **Domain-слой** не содержит импортов `android.*` и `androidx.*` (контролируется в CI скриптом `check_repo_standards.py`) и оперирует чистыми контрактами (`SpeechOutput`, `HapticFeedbackService`, `HapticCooldownGate`, `CaneBluetoothClient`, `VisionProcessor`, `AlertStrategy`).
- **Data-слой** инкапсулирует MediaPipe / ML Kit, потокобезопасный односторонний клиент Bluetooth SPP, системный `TextToSpeech` и `Vibrator` / `VibratorManager`.

### Структура каталогов

```text
android/
├── app/
│   ├── build.gradle.kts                   # Конфигурация сборки модуля app, abiFilters, noCompress
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml        # Разрешения камеры, вибро, Bluetooth и декларация TTS
│       │   ├── assets/
│       │   │   └── efficientdet_lite0.tflite  # Модель детекции COCO (4.4 МБ, int8)
│       │   ├── java/com/project/argus/
│       │   │   ├── ArgusApplication.kt    # Инициализация зависимостей приложения
│       │   │   ├── MainActivity.kt        # Compose-хост + удержание FLAG_KEEP_SCREEN_ON
│       │   │   ├── data/
│       │   │   │   ├── bluetooth/         # BluetoothCaneClient (односторонний SPP, поколения сокета, демо)
│       │   │   │   ├── haptic/            # AndroidHapticFeedbackService (VibratorManager / VibrationEffect)
│       │   │   │   ├── tts/               # AndroidVoiceAlertService (ru-RU TTS + кулдауны)
│       │   │   │   └── vision/            # VisionAnalyzer, VisionResultProcessor,
│       │   │   │                          # HazardClassifier, SpatialClassifier, LabelFormatter
│       │   │   ├── domain/
│       │   │   │   ├── bluetooth/         # CaneBluetoothClient, BluetoothConnectionStatus
│       │   │   │   ├── haptic/            # HapticFeedbackService, NoOpHapticFeedbackService, HapticCooldownGate
│       │   │   │   ├── model/             # CaneTelemetry, ObstacleTranslator, VisionResult
│       │   │   │   ├── tts/               # SpeechOutput, VoiceAlertService, SpeechLifecycle
│       │   │   │   └── usecase/           # EvaluateHazardsUseCase, EvaluateSensorFusionUseCase, AlertStrategy
│       │   │   └── ui/
│       │   │       ├── camera/            # CameraPreviewView + resolveCaneDistanceDisplay + TalkBack semantics
│       │   │       ├── main/              # MainScreen, MainViewModel, MainUiState
│       │   │       │   └── components/    # HazardBadge, CameraPermissionView
│       │   │       └── theme/             # Color, Theme, Type (Material 3)
│       │   ├── keepRules/
│       │   │   └── rules.keep             # R8/ProGuard keep-правила для MediaPipe и protobuf-lite
│       │   └── res/                       # Векторные иконки, строковые ресурсы, цвета, темы
│       ├── test/                          # 9 наборов модульных JVM-тестов (67 тестов, JUnit 4 + Coroutines Test)
│       └── androidTest/                   # Инструментальные тесты Android (VisionIntegrationTest)
├── gradle/
│   ├── libs.versions.toml                 # Version Catalog: централизованные зависимости
│   ├── gradle-daemon-jvm.properties       # Конфигурация JVM daemon (toolchain 21)
│   └── wrapper/                           # Gradle Wrapper 9.6.0
├── build.gradle.kts                       # Корневой скрипт сборки
├── settings.gradle.kts                    # Настройки репозиториев (Google, MavenCentral)
└── gradle.properties                      # JVM-аргументы компилятора, configuration-cache
```

### Стек технологий

| Компонент | Версия | Описание |
|:---|:---|:---|
| **Kotlin** | `2.2.10` | Основной язык разработки приложения |
| **Android Gradle Plugin** | `9.4.1` | Система сборки Google Android |
| **Jetpack Compose BOM** | `2026.02.01` | Декларативный UI на базе Material Design 3 |
| **CameraX** | `1.4.1` | Захват и анализ кадров камеры в формате `YUV_420_888` |
| **MediaPipe Tasks Vision** | `1.1.1` | Локальный runtime детекции объектов COCO |
| **Google ML Kit Image Labeling** | `17.0.9` | Локальная классификация контекста окружения сцены |
| **KotlinX Coroutines** | `1.8.1` | Асинхронные потоки `StateFlow` и корутины |
| **AndroidX Lifecycle** | `2.11.0` | `ViewModel`, `lifecycle-runtime-compose` |

---

## Разрешения Android

| Разрешение | Назначение | Используется в текущей версии |
|:---|:---|:---:|
| `android.permission.CAMERA` | Видеопоток для нейросетевого распознавания | **Да** (запрашивается в runtime) |
| `android.permission.VIBRATE` | Двойной тактильный виброотклик смартфона при срочных угрозах | **Да** (`AndroidHapticFeedbackService`) |
| `android.permission.BLUETOOTH_CONNECT` | Подключение к сопряженной трости по Bluetooth SPP (Android 12+) | **Да** (запрашивается в runtime) |
| `android.permission.BLUETOOTH_SCAN` | Декларация с флагом `neverForLocation`; проверяется перед опциональным `cancelDiscovery()` | **Да** (без геолокации и без принудительного запроса в UI) |
| `android.permission.BLUETOOTH` / `ADMIN` | Подключение к трости на Android 8.0-11 (`maxSdkVersion=30`) | **Да** (для API 26-30) |

> Приложение принципиально не запрашивает разрешение `android.permission.INTERNET`, что исключает отправку пользовательских видеокадров и телеметрии во внешнюю сеть.

---

## Конфиденциальность и безопасность

- **100% On-Device Processing**: модели `EfficientDet-Lite0` и ML Kit исполняются локально на устройстве.
- **Строгий контроль ресурсов памяти**: буферы `ImageProxy`, `MPImage` и промежуточные `Bitmap` освобождаются сразу после обработки кадра; изображения никогда не сохраняются на накопитель.
- **Независимость контуров безопасности**: при закрытии приложения или разряде смартфона трость Argus продолжает автономную работу на базе собственного сонара, гироскопа и вибромотора в ручке.

---

## Тестирование

Все модульные тесты и компиляция инструментальных тестов автоматически выполняются в GitHub Actions на каждом Pull Request (`.github/workflows/android-ci.yml`).

### Запуск тестов

```bash
cd android

# Запуск всех модульных JVM-тестов и проверка компиляции androidTest:
./gradlew testDebugUnitTest compileDebugAndroidTestKotlin

# Запуск инструментальных тестов на подключенном устройстве или эмуляторе:
./gradlew connectedDebugAndroidTest
```

### Состав тестового набора (67 модульных JVM-тестов + инструментальные)

| Тестовый класс | Тип | Кол-во | Что верифицируется |
|:---|:---:|:---:|:---|
| **`EvaluateHazardsUseCaseTest`** | Unit (JVM) | 15 | Иерархия приоритетов оповещений (ступени, стены, `NEAR`/`MEDIUM` объекты, подлокотники, двери), временной фильтр лестниц (2 кадра в 750 мс). |
| **`MainViewModelTest`** | Unit (JVM) | 15 | Разрешения камеры и Bluetooth, арбитраж 5 уровней приоритетов, срабатывание `vibrateUrgent()` только на срочных угрозах и обновление `MainUiState`. |
| **`VisionResultProcessorTest`** | Unit (JVM) | 14 | Белый список 10 классов COCO, пороги площади `0.06`/`0.12`/`0.28`, зоны `LEFT`/`CENTER`/`RIGHT`, детекция угроз `HazardClassifier`. |
| **`EvaluateSensorFusionUseCaseTest`** | Unit (JVM) | 6 | Приоритет 1 сонара (`< 35 см`), приоритет 2 бокового Sensor Fusion (`LEFT`/`RIGHT`), отсечение устаревшей телеметрии (`>= 1000 мс`). |
| **`CaneTelemetryTest`** | Unit (JVM) | 5 | Парсинг протокола `DIST:<см>,DIR:<курс>`, порог `isCriticalDistance` (`1..34 см`), обработка свободного пути `DIST:-1` (`isCriticalDistance == false`). |
| **`CaneDistanceDisplayTest`** | Unit (JVM) | 5 | Граничные состояния карточки дистанции (`null`, `-1`, `0`, `1`, `34`, `35`, `50`, `51`, `120`), метка `"> 200 СМ"` и полные строки `contentDescription` для TalkBack. |
| **`HapticCooldownGateTest`** | Unit (JVM) | 4 | Первое срабатывание (включая `t = 0`), подавление повторов внутри окна `1500 мс`, восстановление при откате часов и `NoOpHapticFeedbackService`. |
| **`BluetoothCaneClientTest`** | Unit (JVM) | 2 | Регистронезависимый отбор сопряженных устройств (`HC-05`, `HC-06`, `ARGUS`) и отсечение посторонней гарнитуры. |
| **`ExampleUnitTest`** | Unit (JVM) | 1 | Базовая проверка окружения JUnit 4. |
| **`VisionIntegrationTest`** | Instrumented | 3 | Инициализация `AndroidVoiceAlertService`, создание `VisionAnalyzer` с реальным `Context` и сквозная связка COCO + `DoorAhead`. |
| **`ExampleInstrumentedTest`** | Instrumented | 1 | Проверка `applicationContext` пакета `com.project.argus`. |

---

## Планы развития

- [x] **Интеграция Bluetooth SPP клиента**: потокобезопасный односторонний прием телеметрии трости (`DIST:<см>,DIR:<CENTER|LEFT|RIGHT>`) и локальный демо-режим.
- [x] **Локализация на русский язык (`ru-RU`)**: русские голосовые подсказки, перевод 10 классов COCO и подавление повторов речи на 4 секунды.
- [x] **Тактильная виброиндикация смартфона**: двойной виброимпульс при срочных угрозах с монотонной защитой от дребезга (`1500 мс`).
- [x] **Интеграция с TalkBack и удержание экрана**: семантическая разметка всех элементов Compose, минимальные таргеты `48.dp` и `FLAG_KEEP_SCREEN_ON`.
- [ ] **Оценка глубины через ARCore Depth API**: уточнение дистанции до нависающих препятствий на смартфонах с поддержкой Depth API / ToF.
- [ ] **Пользовательский профиль настроек**: выбор темпа речи TTS и длительности тактильного отклика в интерфейсе приложения.

---

## Полезные ссылки

- [Архитектурные решения проекта Argus (ADR)](../docs/adr.md)
- [Спецификация распиновки и подключения трости](../docs/pinout.md)
- [Руководство по CameraX ImageAnalysis](https://developer.android.com/media/camera/camerax/analyze)
- [MediaPipe Tasks: Object Detection Guide](https://ai.google.dev/edge/mediapipe/solutions/vision/object_detector)
- [Документация Google ML Kit Image Labeling](https://developers.google.com/ml-kit/vision/image-labeling)
- [Руководство по доступности в Android Accessibility](https://developer.android.com/guide/topics/ui/accessibility)

---

## Лицензия

Исходный код мобильного приложения распространяется под свободной лицензией **MIT**. Подробный текст лицензии приведен в файле [LICENSE](../LICENSE) в корне репозитория.
