# 📱 Argus Android Application

> Автономный мобильный ассистент компьютерного зрения и тактильно-голосовой навигации для незрячих и слабовидящих пользователей на базе CameraX, MediaPipe Tasks и ML Kit.

[![Kotlin](https://img.shields.io/badge/Kotlin-2.2.10-7F52FF?style=flat&logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![Android](https://img.shields.io/badge/Android-8.0%2B%20(API%2026%2B)-3DDC84?style=flat&logo=android&logoColor=white)](https://developer.android.com/)
[![Compose](https://img.shields.io/badge/Jetpack%20Compose-BOM%202026.02.01-4285F4?style=flat&logo=jetpackcompose&logoColor=white)](https://developer.android.com/compose)
[![MediaPipe](https://img.shields.io/badge/MediaPipe-Tasks%20Vision%201.1.1-00796B?style=flat&logo=google&logoColor=white)](https://ai.google.dev/edge/mediapipe/solutions/vision/object_detector)
[![ML Kit](https://img.shields.io/badge/ML%20Kit-Image%20Labeling-FBBC04?style=flat&logo=google&logoColor=white)](https://developers.google.com/ml-kit)
[![License: MIT](https://img.shields.io/badge/License-MIT-green.svg?style=flat)](../LICENSE)

---

Android-приложение системы **Argus** в реальном времени анализирует видеопоток с камеры смартфона (`640x480`), распознает 23 курированных навигационных класса препятствий COCO (`ObstacleCatalog`, градация риска `dangerPriority` `1..3`) со стабилизацией кадров (`TemporalObjectTracker`) и опасные элементы окружения (лестницы, глухие стены, дверные проемы с защитой от ложных срабатываний на обивке мебели), объединяет их с данными сонара и гироскопа трости по одностороннему каналу Bluetooth SPP, озвучивает русские голосовые подсказки и дублирует срочные угрозы тактильным виброоткликом смартфона.

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

Текущая стадия: готовность к финальной защите (**версия 1.0, versionCode 1**, 94 модульных JVM-теста в 12 наборах + инструментальные тесты).

| Функция | Реализация | Состояние |
|:---|:---|:---:|
| **Видеопоток с камеры и защита памяти** | CameraX ImageAnalysis (`YUV_420_888`, `ResolutionSelector` `640x480`, ~5.5 FPS), защита от очереди кадров `isProcessing: AtomicBoolean`, безопасное закрытие `VisionAnalyzer` / `Bitmap` / `MPImage` / `ImageProxy` | Реализовано |
| **Детекция объектов (23 класса COCO) и трекинг** | MediaPipe Tasks Vision (`EfficientDet-Lite0` int8, порог `0.45`, `maxResults = 7`) + `ObstacleCatalog` (`dangerPriority` `1..3`) + `TemporalObjectTracker` | Реализовано, покрыто тестами |
| **Классификация окружения (сцены)** | Google ML Kit Image Labeling (порог `0.40`) + `HazardClassifier` (лестницы, стены, двери + подавление ложных ступеней на мебели) | Реализовано, покрыто тестами |
| **Приоритизация угроз и Sensor Fusion** | Единая 5-уровневая иерархия (`EvaluateHazardsUseCase` + `EvaluateSensorFusionUseCase` + `MainViewModel`) с понижением риска для `dangerPriority == 1` | Реализовано, покрыто тестами |
| **Озвучивание подсказок** | Android Text-to-Speech (`Locale.forLanguageTag("ru-RU")` с фолбэком) + потокобезопасная `SpeechCooldownPolicy` на `SystemClock::elapsedRealtime` (`4 с` / `2.5 с` / `1.5 с`) | Реализовано, покрыто тестами |
| **Прием телеметрии с трости по Bluetooth SPP** | Односторонний прием от `HC-05`/`HC-06`/`ARGUS` (`AtomicReference` + `connectionGeneration`, проверка `BLUETOOTH_SCAN`, демо-режим) | Реализовано, покрыто тестами |
| **Тактильный виброотклик смартфона** | `HapticFeedbackService` + `HapticCooldownGate` (`1500 мс` на `SystemClock::elapsedRealtime`) + двойной импульс `AndroidHapticFeedbackService` на срочных угрозах | Реализовано, покрыто тестами |
| **Доступность TalkBack и удержание экрана** | Семантика Compose (`semantics`, полные единицы в сантиметрах, `LiveRegionMode.Polite`, `heading()`, кнопки `>= 48.dp`, заголовки без эмодзи, скрытие декоративного `Canvas`) и `FLAG_KEEP_SCREEN_ON` | Реализовано, покрыто тестами |

---

## Возможности

- **Потокобезопасный и экономичный конвейер зрения**: ограничение буфера `ImageAnalysis` до `640x480` (`FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER`), пропуск кадров в полете (`isProcessing: AtomicBoolean`) и троттлинг `180 мс` (~5.5 FPS), синхронизация нативного детектора по `detectorLock` / `nativeClosed`, гарантированный однократный `imageProxy.close()` и `Bitmap.recycle()` в `try/finally` и сброс трекера при закрытии анализатора.
- **Курированный каталог 23 классов COCO (`ObstacleCatalog`)**: выделение только значимых для пешеходной навигации объектов с русской озвучкой (`ObstacleTranslator`) и тремя уровнями риска без бытового шума (посуды, еды, книг):
  - `dangerPriority = 3` (подвижные/крупные): `person` ("человек"), `car` ("машина"), `bus` ("автобус"), `truck` ("грузовик"), `motorcycle` ("мотоцикл"), `bicycle` ("велосипед");
  - `dangerPriority = 2` (физические преграды, животные, мебель, уличные столбики): `dog` ("собака"), `cat` ("кошка"), `chair` ("стул"), `couch` ("диван"), `bench` ("скамейка"), `dining table` ("стол"), `bed` ("кровать"), `potted plant` ("растение"), `suitcase` ("чемодан"), `backpack` ("рюкзак"), `fire hydrant` ("гидрант"), `stop sign` ("дорожный знак"), `traffic light` ("светофор"), `parking meter` ("столбик");
  - `dangerPriority = 1` (низкий риск - никогда не вызывают срочную тревогу и вибрацию смартфона): `handbag` ("сумка"), `umbrella` ("зонт"), `sports ball` ("мяч", `isCompact = true`).
- **Временная стабилизация детекций (`TemporalObjectTracker`)**: уверенные объекты (`confidence >= 0.55`) и любые близкие (`NEAR`) преграды подтверждаются мгновенно на 1-м кадре, а слабые детекции (`0.45..0.55`, не `NEAR`) требуют подтверждения на 2 кадрах в окне `<= 750 мс` по совпадению класса и IoU / центра рамки.
- **Разделение на пространственные зоны и адаптивная шкала площади**: классификация по секторам (`LEFT`, `CENTER`, `RIGHT`) и трем категориям дистанции (`NEAR >= 28%`, `MEDIUM 12%-28%`, `FAR 6%-12%`, `< 6%` игнор для стандартных объектов; `0.14 / 0.05 / 0.02` для компактного профиля `sports ball`).
- **Распознавание опасных элементов инфраструктуры**: классификатор лестниц (подтверждение на 2 кадрах подряд в окне `< 750 мс` с подавлением ложных ступеней при мебельном контексте `chair`/`couch`/`sofa`/`furniture`/`armrest` при `stairs < 0.72`), глухих стен и дверных проемов (`HazardClassifier`).
- **Sensor Fusion, голосовое сопровождение и виброотклик смартфона**: объединение ультразвукового сонара и крена трости со зрением камеры; срочные предупреждения (`isUrgent == true`: сонар `< 35 см`, лестница, боковой конфликт, стена, объект `NEAR` с `dangerPriority >= 2`) мгновенно прерывают текущую речь (`QUEUE_FLUSH`) и сопровождаются двойным тактильным импульсом смартфона (`0/120/80/180 мс`) с защитой от дребезга (`1500 мс`).
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
   - **Зеленый контур**: объект на правом фланге (зона `RIGHT`).

### Единая 5-уровневая таблица приоритетов, речи и виброотклика

Предупреждения оцениваются в каждом цикле обработки кадра и при поступлении критических пакетов телеметрии. Срабатывает **условие с наивысшим приоритетом**:

| Приоритет | Ситуация | Голосовое сообщение | Срочность (прерывание + вибро смартфона) |
|:---:|:---|:---|:---:|
| **1** | **Сонар трости `< 35 см` ИЛИ `StairsAhead`** | `"Внимание, близко препятствие: XX см!"` / `"Осторожно, впереди лестница!"` | **Да** (`QUEUE_FLUSH` + двойной виброимпульс) |
| **2** | **Боковой Sensor Fusion** (`LEFT`/`RIGHT` + объект в той же зоне) | `"Осторожно, слева преграда!"` / `"Осторожно, справа преграда!"` | **Да** (`QUEUE_FLUSH` + двойной виброимпульс) |
| **3** | **`WallProximity` ИЛИ близкий объект `NEAR` по центру (`dangerPriority >= 2`)** | `"Внимание, стена прямо по курсу!"` / `"Вплотную, <объект>!"` | **Да** (`QUEUE_FLUSH` + двойной виброимпульс) |
| **4** | **`DoorAhead`** | `"Впереди дверной проем"` | **Нет** (`QUEUE_ADD`, без вибро смартфона) |
| **5** | **Препятствие `MEDIUM` по центру (и `NEAR` с `dangerPriority == 1`) с анализом коридора** | `"Прямо <объект>, обойдите слева"` / `"Прямо <объект>, обойдите справа"` / `"Впереди <объект>"` | **Нет** (`QUEUE_ADD`, без вибро смартфона) |

> **Перевод объектов и подавление повторов:** 23 навигационных класса COCO переводятся на русский язык через `ObstacleTranslator` и `ObstacleCatalog` (`person` -> `"человек"`, `car` -> `"машина"`, `chair` -> `"стул"`, `dining table` -> `"стол"`, `potted plant` -> `"растение"`, `parking meter` -> `"столбик"` и др.). Очередь и антиспам озвучки в `AndroidVoiceAlertService` управляются доменной политикой `SpeechCooldownPolicy` на монотонных часах `SystemClock::elapsedRealtime` (`sameTextCooldownMs = 4000L`, `globalCooldownMs = 2500L`, `urgentSameTextCooldownMs = 1500L`), а частота срочной вибрации смартфона ограничена окном 1.5 секунды (`HapticCooldownGate(cooldownMs = 1500L)`).

---

## Как это работает

### Конвейер обработки кадров

```text
               [ Видеопоток камеры CameraX ]
          (ImageAnalysis 640x480, YUV_420_888)
                             │
                             ▼
                    ┌─────────────────┐
                    │  VisionAnalyzer │ -- 180 мс (~5.5 FPS) + isProcessing
                    └────────┬────────┘
                             │
            ┌────────────────┴────────────────┐
            ▼                                 ▼
┌───────────────────────────────┐ ┌───────────────────────────────┐
│     MediaPipe Tasks Vision    │ │     ML Kit Image Labeling     │
│   EfficientDet-Lite0 (int8)   │ │      Общий контекст сцены     │
│ 23 класса COCO, порог >= 0.45 │ │   (лестница, стена, дверь)    │
└───────────────┬───────────────┘ └───────────────┬───────────────┘
                │                                 │
                └────────────────┬────────────────┘
                                 │
                                 ▼
                   ┌───────────────────────────┐
                   │   VisionResultProcessor   │
                   ├───────────────────────────┤
                   │  • ObstacleCatalog (23)   │
                   │  • TemporalObjectTracker  │
                   │  • SpatialClassifier      │
                   │  • HazardClassifier       │
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
                         │          (SpeechCooldownPolicy) .vibrateUrgent()
                         ▼                   ▼               ▼
                 [ Compose UI ]     [ Android TTS ]   [ Двойной импульс ]
```

### Пространственные зоны и оценка дистанции

- **Горизонтальные зоны (`HorizontalZone`)**: вычисляются по горизонтальному центру рамки объекта (`centerX`):
  - `LEFT`: центр рамки левее 33% ширины кадра;
  - `CENTER`: центр рамки в диапазоне от 33% до 66% ширины кадра;
  - `RIGHT`: центр рамки правее 66% ширины кадра.
- **Категории дистанции (`DistanceCategory`)**: рассчитываются эвристически по доле площади кадра, занимаемой рамкой объекта (`SpatialClassifier`):
  - Стандартный профиль (`isCompact = false`): `NEAR >= 28%`, `MEDIUM 12%-28%`, `FAR 6%-12%` (без озвучки), `< 6%` игнорируется;
  - Компактный профиль (`isCompact = true`, `sports ball`): `NEAR >= 14%`, `MEDIUM 5%-14%`, `FAR 2%-5%`, `< 2%` игнорируется;
  - Отфильтрованные объекты сортируются по `(dangerPriority desc, distanceCategory asc, confidence desc)`, чтобы пешеход или автомобиль всегда обрабатывались приоритетнее мебели.

### Классификация опасностей и дедупликация речи

1. **Лестницы и защита от мебельной обивки**: метки `stair`, `step`, `tread` (`>= 0.65`) подтверждаются только при появлении минимум на двух кадрах подряд в окне `< 750 мс`. При наличии мебельного контекста (`armrest >= 0.40` или `chair`/`couch`/`sofa`/`furniture >= 0.55` при `stairs < 0.72`) ложная лестница подавляется без генерации отдельного речевого шума о подлокотниках.
2. **Глухие стены и дверные проемы**: метка `wall` (`>= 0.65`) срабатывает только при отсутствии физических объектов переднего плана (`!hasPhysicalObjects`); метка `door` (`>= 0.55`) формирует подсказку `DoorAhead`.
3. **Временная стабилизация рамок (`TemporalObjectTracker`)**: уверенные (`>= 0.55`) и близкие (`NEAR`) объекты проходят с нулевой задержкой на 1-м кадре, а слабые (`0.45..0.55` и не `NEAR`) требуют подтверждения на 2 кадрах в окне `<= 750 мс` (с защитой от отката часов и ограничением до 32 активных треков).
4. **Разделение ролей TTS и TalkBack**: живые голосовые предупреждения ведет `AndroidVoiceAlertService` через `SpeechCooldownPolicy`, заголовки `strings.xml` очищены от эмодзи, а Compose-карточки размечены семантикой для навигации жестами TalkBack без перебивания речи.

---

## Архитектура

Проект построен по принципам **Clean Architecture** и **UDF (Unidirectional Data Flow)** (подробные обоснования см. в [`docs/adr.md`](../docs/adr.md)):
- **UI-слой** подписывается на неизменяемый поток `StateFlow<MainUiState>`, управляет `FLAG_KEEP_SCREEN_ON` и семантикой доступности.
- **Domain-слой** не содержит импортов `android.*` и `androidx.*` (контролируется в CI скриптом `check_repo_standards.py`) и оперирует чистыми контрактами (`ObstacleCatalog`, `SpeechOutput`, `SpeechCooldownPolicy`, `HapticFeedbackService`, `HapticCooldownGate`, `CaneBluetoothClient`, `AlertStrategy`).
- **Data-слой** инкапсулирует MediaPipe / ML Kit, `TemporalObjectTracker`, потокобезопасный односторонний клиент Bluetooth SPP, системный `TextToSpeech` и `Vibrator` / `VibratorManager`.

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
│       │   │   │   ├── tts/               # AndroidVoiceAlertService (ru-RU TTS + SpeechCooldownPolicy)
│       │   │   │   └── vision/            # VisionAnalyzer, VisionResultProcessor, TemporalObjectTracker,
│       │   │   │                          # HazardClassifier, SpatialClassifier, LabelFormatter
│       │   │   ├── domain/
│       │   │   │   ├── bluetooth/         # CaneBluetoothClient, BluetoothConnectionStatus
│       │   │   │   ├── haptic/            # HapticFeedbackService, NoOpHapticFeedbackService, HapticCooldownGate
│       │   │   │   ├── model/             # CaneTelemetry, ObstacleCatalog, ObstacleTranslator, VisionResult
│       │   │   │   ├── tts/               # SpeechOutput, VoiceAlertService, SpeechLifecycle, SpeechCooldownPolicy
│       │   │   │   └── usecase/           # EvaluateHazardsUseCase, EvaluateSensorFusionUseCase, AlertStrategy
│       │   │   └── ui/
│       │   │       ├── camera/            # CameraPreviewView (640x480) + resolveCaneDistanceDisplay + TalkBack
│       │   │       ├── main/              # MainScreen, MainViewModel, MainUiState
│       │   │       │   └── components/    # HazardBadge, CameraPermissionView
│       │   │       └── theme/             # Color, Theme, Type (Material 3)
│       │   ├── keepRules/
│       │   │   └── rules.keep             # R8/ProGuard keep-правила для MediaPipe и protobuf-lite
│       │   └── res/                       # Векторные иконки, строковые ресурсы, цвета, темы
│       ├── test/                          # 12 наборов модульных JVM-тестов (94 теста, JUnit 4 + Coroutines Test)
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
| `android.permission.ACCESS_FINE_LOCATION` | Системное требование стека Bluetooth Classic на Android 8.0-11 (`maxSdkVersion=30`) | **Да** (только для API 26-30) |

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

### Состав тестового набора (94 модульных JVM-теста в 12 наборах + инструментальные)

| Тестовый класс | Тип | Кол-во | Что верифицируется |
|:---|:---:|:---:|:---|
| **`EvaluateHazardsUseCaseTest`** | Unit (JVM) | 16 | Иерархия приоритетов оповещений, фильтр лестниц (2 кадра подряд в 750 мс), понижение `dangerPriority == 1` до несрочного уровня 5, приоритет `person` выше `chair`. |
| **`VisionResultProcessorTest`** | Unit (JVM) | 16 | Курированный список 23 классов COCO, пороги `0.06/0.12/0.28` и компактные `0.02/0.05/0.14`, сортировка `(dangerPriority desc, distance asc, confidence desc)`, мебельное подавление лестниц и `TemporalObjectTracker`. |
| **`MainViewModelTest`** | Unit (JVM) | 15 | Разрешения камеры и Bluetooth, арбитраж 5 уровней приоритетов, отсутствие `vibrateUrgent()` на несрочных и `dangerPriority == 1` объектах, обновление `MainUiState`. |
| **`ObstacleCatalogTest`** | Unit (JVM) | 8 | Точный состав 23 навигационных классов COCO, отсечение бытового шума и `"door"`, градация `dangerPriority` `1..3`, профиль `isCompact` и русские переводы с алиасами. |
| **`SpeechCooldownPolicyTest`** | Unit (JVM) | 8 | Потокобезопасные кулдауны TTS (`4000 мс` повтор, `2500 мс` общий, `1500 мс` дубли `urgent`), мгновенный прорыв новой срочной угрозы, откат часов и `reset()`. |
| **`TemporalObjectTrackerTest`** | Unit (JVM) | 8 | Мгновенный пропуск при `>= 0.55` или `NEAR`, подтверждение `0.45..0.55` на 2 кадрах в `<= 750 мс`, откат часов, ограничение памяти треков и `reset()`. |
| **`EvaluateSensorFusionUseCaseTest`** | Unit (JVM) | 6 | Приоритет 1 сонара (`< 35 см`), приоритет 2 бокового Sensor Fusion (`LEFT`/`RIGHT`), отсечение устаревшей телеметрии (`>= 1000 мс`). |
| **`CaneTelemetryTest`** | Unit (JVM) | 5 | Парсинг протокола `DIST:<см>,DIR:<курс>`, порог `isCriticalDistance` (`1..34 см`), обработка свободного пути `DIST:-1` (`isCriticalDistance == false`). |
| **`CaneDistanceDisplayTest`** | Unit (JVM) | 5 | Граничные состояния карточки дистанции (`null`, `-1`, `0`, `1`, `34`, `35`, `50`, `51`, `120`), метка `"> 200 СМ"` и полные строки `contentDescription` для TalkBack. |
| **`HapticCooldownGateTest`** | Unit (JVM) | 4 | Первое срабатывание (включая `t = 0`), подавление повторов внутри окна `1500 мс`, восстановление при откате часов и `NoOpHapticFeedbackService`. |
| **`BluetoothCaneClientTest`** | Unit (JVM) | 2 | Регистронезависимый отбор сопряженных устройств (`HC-05`, `HC-06`, `ARGUS`) и отсечение посторонней гарнитуры. |
| **`ExampleUnitTest`** | Unit (JVM) | 1 | Базовая проверка окружения JUnit 4. |
| **`VisionIntegrationTest`** | Instrumented | 4 | Инициализация `AndroidVoiceAlertService` и `VisionAnalyzer` с реальным `Context`, сквозная связка COCO + `DoorAhead`, компактный `sports ball` и подавление ложных ступеней на мебели. |
| **`ExampleInstrumentedTest`** | Instrumented | 1 | Проверка `applicationContext` пакета `com.project.argus`. |

---

## Планы развития

- [x] **Интеграция Bluetooth SPP клиента**: потокобезопасный односторонний прием телеметрии трости (`DIST:<см>,DIR:<CENTER|LEFT|RIGHT>`) и локальный демо-режим.
- [x] **Локализация на русский язык (`ru-RU`) и каталог 23 классов COCO**: русские голосовые подсказки, градация угроз `ObstacleCatalog`, `TemporalObjectTracker` и монотонные кулдауны `SpeechCooldownPolicy`.
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
