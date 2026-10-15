// Прототип P0005: Эмуляция угла крена + Алгоритм гистерезиса направления
// Проверка математической модели фильтрации шума шагов и гистерезиса (25°/17°, 250 мс)
// Спецификация: firmware/direction_logic/direction_logic.h

#include "direction_logic.h"

#if defined(ARDUINO) && !defined(ARGUS_SEPARATE_COMPILATION)
#include "direction_logic.cpp"
#endif

const int POT_PIN = A0;
const int LED_LEFT = 8;
const int LED_CENTER = 9;
const int LED_RIGHT = 10;

DirectionClassifier classifier;
Direction lastDir = Direction::CENTER;

void updateLeds(Direction dir) {
  digitalWrite(LED_LEFT,   (dir == Direction::LEFT)   ? HIGH : LOW);
  digitalWrite(LED_CENTER, (dir == Direction::CENTER) ? HIGH : LOW);
  digitalWrite(LED_RIGHT,  (dir == Direction::RIGHT)  ? HIGH : LOW);
}

const char* getDirString(Direction dir) {
  switch (dir) {
    case Direction::CENTER: return "CENTER";
    case Direction::LEFT:   return "LEFT";
    case Direction::RIGHT:  return "RIGHT";
    default:                return "UNKNOWN";
  }
}

void setup() {
  Serial.begin(115200);

  pinMode(LED_LEFT, OUTPUT);
  pinMode(LED_CENTER, OUTPUT);
  pinMode(LED_RIGHT, OUTPUT);

  updateLeds(Direction::CENTER);

  Serial.println(F("=================================================="));
  Serial.println(F(" Argus P0005: Tilt Emulation & Direction Logic   "));
  Serial.println(F(" Threshold: 25 deg | Release: 17 deg | Filter: 250ms"));
  Serial.println(F(" Format: TILT:<roll>,DIR:<direction>              "));
  Serial.println(F("=================================================="));
}

void loop() {
  // Чтение потенциометра: 0..1023 преобразуем в угол крена -45.0 .. +45.0 градусов
  int raw = analogRead(POT_PIN);
  float roll = ((float)raw / 1023.0f) * 90.0f - 45.0f;

  // Классификация направления с учетом времени удержания и гистерезиса
  Direction currentDir = classifier.classify(roll, millis());

  if (currentDir != lastDir) {
    lastDir = currentDir;
    updateLeds(currentDir);
  }

  // Вывод данных для Serial Monitor / Serial Plotter
  Serial.print(F("TILT:"));
  Serial.print(roll, 1);
  Serial.print(F(",DIR:"));
  Serial.println(getDirString(currentDir));

  delay(50);
}
