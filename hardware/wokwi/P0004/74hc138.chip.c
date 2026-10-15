// 74HC138 3-to-8 Line Decoder / Demultiplexer Wokwi Custom Chip (Industrial
// Grade) Follows truth table in argus/docs/truth_table.md and
// argus/docs/pinout.md
#include "wokwi-api.h"
#include <stdbool.h>
#include <stdio.h>
#include <stdlib.h>

typedef struct {
  pin_t pin_a;
  pin_t pin_b;
  pin_t pin_c;
  pin_t pin_g1;
  pin_t pin_g2a;
  pin_t pin_g2b;
  pin_t pin_y[8];
} chip_state_t;

static void update_outputs(chip_state_t *chip) {
  // G1 is Active HIGH; /G2A and /G2B are Active LOW
  bool g1_active = (pin_read(chip->pin_g1) == HIGH);
  bool g2a_active = (pin_read(chip->pin_g2a) == LOW);
  bool g2b_active = (pin_read(chip->pin_g2b) == LOW);

  if (g1_active && g2a_active && g2b_active) {
    uint8_t a = (pin_read(chip->pin_a) == HIGH) ? 1U : 0U;
    uint8_t b = (pin_read(chip->pin_b) == HIGH) ? 1U : 0U;
    uint8_t c = (pin_read(chip->pin_c) == HIGH) ? 1U : 0U;

    uint8_t address = a | (b << 1) | (c << 2);

    for (uint8_t i = 0; i < 8; i++) {
      // Active-LOW logic: only the addressed channel sinks current (LOW)
      pin_write(chip->pin_y[i], (i == address) ? LOW : HIGH);
    }
  } else {
    // Chip disabled: all outputs stay HIGH (Hi-Z / off for current-sinking
    // loads)
    for (uint8_t i = 0; i < 8; i++) {
      pin_write(chip->pin_y[i], HIGH);
    }
  }
}

static void on_pin_change(void *user_data, pin_t pin, uint32_t value) {
  (void)pin;
  (void)value;
  update_outputs((chip_state_t *)user_data);
}

void chip_init(void) {
  chip_state_t *chip = (chip_state_t *)calloc(1, sizeof(chip_state_t));
  if (!chip)
    return;

  // Safe defaults: if enable pins are left floating, chip stays DISABLED
  chip->pin_a = pin_init("A", INPUT_PULLDOWN);
  chip->pin_b = pin_init("B", INPUT_PULLDOWN);
  chip->pin_c = pin_init("C", INPUT_PULLDOWN);
  chip->pin_g1 =
      pin_init("G1", INPUT_PULLDOWN); // Needs explicit HIGH to enable
  chip->pin_g2a = pin_init("G2A", INPUT_PULLUP); // Needs explicit LOW to enable
  chip->pin_g2b = pin_init("G2B", INPUT_PULLUP); // Needs explicit LOW to enable

  char name[4];
  for (uint8_t i = 0; i < 8; i++) {
    snprintf(name, sizeof(name), "Y%u", i);
    chip->pin_y[i] = pin_init(name, OUTPUT_HIGH);
  }

  const pin_watch_config_t watch_config = {
      .edge = BOTH, .pin_change = on_pin_change, .user_data = chip};

  pin_watch(chip->pin_a, &watch_config);
  pin_watch(chip->pin_b, &watch_config);
  pin_watch(chip->pin_c, &watch_config);
  pin_watch(chip->pin_g1, &watch_config);
  pin_watch(chip->pin_g2a, &watch_config);
  pin_watch(chip->pin_g2b, &watch_config);

  update_outputs(chip);
}