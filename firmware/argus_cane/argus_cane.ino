// основная прошивка умной трости Argus
// датчик положения MPU6050 (0x68), дешифратор 74HC138
// ультразвуковой датчик расстояния RCWL-9610A (HC-SR04)
// виброотклик (PWM D9) и односторонняя Bluetooth телеметрия HC-05 (TX D11)

#include <Wire.h>
#include <SoftwareSerial.h>
#include <MPU6050_light.h>
#include "../direction_logic/direction_logic.h"

#if defined(ARDUINO) && !defined(ARGUS_SEPARATE_COMPILATION)
#include "../direction_logic/direction_logic.cpp"
#endif

// пины управления 74HC138
const int PIN_A = 2; // адресный вход A (бит 0)
const int PIN_B = 3; // адресный вход B (бит 1)
const int PIN_C = 4; // адресный вход C (бит 2, зафиксирован в LOW для Y0..Y3)

// пины ультразвукового датчика расстояния RCWL-9610A (HC-SR04)
const int PIN_TRIG = 5; // сигнал запуска измерения (Trig)
const int PIN_ECHO = 6; // вход эхо-импульса (Echo)

// управление вибромоторчиком (ШИМ через NPN транзистор)
const int PIN_VIBRO = 9;

// пины модуля Bluetooth HC-05 (SoftwareSerial)
// аппаратные пины D0/D1 сохранены для USB отладки (Serial Monitor)
const int BT_RX_PIN = 10; // не используется для приема (односторонняя телеметрия)
const int BT_TX_PIN = 11; // TX Arduino к RX HC-05 через делитель напряжения (1к / 2к)

// пин и порог детектора зарядки (Battery Shield TP4056)
const int PIN_CHARGING = A1;

// параметры обнаружения препятствий и виброотклика
const long OBSTACLE_THRESHOLD_CM = 50; // порог дистанции для тактильного сигнала (см)
const int VIBRO_INTENSITY = 255;       // интенсивность вибрации
const unsigned long VIBRO_PULSE_DURATION_MS = 40; // длительность тактильного импульса

SoftwareSerial btSerial(BT_RX_PIN, BT_TX_PIN);
MPU6050 mpu(Wire);
DirectionClassifier classifier;
Direction lastReportedDir = Direction::CENTER;
float rollOffset = 0.0f; // программное смещение нуля по крену

unsigned long lastDistanceCheckMs = 0;
const unsigned long DISTANCE_CHECK_INTERVAL_MS = 60;

// переменные автомата состояний виброотклика
unsigned long lastPulseToggle = 0;
bool pulseState = false;
unsigned long pulseIntervalMs = 0;

// переменные и интервал периодической телеметрии
long currentDist = -1;
unsigned long lastTelemetryMs = 0;
const unsigned long TELEMETRY_INTERVAL_MS = 180; // периодичность отправки (150-200 мс)

// переменные детектора зарядки и двойного виброотклика
bool lastChargingState = false;

enum class ChargingVibroState {
  IDLE,
  PULSE_1,
  PAUSE,
  PULSE_2
};

ChargingVibroState chargingVibroState = ChargingVibroState::IDLE;
unsigned long chargingVibroTimer = 0;
const unsigned long CHARGING_PULSE_MS = 60;
const unsigned long CHARGING_PAUSE_MS = 60;

long readDistanceCm() {
  digitalWrite(PIN_TRIG, LOW);
  delayMicroseconds(2);
  digitalWrite(PIN_TRIG, HIGH);
  delayMicroseconds(10);
  digitalWrite(PIN_TRIG, LOW);

  unsigned long duration = pulseIn(PIN_ECHO, HIGH, 25000); // таймаут примерно 4.3 м
  if (duration == 0) {
    return -1; // препятствие вне зоны обнаружения
  }
  return duration / 58; // дистанция в см
}

const char* getDirectionString(Direction dir) {
  switch (dir) {
    case Direction::CENTER: return "CENTER";
    case Direction::LEFT:   return "LEFT";
    case Direction::RIGHT:  return "RIGHT";
    default:                return "CENTER";
  }
}

// трость не принимает команды извне: вибрация и LED управляются только локальными датчиками, Bluetooth используется только для исходящей телеметрии
void sendTelemetry(long distCm, Direction dir) {
  btSerial.print(F("DIST:"));
  btSerial.print(distCm);
  btSerial.print(F(",DIR:"));
  btSerial.println(getDirectionString(dir));
}

void applyDirectionOutputs(Direction dir) {
  switch (dir) {
    case Direction::CENTER:
      // код 00 (CBA = 000) -> активен выход Y0 (светодиоды выключены)
      digitalWrite(PIN_A, LOW);
      digitalWrite(PIN_B, LOW);
      break;

    case Direction::LEFT:
      // код 01 (CBA = 001) -> активен выход Y1 (включает левый светодиод)
      digitalWrite(PIN_A, HIGH);
      digitalWrite(PIN_B, LOW);
      break;

    case Direction::RIGHT:
      // код 10 (CBA = 010) -> активен выход Y2 (включает правый светодиод)
      digitalWrite(PIN_A, LOW);
      digitalWrite(PIN_B, HIGH);
      break;
  }
}

