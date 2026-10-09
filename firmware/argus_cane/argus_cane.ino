// Основная прошивка умной трости Argus.
// Датчик положения MPU6050 (0x68), дешифратор 74HC138,
// Bluetooth HC-05 (SoftwareSerial D10/D11) и виброотклик (PWM D9).

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

// Управление вибромоторчиком (ШИМ через NPN-транзистор)
const int PIN_VIBRO = 9;

// Пины модуля Bluetooth HC-05
const int BT_RX_PIN = 10; // RX Arduino (подключается к TX HC-05)
const int BT_TX_PIN = 11; // TX Arduino (к RX HC-05 через делитель напряжения)

SoftwareSerial btSerial(BT_RX_PIN, BT_TX_PIN);
MPU6050 mpu(Wire);
DirectionClassifier classifier;
Direction lastReportedDir = Direction::CENTER;

// Переменные для неблокирующего импульса виброотклика
bool vibroActive = false;
unsigned long vibroEndTime = 0;
const unsigned long VIBRO_PULSE_MS = 250;
const int VIBRO_INTENSITY = 255;

void triggerVibration(unsigned long durationMs = VIBRO_PULSE_MS, int intensity = VIBRO_INTENSITY) {
  analogWrite(PIN_VIBRO, intensity);
  vibroEndTime = millis() + durationMs;
  vibroActive = true;
}

void updateVibrationState() {
  if (vibroActive && millis() >= vibroEndTime) {
    analogWrite(PIN_VIBRO, 0);
    vibroActive = false;
  }
}

void processBluetoothCommands() {
  static String inputBuffer = "";

  while (btSerial.available() > 0) {
    char c = (char)btSerial.read();
    if (c == '\n' || c == '\r') {
      inputBuffer.trim();
      if (inputBuffer.length() > 0) {
        if (inputBuffer == "VIBRATE") {
          triggerVibration();
          Serial.println(F("[BT] Command: VIBRATE -> pulse triggered"));
        } else {
          Serial.print(F("[BT] Unknown command: "));
          Serial.println(inputBuffer);
        }
        inputBuffer = "";
      }
    } else {
      if (inputBuffer.length() < 32) {
        inputBuffer += c;
      }
      // Обработка команды без переноса строки
      if (inputBuffer.endsWith("VIBRATE")) {
        triggerVibration();
        Serial.println(F("[BT] Command: VIBRATE -> pulse triggered"));
        inputBuffer = "";
      }
    }
  }
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
  // Обработка команд от мобильного приложения по Bluetooth
  processBluetoothCommands();

  // Обновление состояния тактильного импульса
  updateVibrationState();

  // Опрос датчика и классификация поворота
  mpu.update();
  float roll = mpu.getAngleX();
  Direction result = classifier.classify(roll, millis());

  if (result != lastReportedDir) {
    lastReportedDir = result;
    applyDirectionOutputs(result);

    Serial.print(F("[DIR] "));
    switch (result) {
      case Direction::CENTER:
        Serial.print(F("CENTER"));
        break;
      case Direction::LEFT:
        Serial.print(F("LEFT"));
        break;
      case Direction::RIGHT:
        Serial.print(F("RIGHT"));
        break;
    }
    Serial.print(F(" | Крен: "));
    Serial.print(roll, 2);
    Serial.println(F(" deg"));
  }

  delay(20);
}
