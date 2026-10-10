// Модульные тесты для логики классификации поворотов (g++ / cl.exe).
// Возвращает 0 при успешном прохождении всех тестов, 1 при ошибке.

#include <iostream>
#include <cstdlib>
#include "direction_logic.h"

#define TEST_ASSERT(cond, msg) \
  do { \
    if (!(cond)) { \
      std::cerr << "[FAIL] Line " << __LINE__ << ": " << msg << std::endl; \
      std::exit(1); \
    } \
  } while (0)

const char* directionToString(Direction dir) {
  switch (dir) {
    case Direction::CENTER: return "CENTER";
    case Direction::LEFT:   return "LEFT";
    case Direction::RIGHT:  return "RIGHT";
    default:                return "UNKNOWN";
  }
}

int main() {
  std::cout << "=========================================" << std::endl;
  std::cout << " Running Argus Direction Logic Test Suite" << std::endl;
  std::cout << "=========================================" << std::endl;

  // -------------------------------------------------------------
  // Тест 1: При ровном угле (0°) и любом времени — всегда CENTER
  // -------------------------------------------------------------
  {
    std::cout << "[RUN] Test 1: Neutral angle (0 deg) -> CENTER" << std::endl;
    DirectionClassifier classifier;
    TEST_ASSERT(classifier.classify(0.0f, 0) == Direction::CENTER, "t=0 at 0 deg must be CENTER");
    TEST_ASSERT(classifier.classify(0.0f, 100) == Direction::CENTER, "t=100 at 0 deg must be CENTER");
    TEST_ASSERT(classifier.classify(0.0f, 500) == Direction::CENTER, "t=500 at 0 deg must be CENTER");
    TEST_ASSERT(classifier.classify(0.0f, 10000) == Direction::CENTER, "t=10000 at 0 deg must be CENTER");
    std::cout << "  [PASS] Neutral angle test passed." << std::endl;
  }

  // -------------------------------------------------------------
  // Тест 2: При угле >25°, но короче 250мс (шум шага) — остаётся CENTER
  // -------------------------------------------------------------
  {
    std::cout << "[RUN] Test 2: Transient tilt (<250ms step noise) -> remains CENTER" << std::endl;
    DirectionClassifier classifier;
    // Всплеск наклона трости вправо на 30° продолжительностью 150мс
    TEST_ASSERT(classifier.classify(30.0f, 100) == Direction::CENTER, "t=100 initial spike must stay CENTER");
    TEST_ASSERT(classifier.classify(30.0f, 200) == Direction::CENTER, "t=200 (100ms held) must stay CENTER");
    TEST_ASSERT(classifier.classify(30.0f, 240) == Direction::CENTER, "t=240 (140ms held < 250ms) must stay CENTER");
    // Возврат в 0° до истечения 250мс
    TEST_ASSERT(classifier.classify(0.0f, 250) == Direction::CENTER, "t=250 return to 0 deg must stay CENTER");
    TEST_ASSERT(classifier.classify(0.0f, 600) == Direction::CENTER, "t=600 subsequent time must remain CENTER");
    std::cout << "  [PASS] Transient step noise test passed." << std::endl;
  }

  // -------------------------------------------------------------
  // Тест 3: При угле >25° дольше 250мс — переход в RIGHT
  // -------------------------------------------------------------
  {
    std::cout << "[RUN] Test 3: Sustained tilt (>25 deg, >250ms) -> transitions to RIGHT" << std::endl;
    DirectionClassifier classifier;
    TEST_ASSERT(classifier.classify(30.0f, 1000) == Direction::CENTER, "t=1000 onset must be CENTER");
    TEST_ASSERT(classifier.classify(30.0f, 1150) == Direction::CENTER, "t=1150 (150ms) must be CENTER");
    // Ровно и более 250мс удержания
    TEST_ASSERT(classifier.classify(30.0f, 1250) == Direction::RIGHT, "t=1250 (250ms held) must be RIGHT");
    TEST_ASSERT(classifier.classify(32.0f, 1500) == Direction::RIGHT, "t=1500 sustained must remain RIGHT");
    std::cout << "  [PASS] Sustained tilt to RIGHT test passed." << std::endl;
  }

  // -------------------------------------------------------------
  // Тест 4: Проверка гистерезиса: после перехода в RIGHT возврат в CENTER
  //         только при угле ниже (25 - 8) = 17°, не раньше
  // -------------------------------------------------------------
  {
    std::cout << "[RUN] Test 4: Hysteresis check (RIGHT -> CENTER only below 17 deg)" << std::endl;
    DirectionClassifier classifier;

    // Сначала инициируем состояние RIGHT
    classifier.classify(30.0f, 100);
    classifier.classify(30.0f, 360); // 260ms held -> RIGHT
    TEST_ASSERT(classifier.currentDir == Direction::RIGHT, "State must be RIGHT");

    // Угол снизился до 22° (ниже 25°, но выше 17°) -> должен оставаться RIGHT
    TEST_ASSERT(classifier.classify(22.0f, 500) == Direction::RIGHT, "22 deg (>17) must stay RIGHT");

    // Угол снизился до 18° (>17°) и удерживается 500мс -> должен оставаться RIGHT!
    TEST_ASSERT(classifier.classify(18.0f, 700) == Direction::RIGHT, "18 deg (>17) at t=700 must stay RIGHT");
    TEST_ASSERT(classifier.classify(18.0f, 1200) == Direction::RIGHT, "18 deg (>17) held >250ms must stay RIGHT");

    // Граничное значение 17.1° (>17°) -> все еще RIGHT
    TEST_ASSERT(classifier.classify(17.1f, 1300) == Direction::RIGHT, "17.1 deg (>17) must stay RIGHT");

    // Угол упал ниже 17° (например, 15°):
    // При кратковременном падении еще действует фильтр удержания
    TEST_ASSERT(classifier.classify(15.0f, 1400) == Direction::RIGHT, "15 deg (<17) at onset must hold RIGHT");

    // После удержания угла <17° дольше minHoldMs (250мс) происходит возврат в CENTER
    TEST_ASSERT(classifier.classify(15.0f, 1660) == Direction::CENTER, "15 deg held for 260ms must return to CENTER");
    std::cout << "  [PASS] Hysteresis threshold test passed." << std::endl;
  }

  // -------------------------------------------------------------
  // Тест 5: Симметричный поворот влево (LEFT) и гистерезис
  // -------------------------------------------------------------
  {
    std::cout << "[RUN] Test 5: Symmetrical LEFT tilt with hysteresis" << std::endl;
    DirectionClassifier classifier;

    // Всплеск шума влево
    TEST_ASSERT(classifier.classify(-28.0f, 100) == Direction::CENTER, "t=100 left spike must be CENTER");
    TEST_ASSERT(classifier.classify(0.0f, 200) == Direction::CENTER, "t=200 left reset must be CENTER");

    // Устойчивый поворот влево > 250мс
    classifier.classify(-30.0f, 1000);
    TEST_ASSERT(classifier.classify(-30.0f, 1260) == Direction::LEFT, "t=1260 sustained left must be LEFT");

    // Гистерезис влево: угол -19° (по модулю > 17°) должен удерживать LEFT
    TEST_ASSERT(classifier.classify(-19.0f, 1600) == Direction::LEFT, "-19 deg held must stay LEFT");

    // Угол вернулся к -10° (по модулю < 17°) и удерживается > 250мс
    classifier.classify(-10.0f, 1700);
    TEST_ASSERT(classifier.classify(-10.0f, 1960) == Direction::CENTER, "return to -10 deg held must be CENTER");
    std::cout << "  [PASS] Symmetrical LEFT tilt test passed." << std::endl;
  }

  std::cout << "=========================================" << std::endl;
  std::cout << " ALL DIRECTION LOGIC TESTS PASSED (5/5)   " << std::endl;
  std::cout << "=========================================" << std::endl;
  return 0;
}
