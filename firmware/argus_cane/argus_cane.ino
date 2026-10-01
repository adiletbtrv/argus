// ASSUMPTION: MPU6050 I2C default address is 0x68 (AD0 connected to GND).
// ASSUMPTION: Cane lateral roll tilt corresponds to mpu.getAngleX().
// ASSUMPTION: Including direction_logic.cpp under Arduino IDE resolves linker symbol dependencies cleanly.
// ASSUMPTION: PIN_C (D4) is kept permanently LOW, reserving upper addresses (Y4-Y7) for future firmware expansion.

#include <Wire.h>
#include <MPU6050_light.h>
#include "../direction_logic/direction_logic.h"

#if defined(ARDUINO) && !defined(ARGUS_SEPARATE_COMPILATION)
#include "../direction_logic/direction_logic.cpp"
#endif

// Назначение цифровых пинов Arduino Nano для управления аппаратным дешифратором 74LS138
const int PIN_A = 2; // Адресный вход A (бит 0)
const int PIN_B = 3; // Адресный вход B (бит 1)
const int PIN_C = 4; // Адресный вход C (бит 2, жестко LOW — резерв на будущее расширение до 8 состояний)

MPU6050 mpu(Wire);
DirectionClassifier classifier;
Direction lastReportedDir = Direction::CENTER;

void applyDirectionOutputs(Direction dir) {
  switch (dir) {
    case Direction::CENTER:
      // Код 00 (CBA = 000) -> активен выход Y0 (нет подключенного светодиода)
      digitalWrite(PIN_A, LOW);
      digitalWrite(PIN_B, LOW);
      break;

    case Direction::LEFT:
      // Код 01 (CBA = 001) -> активен выход Y1 (включает левый светодиод через подтяжку)
      digitalWrite(PIN_A, HIGH);
      digitalWrite(PIN_B, LOW);
      break;

    case Direction::RIGHT:
      // Код 10 (CBA = 010) -> активен выход Y2 (включает правый светодиод через подтяжку)
      digitalWrite(PIN_A, LOW);
      digitalWrite(PIN_B, HIGH);
      break;
  }
}

void setup() {
  Serial.begin(9600);
  Wire.begin();

  // Инициализация пинов управления дешифратором 74LS138
  pinMode(PIN_A, OUTPUT);
  pinMode(PIN_B, OUTPUT);
  pinMode(PIN_C, OUTPUT);

  // Исходное состояние: C жестко LOW, A и B в LOW (направление CENTER)
  digitalWrite(PIN_A, LOW);
  digitalWrite(PIN_B, LOW);
  digitalWrite(PIN_C, LOW); // Резервный пин старшего бита, удерживается LOW

  Serial.println(F("========================================"));
  Serial.println(F(" Argus Smart Cane - Firmware Initializing"));
  Serial.println(F("========================================"));

  byte status = mpu.begin();
  if (status != 0) {
    Serial.print(F("[ERROR] MPU6050 initialization failed, code: "));
    Serial.println(status);
    Serial.println(F("[ERROR] Halting. Check I2C SDA/SCL wiring."));
    while (true) {
      delay(1000);
    }
  }

  Serial.println(F("[INFO] Calibrating MPU6050 gyro/accel offsets..."));
  // ВАЖНО: Трость должна быть неподвижна и строго вертикальна во время калибровки!
  mpu.calcOffsets();
  Serial.println(F("[INFO] Calibration complete. Turn signals ready."));
}

void loop() {
  mpu.update();
  float roll = mpu.getAngleX();
  Direction result = classifier.classify(roll, millis());

  // При изменении классифицированного направления обновляем пины дешифратора и лог
  if (result != lastReportedDir) {
    lastReportedDir = result;
    applyDirectionOutputs(result);

    Serial.print(F("[DEBUG] New Direction: "));
    switch (result) {
      case Direction::CENTER:
        Serial.print(F("CENTER (Straight)"));
        break;
      case Direction::LEFT:
        Serial.print(F("LEFT"));
        break;
      case Direction::RIGHT:
        Serial.print(F("RIGHT"));
        break;
    }
    Serial.print(F(" | Roll: "));
    Serial.print(roll, 2);
    Serial.print(F(" deg | Pins (CBA): 0"));
    Serial.print(digitalRead(PIN_B));
    Serial.println(digitalRead(PIN_A));
  }

  delay(20);
}
