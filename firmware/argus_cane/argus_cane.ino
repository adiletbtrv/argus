// основная прошивка умной трости Argus
// датчик положения MPU6050 (0x68), дешифратор 74HC138
// ультразвуковой датчик расстояния RCWL-9610A (HC-SR04)
// виброотклик (PWM D9) и односторонняя Bluetooth телеметрия HC-05 (TX D11)

// Режим симуляции в Wokwi (прямое управление светодиодами на D7, D8, D12 без 74HC138).
// Для запуска в Wokwi раскомментируйте макрос ниже или задайте флаг при компиляции:
// #define WOKWI_SIMULATION

#include <Wire.h>
#include <SoftwareSerial.h>
#include <MPU6050_light.h>

#if defined(__has_include)
  #if __has_include("direction_logic.h")
    #include "direction_logic.h"
  #elif __has_include("../direction_logic/direction_logic.h")
    #include "../direction_logic/direction_logic.h"
  #else
    #include "direction_logic.h"
  #endif
#else
  #if defined(WOKWI_SIMULATION)
    #include "direction_logic.h"
  #else
    #include "../direction_logic/direction_logic.h"
  #endif
#endif

#if defined(ARDUINO) && !defined(ARGUS_SEPARATE_COMPILATION)
  #if defined(__has_include)
    #if __has_include("direction_logic.cpp")
      #include "direction_logic.cpp"
    #elif __has_include("../direction_logic/direction_logic.cpp")
      #include "../direction_logic/direction_logic.cpp"
    #else
      #include "direction_logic.cpp"
    #endif
  #else
    #if defined(WOKWI_SIMULATION)
      #include "direction_logic.cpp"
    #else
      #include "../direction_logic/direction_logic.cpp"
    #endif
  #endif
#endif

#if defined(WOKWI_SIMULATION)
// прямое подключение светодиодов в симуляторе Wokwi
const int PIN_LED_LEFT = 7;     // левый поворотник (зеленый LED)
const int PIN_LED_CENTER = 8;   // центральное положение (желтый LED)
const int PIN_LED_RIGHT = 12;   // правый поворотник (синий LED)
#else
// пины управления 74HC138
const int PIN_A = 2; // адресный вход A (бит 0)
const int PIN_B = 3; // адресный вход B (бит 1)
const int PIN_C = 4; // адресный вход C (бит 2, зафиксирован в LOW для Y0..Y3)
#endif

// пины ультразвукового датчика расстояния RCWL-9610A (HC-SR04)
const int PIN_TRIG = 5; // сигнал запуска измерения (Trig)
const int PIN_ECHO = 6; // вход эхо-импульса (Echo)

// управление вибромоторчиком (ШИМ через NPN транзистор)
const int PIN_VIBRO = 9;

// пины модуля Bluetooth HC-05 (SoftwareSerial)
// аппаратные пины D0/D1 сохранены для USB отладки (Serial Monitor)
const int BT_RX_PIN = 10; // не используется для приема (односторонняя телеметрия)
const int BT_TX_PIN = 11; // TX Arduino к RX HC-05 через делитель напряжения (1к / 2к)

// параметры обнаружения препятствий и виброотклика
const long OBSTACLE_THRESHOLD_CM = 50; // порог дистанции для тактильного сигнала (см)
const int VIBRO_INTENSITY = 255;       // интенсивность вибрации

SoftwareSerial btSerial(BT_RX_PIN, BT_TX_PIN);
MPU6050 mpu(Wire);
DirectionClassifier classifier;
Direction lastReportedDir = Direction::CENTER;

unsigned long lastDistanceCheckMs = 0;
const unsigned long DISTANCE_CHECK_INTERVAL_MS = 60;

long readDistanceCm() {
  digitalWrite(PIN_TRIG, LOW);
  delayMicroseconds(2);
  digitalWrite(PIN_TRIG, HIGH);
  delayMicroseconds(10);
  digitalWrite(PIN_TRIG, LOW);

  unsigned long duration = pulseIn(PIN_ECHO, HIGH, 25000); // таймаут примерно 4.3 м
  if (duration == 0) {
    return -1; // препятствие вне зоны обнаружения
  }
  return duration / 58; // дистанция в см
}

const char* getDirectionString(Direction dir) {
  switch (dir) {
    case Direction::CENTER: return "CENTER";
    case Direction::LEFT:   return "LEFT";
    case Direction::RIGHT:  return "RIGHT";
    default:                return "CENTER";
  }
}

// трость не принимает команды извне: вибрация и LED управляются только локальными датчиками, Bluetooth используется только для исходящей телеметрии
void sendTelemetry(long distCm, Direction dir) {
  btSerial.print(F("DIST:"));
  btSerial.print(distCm);
  btSerial.print(F(",DIR:"));
  btSerial.println(getDirectionString(dir));
}

