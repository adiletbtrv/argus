// wokwi стенд
// использует direction_logic из основной прошивки
// но заменяет MPU6050 потенциометром, дешифратор 74HC138 - тремя светодиодами
// а вибромотор - пьезодинамиком, bluetooth не эмулируется - телеметрия идет в
// Serial

#include "direction_logic.h"

// пины стенда (совпадают с diagram.json)
const int BUTTON_PIN = 2;   // кнопка (D2), будущая калибровка по issue #11
const int BUILTIN_LED = 13; // встроенный светодиод Nano

const int TRIG_PIN = 4; // HC-SR04 TRIG
const int ECHO_PIN = 5; // HC-SR04 ECHO
const int TILT_POT_PIN =
    A0; // потенциометр: имитация крена (-45 ... +45 градусов)

const int LED_LEFT = 8;    // зеленый LED (поворот влево)
const int LED_CENTER = 9;  // желтый LED (курс прямо)
const int LED_RIGHT = 10;  // синий LED (поворот вправо)
const int BUZZER_PIN = 11; // пьезодинамик (эмуляция вибромотора)

DirectionClassifier classifier;

long readDistanceCm() {
  digitalWrite(TRIG_PIN, LOW);
  delayMicroseconds(2);
  digitalWrite(TRIG_PIN, HIGH);
  delayMicroseconds(10);
  digitalWrite(TRIG_PIN, LOW);
  long duration = pulseIn(ECHO_PIN, HIGH, 30000);
  if (duration == 0)
    return 400;
  return duration * 0.034 / 2;
}

void setup() {
  Serial.begin(115200);

  pinMode(BUTTON_PIN, INPUT_PULLUP);
  pinMode(BUILTIN_LED, OUTPUT);

  pinMode(TRIG_PIN, OUTPUT);
  pinMode(ECHO_PIN, INPUT);

  pinMode(LED_LEFT, OUTPUT);
  pinMode(LED_CENTER, OUTPUT);
  pinMode(LED_RIGHT, OUTPUT);
  pinMode(BUZZER_PIN, OUTPUT);

  Serial.println("=== ARGUS CANE SIMULATOR STARTED ===");
  Serial.println("Крути ручку потенциометра, чтобы наклонять трость!");
}

void loop() {
  // 1. угол крена из потенциометра (0..1023 -> -45..+45 градусов)
  int rawPot = analogRead(TILT_POT_PIN);
  float rollDeg = map(rawPot, 0, 1023, -450, 450) / 10.0f;

  // 2. классификация направления (direction_logic из основной прошивки)
  Direction dir = classifier.classify(rollDeg, millis());

  // 3. индикация направления
  digitalWrite(LED_LEFT, (dir == Direction::LEFT) ? HIGH : LOW);
  digitalWrite(LED_CENTER, (dir == Direction::CENTER) ? HIGH : LOW);
  digitalWrite(LED_RIGHT, (dir == Direction::RIGHT) ? HIGH : LOW);

  // 4. обнаружение препятствий
  long distCm = readDistanceCm();

  if (distCm < 50) {
    tone(BUZZER_PIN, 1000);
  } else {
    noTone(BUZZER_PIN);
  }

  // 5. кнопка на D2 (зажигает встроенный LED при нажатии)
  if (digitalRead(BUTTON_PIN) == LOW) {
    digitalWrite(BUILTIN_LED, HIGH);
  } else {
    digitalWrite(BUILTIN_LED, LOW);
  }

  // 6. телеметрия в Serial Monitor
  static unsigned long lastPrint = 0;
  if (millis() - lastPrint >= 150) {
    lastPrint = millis();
    Serial.print("TILT:");
    Serial.print(rollDeg, 1);
    Serial.print(",DIR:");
    if (dir == Direction::LEFT)
      Serial.print("LEFT");
    else if (dir == Direction::RIGHT)
      Serial.print("RIGHT");
    else
      Serial.print("CENTER");
    Serial.print(",DIST:");
    Serial.print(distCm);
    Serial.println(" cm");
  }

  delay(20);
}
