// фильтрация и гистерезис для определения поворота трости

#include "direction_logic.h"

Direction DirectionClassifier::classify(float rollDeg, unsigned long nowMs) {
  Direction candidate = Direction::CENTER;

  // определение целевого кандидата с учетом текущего направления и зоны гистерезиса
  if (currentDir == Direction::RIGHT) {
    if (rollDeg >= (thresholdDeg - hysteresisDeg)) {
      candidate = Direction::RIGHT;
    } else if (rollDeg <= -thresholdDeg) {
      candidate = Direction::LEFT;
    } else {
      candidate = Direction::CENTER;
    }
  } else if (currentDir == Direction::LEFT) {
    if (rollDeg <= -(thresholdDeg - hysteresisDeg)) {
      candidate = Direction::LEFT;
    } else if (rollDeg >= thresholdDeg) {
      candidate = Direction::RIGHT;
    } else {
      candidate = Direction::CENTER;
    }
  } else {
    // currentDir == Direction::CENTER
    if (rollDeg >= thresholdDeg) {
      candidate = Direction::RIGHT;
    } else if (rollDeg <= -thresholdDeg) {
      candidate = Direction::LEFT;
    } else {
      candidate = Direction::CENTER;
    }
  }

  // если кандидат совпадает с активным направлением, сбрасываем ожидание перехода
  if (candidate == currentDir) {
    pendingDir = currentDir;
    pendingSince = nowMs;
    return currentDir;
  }

  // обнаружена смена направления кандидата так что засекаем время начала
  if (pendingDir != candidate) {
    pendingDir = candidate;
    pendingSince = nowMs;
  }

  // проверяем удерживается ли кандидат дольше установленного порога minHoldMs
  if ((nowMs - pendingSince) >= minHoldMs) {
    currentDir = candidate;
    pendingDir = currentDir;
    pendingSince = nowMs;
  }

  return currentDir;
}