void applyDirectionOutputs(Direction dir) {
#if defined(WOKWI_SIMULATION)
  switch (dir) {
    case Direction::CENTER:
      digitalWrite(PIN_LED_LEFT, LOW);
      digitalWrite(PIN_LED_CENTER, HIGH);
      digitalWrite(PIN_LED_RIGHT, LOW);
      break;

    case Direction::LEFT:
      digitalWrite(PIN_LED_LEFT, HIGH);
      digitalWrite(PIN_LED_CENTER, LOW);
      digitalWrite(PIN_LED_RIGHT, LOW);
      break;

    case Direction::RIGHT:
      digitalWrite(PIN_LED_LEFT, LOW);
      digitalWrite(PIN_LED_CENTER, LOW);
      digitalWrite(PIN_LED_RIGHT, HIGH);
      break;
  }
#else
  switch (dir) {
    case Direction::CENTER:
      // код 00 (CBA = 000) -> активен выход Y0 (светодиоды выключены)
      digitalWrite(PIN_A, LOW);
      digitalWrite(PIN_B, LOW);
      break;

    case Direction::LEFT:
      // код 01 (CBA = 001) -> активен выход Y1 (включает левый светодиод)
      digitalWrite(PIN_A, HIGH);
      digitalWrite(PIN_B, LOW);
      break;

    case Direction::RIGHT:
      // код 10 (CBA = 010) -> активен выход Y2 (включает правый светодиод)
      digitalWrite(PIN_A, LOW);
      digitalWrite(PIN_B, HIGH);
      break;
  }
#endif
}

void setup() {
  Serial.begin(9600);
  btSerial.begin(9600);
  btSerial.stopListening(); // отключаем прерывания RX: трость только передает, входящие байты игнорируются
  Wire.begin();

#if defined(WOKWI_SIMULATION)
  // настройка прямых выходов светодиодов в Wokwi
  pinMode(PIN_LED_LEFT, OUTPUT);
  pinMode(PIN_LED_CENTER, OUTPUT);
  pinMode(PIN_LED_RIGHT, OUTPUT);

  digitalWrite(PIN_LED_LEFT, LOW);
  digitalWrite(PIN_LED_CENTER, HIGH); // по умолчанию трость в нейтрали
  digitalWrite(PIN_LED_RIGHT, LOW);
#else
  // настройка выходов 74HC138
  pinMode(PIN_A, OUTPUT);
  pinMode(PIN_B, OUTPUT);
  pinMode(PIN_C, OUTPUT);

  digitalWrite(PIN_A, LOW);
  digitalWrite(PIN_B, LOW);
  digitalWrite(PIN_C, LOW);
#endif

  // настройка ультразвукового датчика расстояния
  pinMode(PIN_TRIG, OUTPUT);
  pinMode(PIN_ECHO, INPUT);
  digitalWrite(PIN_TRIG, LOW);

  // настройка вывода вибромоторчика
  pinMode(PIN_VIBRO, OUTPUT);
  analogWrite(PIN_VIBRO, 0);

  Serial.println(F("Argus Smart Cane: инициализация..."));

  byte status = mpu.begin();
  if (status != 0) {
    Serial.print(F("[ERROR] MPU6050 ошибка: "));
    Serial.println(status);
    while (true) {
      delay(1000);
    }
  }

  // при калибровке трость должна стоять вертикально и неподвижно
  Serial.println(F("Калибровка гироскопа и акселерометра MPU6050..."));
  mpu.calcOffsets();
  Serial.println(F("Готово к работе."));
}

void loop() {
  // опрос датчика положения и классификация направления поворота
  mpu.update();
  float roll = mpu.getAngleX();
  Direction currentDir = classifier.classify(roll, millis());

  if (currentDir != lastReportedDir) {
    lastReportedDir = currentDir;
    applyDirectionOutputs(currentDir);

    Serial.print(F("[DIR] "));
    Serial.print(getDirectionString(currentDir));
    Serial.print(F(" | Крен: "));
    Serial.print(roll, 2);
    Serial.println(F(" deg"));
  }

  // опрос RCWL-9610A и управление вибромотором (прототип P0002)
  unsigned long now = millis();
  if (now - lastDistanceCheckMs >= DISTANCE_CHECK_INTERVAL_MS) {
    lastDistanceCheckMs = now;
    long dist = readDistanceCm();

    if (dist > 0 && dist <= OBSTACLE_THRESHOLD_CM) {
      // препятствие обнаружено ближе порогового расстояния:
      // 1. активация вибромотора через транзистор
      analogWrite(PIN_VIBRO, VIBRO_INTENSITY);

      // 2. исходящая односторонняя телеметрия на смартфон
      sendTelemetry(dist, currentDir);
    } else {
      // препятствие отсутствует или дальше порога
      analogWrite(PIN_VIBRO, 0);
    }
  }

  delay(20);
}
