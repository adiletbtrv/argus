// Прототип P0004: Дешифратор 74HC138 + Светодиоды поворотников (Active LOW)
// Проверка таблицы истинности и аппаратной взаимоблокировки сигналов
// Спецификация: argus/docs/truth_table.md и argus/docs/pinout.md

const int PIN_A = 2; // Адресный вход A (бит 0, LSB)
const int PIN_B = 3; // Адресный вход B (бит 1)
const int PIN_C = 4; // Адресный вход C (бит 2, MSB, зафиксирован LOW)

void setAddress(int c, int b, int a) {
  digitalWrite(PIN_C, c ? HIGH : LOW);
  digitalWrite(PIN_B, b ? HIGH : LOW);
  digitalWrite(PIN_A, a ? HIGH : LOW);
}

void setup() {
  Serial.begin(9600);
  pinMode(PIN_A, OUTPUT);
  pinMode(PIN_B, OUTPUT);
  pinMode(PIN_C, OUTPUT);

  // Исходное состояние: CBA = 000 -> активен /Y0 (нейтраль, светодиоды выключены)
  setAddress(0, 0, 0);

  Serial.println(F("=================================================="));
  Serial.println(F(" Argus P0004: 74HC138 Decoder Truth Table Testbench"));
  Serial.println(F(" Hardware Interlock & Active-LOW Current Sinking  "));
  Serial.println(F("=================================================="));
}

void loop() {
  // Состояние 0: Нейтраль (CENTER / STRAIGHT)
  // Входы: C=0, B=0, A=0 -> Выход /Y0 = LOW, остальные HIGH. Светодиоды выключены.
  setAddress(0, 0, 0);
  Serial.println(F("[STATE 0] Address: C=0 B=0 A=0 -> /Y0 Active (LOW) -> CENTER (LEDs OFF)"));
  delay(2000);

  // Состояние 1: Поворот влево (LEFT)
  // Входы: C=0, B=0, A=1 -> Выход /Y1 = LOW. Катод LED LEFT притянут к GND, диод светит.
  setAddress(0, 0, 1);
  Serial.println(F("[STATE 1] Address: C=0 B=0 A=1 -> /Y1 Active (LOW) -> LEFT TURN (LED LEFT ON)"));
  delay(2000);

  // Состояние 2: Поворот вправо (RIGHT)
  // Входы: C=0, B=1, A=0 -> Выход /Y2 = LOW. Катод LED RIGHT притянут к GND, диод светит.
  setAddress(0, 1, 0);
  Serial.println(F("[STATE 2] Address: C=0 B=1 A=0 -> /Y2 Active (LOW) -> RIGHT TURN (LED RIGHT ON)"));
  delay(2000);

  // Состояние 3: Резервный канал (RESERVE)
  // Входы: C=0, B=1, A=1 -> Выход /Y3 = LOW (NC). Светодиоды выключены.
  setAddress(0, 1, 1);
  Serial.println(F("[STATE 3] Address: C=0 B=1 A=1 -> /Y3 Active (LOW) -> RESERVE (LEDs OFF)"));
  delay(2000);
}
