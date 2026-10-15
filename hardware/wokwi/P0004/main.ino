// Прототип P0004 (PRO): Дешифратор 74HC138 + Светодиоды поворотников (Active
// LOW) Аппаратная взаимоблокировка (Hardware Interlock) и защита от переходных
// глитчей Спецификация: argus/docs/truth_table.md и argus/docs/pinout.md

#include <Arduino.h>

constexpr uint8_t PIN_A = 2; // Адресный вход A (бит 0, LSB, PD2)
constexpr uint8_t PIN_B = 3; // Адресный вход B (бит 1, PD3)
constexpr uint8_t PIN_C = 4; // Адресный вход C (бит 2, MSB, PD4)

constexpr unsigned long STATE_INTERVAL_MS = 2000;

struct TestVector {
  uint8_t c;
  uint8_t b;
  uint8_t a;
  const char *activePin;
  const char *description;
};

const TestVector TRUTH_TABLE[] = {
    {0, 0, 0, "/Y0", "CENTER / STRAIGHT (All Turn LEDs OFF)"},
    {0, 0, 1, "/Y1", "LEFT TURN         (LED LEFT ON - Sinking to GND)"},
    {0, 1, 0, "/Y2", "RIGHT TURN        (LED RIGHT ON - Sinking to GND)"},
    {0, 1, 1, "/Y3", "RESERVE CHANNEL   (NC - All Turn LEDs OFF)"}};

constexpr size_t NUM_STATES = sizeof(TRUTH_TABLE) / sizeof(TRUTH_TABLE[0]);

// Синхронная (атомарная) установка адреса на пинах D2, D3, D4 без переходных
// глитчей
void setAddressAtomic(uint8_t c, uint8_t b, uint8_t a) {
#if defined(PORTD)
  // Биты PD2 (пин 2), PD3 (пин 3), PD4 (пин 4) обновляются за 1 такт процессора
  uint8_t mask = (1 << PD2) | (1 << PD3) | (1 << PD4);
  uint8_t bits =
      ((a & 0x01) << PD2) | ((b & 0x01) << PD3) | ((c & 0x01) << PD4);
  uint8_t oldSREG = SREG;
  noInterrupts();
  PORTD = (PORTD & ~mask) | bits;
  SREG = oldSREG;
#else
  digitalWrite(PIN_C, c ? HIGH : LOW);
  digitalWrite(PIN_B, b ? HIGH : LOW);
  digitalWrite(PIN_A, a ? HIGH : LOW);
#endif
}

void printStateReport(size_t index, const TestVector &tv) {
  Serial.print(F("[STATE "));
  Serial.print(index);
  Serial.print(F("] CBA="));
  Serial.print(tv.c);
  Serial.print(tv.b);
  Serial.print(tv.a);
  Serial.print(F(" | Active(LOW): "));
  Serial.print(tv.activePin);
  Serial.print(F(" | "));
  Serial.println(tv.description);
}

void setup() {
  Serial.begin(115200); // Стандартная скорость профессиональной телеметрии
  pinMode(PIN_A, OUTPUT);
  pinMode(PIN_B, OUTPUT);
  pinMode(PIN_C, OUTPUT);

  setAddressAtomic(0, 0, 0);

  Serial.println(
      F("============================================================="));
  Serial.println(
      F(" Argus P0004: 74HC138 Decoder Truth Table Testbench (PRO)    "));
  Serial.println(
      F(" Architecture: Active-LOW Current Sinking & Glitch-Free Port "));
  Serial.println(
      F("============================================================="));
  printStateReport(0, TRUTH_TABLE[0]);
}

void loop() {
  static unsigned long lastSwitchMs = 0;
  static size_t currentState = 0;

  unsigned long now = millis();
  if (now - lastSwitchMs >= STATE_INTERVAL_MS) {
    lastSwitchMs = now;
    currentState = (currentState + 1) % NUM_STATES;

    const TestVector &tv = TRUTH_TABLE[currentState];
    setAddressAtomic(tv.c, tv.b, tv.a);
    printStateReport(currentState, tv);
  }
}