void updateChargingVibro(unsigned long now) {
  if (chargingVibroState == ChargingVibroState::IDLE) {
    return;
  }

  if (chargingVibroState == ChargingVibroState::PULSE_1) {
    if (now - chargingVibroTimer >= CHARGING_PULSE_MS) {
      analogWrite(PIN_VIBRO, 0);
      chargingVibroState = ChargingVibroState::PAUSE;
      chargingVibroTimer = now;
    }
  } else if (chargingVibroState == ChargingVibroState::PAUSE) {
    if (now - chargingVibroTimer >= CHARGING_PAUSE_MS) {
      analogWrite(PIN_VIBRO, VIBRO_INTENSITY);
      chargingVibroState = ChargingVibroState::PULSE_2;
      chargingVibroTimer = now;
    }
  } else if (chargingVibroState == ChargingVibroState::PULSE_2) {
    if (now - chargingVibroTimer >= CHARGING_PULSE_MS) {
      analogWrite(PIN_VIBRO, 0);
      chargingVibroState = ChargingVibroState::IDLE;
      chargingVibroTimer = now;
      lastPulseToggle = now;
    }
  }
}

void updateVibrationFSM(unsigned long now) {
  if (chargingVibroState != ChargingVibroState::IDLE) {
    return; // не перебиваем сигнал подключения зарядки
  }

  if (pulseIntervalMs == 0) {
    if (pulseState) {
      analogWrite(PIN_VIBRO, 0);
      pulseState = false;
    }
    return;
  }

  if (pulseState) {
    if (now - lastPulseToggle >= VIBRO_PULSE_DURATION_MS) {
      analogWrite(PIN_VIBRO, 0);
      pulseState = false;
      lastPulseToggle = now;
    }
  } else {
    if (now - lastPulseToggle >= pulseIntervalMs) {
      analogWrite(PIN_VIBRO, VIBRO_INTENSITY);
      pulseState = true;
      lastPulseToggle = now;
    }
  }
}

void setup() {
  Serial.begin(9600);
  btSerial.begin(9600);
  btSerial.stopListening(); // отключаем прерывания RX: трость только передает, входящие байты игнорируются
  Wire.begin();
  Wire.setWireTimeout(3000, true); // сброс I2C при зависании SDA/SCL > 3 мс

  // настройка выходов 74HC138
  pinMode(PIN_A, OUTPUT);
  pinMode(PIN_B, OUTPUT);
  pinMode(PIN_C, OUTPUT);

  digitalWrite(PIN_A, LOW);
  digitalWrite(PIN_B, LOW);
  digitalWrite(PIN_C, LOW);

  // настройка ультразвукового датчика расстояния
  pinMode(PIN_TRIG, OUTPUT);
  pinMode(PIN_ECHO, INPUT);
  digitalWrite(PIN_TRIG, LOW);

  // настройка вывода вибромоторчика
  pinMode(PIN_VIBRO, OUTPUT);
  analogWrite(PIN_VIBRO, 0);

  // настройка входа детектора зарядки
  pinMode(PIN_CHARGING, INPUT);

  Serial.println(F("Argus Smart Cane: инициализация..."));

  byte status = mpu.begin();
  if (status != 0) {
    Serial.print(F("[ERROR] MPU6050 ошибка: "));
    Serial.println(status);
    while (true) {
      delay(1000);
    }
  }

  // при калибровке трость должна стоять вертикально и неподвижно
  Serial.println(F("Калибровка гироскопа и акселерометра MPU6050..."));
  mpu.calcOffsets();

  // усреднение первых 20 отсчетов крена для нулевой точки
  float rollSum = 0;
  for (int i = 0; i < 20; i++) {
    mpu.update();
    rollSum += mpu.getAngleX();
    delay(5);
  }
  rollOffset = rollSum / 20.0f;

  Serial.println(F("Готово к работе."));
}

void loop() {
  // опрос датчика положения и классификация направления поворота
  mpu.update();
  float roll = mpu.getAngleX() - rollOffset;
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

  // опрос RCWL-9610A и пересчет интервала виброотклика
  unsigned long now = millis();
  if (now - lastDistanceCheckMs >= DISTANCE_CHECK_INTERVAL_MS) {
    lastDistanceCheckMs = now;
    currentDist = readDistanceCm();

    if (currentDist > 0 && currentDist <= OBSTACLE_THRESHOLD_CM) {
      pulseIntervalMs = map(constrain(currentDist, 5, 50), 5, 50, 40, 400);
    } else {
      pulseIntervalMs = 0;
    }
  }

  // периодическая отправка телеметрии каждые 180 мс
  if (now - lastTelemetryMs >= TELEMETRY_INTERVAL_MS) {
    lastTelemetryMs = now;
    sendTelemetry(currentDist, currentDir);
  }

  // детекция переднего фронта подключения зарядки
  bool currentChargingState = (analogRead(PIN_CHARGING) > 512);
  if (currentChargingState && !lastChargingState) {
    chargingVibroState = ChargingVibroState::PULSE_1;
    chargingVibroTimer = now;
    analogWrite(PIN_VIBRO, VIBRO_INTENSITY);
  }
  lastChargingState = currentChargingState;

  // обновление конечных автоматов вибрации
  updateChargingVibro(now);
  updateVibrationFSM(now);
}
