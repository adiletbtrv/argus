// ASSUMPTION: Pure C++ implementation without Arduino dependencies.
// ASSUMPTION: Positive roll angles (>= thresholdDeg) denote turning RIGHT.
// ASSUMPTION: Negative roll angles (<= -thresholdDeg) denote turning LEFT.
// ASSUMPTION: `pendingDir` tracks candidate direction pending hold-time verification.

#ifndef DIRECTION_LOGIC_H
#define DIRECTION_LOGIC_H

enum class Direction {
  CENTER,
  LEFT,
  RIGHT
};

struct DirectionClassifier {
  float thresholdDeg = 25.0f;     // Порог наклона в градусах
  float hysteresisDeg = 8.0f;     // Гистерезис для предотвращения дребезга на границе
  unsigned long minHoldMs = 250;  // Минимальная длительность удержания против шума ходьбы
  Direction currentDir = Direction::CENTER;
  unsigned long pendingSince = 0;
  Direction pendingDir = Direction::CENTER;

  // classify принимает угол крена (rollDeg) и текущую временную метку (nowMs).
  // Функция не использует millis() напрямую, что обеспечивает детерминированное тестирование.
  Direction classify(float rollDeg, unsigned long nowMs);
};

#endif // DIRECTION_LOGIC_H
