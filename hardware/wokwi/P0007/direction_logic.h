#ifndef DIRECTION_LOGIC_H
#define DIRECTION_LOGIC_H

// модуль классификации направления на чистых плюсах
// положительный угол - поворот направо, отрицательный - налево
// pendingDir отслеживает кандидатное направление до подтверждения по таймауту удержания

enum class Direction {
  CENTER,
  LEFT,
  RIGHT
};

struct DirectionClassifier {
  float thresholdDeg = 25.0f;     // порог наклона в градусах
  float hysteresisDeg = 8.0f;     // гистерезис для предотвращения дребезга на границе
  unsigned long minHoldMs = 250;  // минимальная длительность удержания против шума ходьбы
  Direction currentDir = Direction::CENTER;
  unsigned long pendingSince = 0;
  Direction pendingDir = Direction::CENTER;

  // функция принимает угол крена (rollDeg) и монотонную временную метку (nowMs)
  Direction classify(float rollDeg, unsigned long nowMs);
};

#endif // DIRECTION_LOGIC_H
