#ifndef DIRECTION_LOGIC_H
#define DIRECTION_LOGIC_H

// Модуль классификации направления на чистом C++ без платформозависимых вызовов.
// Положительный угол крена соответствует повороту направо, отрицательный — налево.
// pendingDir отслеживает кандидатное направление до подтверждения по тайм-ауту удержания.

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

  // Функция принимает угол крена (rollDeg) и монотонную временную метку (nowMs).
  Direction classify(float rollDeg, unsigned long nowMs);
};

#endif // DIRECTION_LOGIC_H
