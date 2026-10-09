// Основная прошивка умной трости Argus.
// Датчик положения MPU6050 (0x68), дешифратор 74HC138,
// ультразвуковой датчик расстояния RCWL-9610A (HC-SR04),
// виброотклик (PWM D9) и односторонняя Bluetooth-телеметрия HC-05 (TX D11).

#include <Wire.h>
#include <SoftwareSerial.h>
#include <MPU6050_light.h>
#include "../direction_logic/direction_logic.h"

#if defined(ARDUINO) && !defined(ARGUS_SEPARATE_COMPILATION)
#include "../direction_logic/direction_logic.cpp"
#endif

// Пины управления КМОП-дешифратором 74HC138
const int PIN_A = 2; // Адресный вход A (бит 0)
const int PIN_B = 3; // Адресный вход B (бит 1)
const int PIN_C = 4; // Адресный вход C (бит 2, зафиксирован в LOW для Y0..Y3)

// Пины ультразвукового датчика расстояния RCWL-9610A (HC-SR04)
const int PIN_TRIG = 5; // Сигнал запуска измерения (Trig)
const int PIN_ECHO = 6; // Вход эхо-импульса (Echo)

// Управление вибромоторчиком (ШИМ через NPN-транзистор)
const int PIN_VIBRO = 9;

// Пины модуля Bluetooth HC-05 (SoftwareSerial)
// Аппаратные пины D0/D1 сохранены для USB-отладки (Serial Monitor)
const int BT_RX_PIN = 10; // Не используется для приема (односторонняя телеметрия)
const int BT_TX_PIN = 11; // TX Arduino к RX HC-05 через делитель напряжения (1к / 2к)

// Параметры обнаружения препятствий и виброотклика
const long OBSTACLE_THRESHOLD_CM = 50; // Порог дистанции для тактильного сигнала (см)
const int VIBRO_INTENSITY = 255;       // Интенсивность вибрации

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

  unsigned long duration = pulseIn(PIN_ECHO, HIGH, 25000); // таймаут ~4.3 м
  if (duration == 0) {
    return -1; // Препятствие вне зоны обнаружения
  }
  return duration / 58; // Дистанция в см
}

const char* getDirectionString(Direction dir) {
  switch (dir) {
    case Direction::CENTER: return "CENTER";
    case Direction::LEFT:   return "LEFT";
    case Direction::RIGHT:  return "RIGHT";
    default:                return "CENTER";
  }
}

// Трость не принимает команды извне — вибрация и LED управляются только локальными датчиками, Bluetooth используется исключительно для исходящей телеметрии.
void sendTelemetry(long distCm, Direction dir) {
  btSerial.print(F("DIST:"));
  btSerial.print(distCm);
  btSerial.print(F(",DIR:"));
  btSerial.println(getDirectionString(dir));
}

void applyDirectionOutputs(Direction dir) {
  switch (dir) {
    case Direction::CENTER:
      // Код 00 (CBA = 000) -> активен выход Y0 (светодиоды выключены)
      digitalWrite(PIN_A, LOW);
      digitalWrite(PIN_B, LOW);
      break;

    case Direction::LEFT:
      // Код 01 (CBA = 001) -> активен выход Y1 (включает левый светодиод)
      digitalWrite(PIN_A, HIGH);
      digitalWrite(PIN_B, LOW);
      break;

    case Direction::RIGHT:
      // Код 10 (CBA = 010) -> активен выход Y2 (включает правый светодиод)
      digitalWrite(PIN_A, LOW);
      digitalWrite(PIN_B, HIGH);
      break;
  }
}

void setup() {
  Serial.begin(9600);
  btSerial.begin(9600);
  Wire.begin();

  // Настройка выходов дешифратора 74HC138
  pinMode(PIN_A, OUTPUT);
  pinMode(PIN_B, OUTPUT);
  pinMode(PIN_C, OUTPUT);

  digitalWrite(PIN_A, LOW);
  digitalWrite(PIN_B, LOW);
  digitalWrite(PIN_C, LOW);

  // Настройка ультразвукового датчика расстояния
  pinMode(PIN_TRIG, OUTPUT);
  pinMode(PIN_ECHO, INPUT);
  digitalWrite(PIN_TRIG, LOW);

  // Настройка вывода вибромоторчика
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

  // При калибровке трость должна стоять вертикально и неподвижно
  Serial.println(F("Калибровка гироскопа и акселерометра MPU6050..."));
  mpu.calcOffsets();
  Serial.println(F("Готово к работе."));
}

void loop() {
  // Опрос датчика положения и классификация направления поворота
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

  // Опрос локального датчика расстояния RCWL-9610A и управление вибромотором (прототип P0002)
  unsigned long now = millis();
  if (now - lastDistanceCheckMs >= DISTANCE_CHECK_INTERVAL_MS) {
    lastDistanceCheckMs = now;
    long dist = readDistanceCm();

    if (dist > 0 && dist <= OBSTACLE_THRESHOLD_CM) {
      // Препятствие обнаружено ближе порогового расстояния:
      // 1. Активация вибромотора через транзистор (локальный тактильный контур)
      analogWrite(PIN_VIBRO, VIBRO_INTENSITY);

      // 2. Исходящая односторонняя телеметрия на смартфон
      sendTelemetry(dist, currentDir);
    } else {
      // Препятствие отсутствует или дальше порога
      analogWrite(PIN_VIBRO, 0);
    }
  }

  delay(20);
}
