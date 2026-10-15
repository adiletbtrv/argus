// Прототип P0006: Делитель напряжения 5В -> 3.3В + Односторонняя Bluetooth телеметрия HC-05
// Согласование логических уровней TX Arduino (5V) -> RX HC-05 (3.3V) и протокол телеметрии
// Спецификация: argus/docs/pinout.md, раздел 4

#include <SoftwareSerial.h>

const int BT_RX_PIN = 10; // Не используется для приема (канал строго трость -> телефон)
const int BT_TX_PIN = 11; // Линия передачи TX (5V) к делителю 1k/2k

SoftwareSerial btSerial(BT_RX_PIN, BT_TX_PIN);

// Пример счетчика для эмуляции изменения показаний датчиков
long simulatedDist = 45;
int simulatedDirState = 0; // 0=CENTER, 1=LEFT, 2=RIGHT

const char* getDirString(int state) {
  switch (state) {
    case 0: return "CENTER";
    case 1: return "LEFT";
    case 2: return "RIGHT";
    default: return "CENTER";
  }
}

void setup() {
  // Аппаратный Serial (D0/D1) остается свободным для USB отладки
  Serial.begin(9600);

  // Программный последовательный порт на пинах D10/D11
  btSerial.begin(9600);

  // Критически важная настройка безопасности:
  // отключаем прием прерываний RX — трость только передает наружу,
  // исключая зависания pulseIn() и перехват управления снаружи
  btSerial.stopListening();

  Serial.println(F("=================================================="));
  Serial.println(F(" Argus P0006: 5V->3.3V Level Shifter & Telemetry  "));
  Serial.println(F(" Divider: 1k (top) / 2k (bottom) -> V_out = 3.33V "));
  Serial.println(F(" Protocol: DIST:<cm>,DIR:<CENTER|LEFT|RIGHT>      "));
  Serial.println(F("=================================================="));
}

void loop() {
  const char* dirStr = getDirString(simulatedDirState);

  // Формирование и отправка пакета в сторону Bluetooth модуля HC-05
  btSerial.print(F("DIST:"));
  btSerial.print(simulatedDist);
  btSerial.print(F(",DIR:"));
  btSerial.println(dirStr);

  // Локальный диагностический вывод в аппаратный Serial Monitor
  Serial.print(F("[TX HC-05 3.3V] Sent packet: DIST:"));
  Serial.print(simulatedDist);
  Serial.print(F(",DIR:"));
  Serial.print(dirStr);
  Serial.println(F(" | Level Shifter: D11 (5V) -> [1k/2k] -> 3.33V (Safe)"));

  // Смена эмулируемых параметров для следующего пакета
  simulatedDist = (simulatedDist >= 150) ? 20 : simulatedDist + 15;
  simulatedDirState = (simulatedDirState + 1) % 3;

  delay(1000);
}
