// 74HC138 3-to-8 Line Decoder / Demultiplexer Wokwi Custom Chip
// Follows truth table in argus/docs/truth_table.md and argus/docs/pinout.md
#include "wokwi-api.h"
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
  int g1  = pin_read(chip->pin_g1);
  int g2a = pin_read(chip->pin_g2a);
  int g2b = pin_read(chip->pin_g2b);

  // Enable condition: G1 == HIGH && /G2A == LOW && /G2B == LOW
  if (g1 == HIGH && g2a == LOW && g2b == LOW) {
    int a = pin_read(chip->pin_a);
    int b = pin_read(chip->pin_b);
    int c = pin_read(chip->pin_c);

    int address = (a ? 1 : 0) | ((b ? 1 : 0) << 1) | ((c ? 1 : 0) << 2);

    for (int i = 0; i < 8; i++) {
      // 74HC138 outputs are Active LOW: selected output is LOW, others are HIGH
      pin_write(chip->pin_y[i], (i == address) ? LOW : HIGH);
    }
  } else {
    // Disabled: all outputs are HIGH (inactive)
    for (int i = 0; i < 8; i++) {
      pin_write(chip->pin_y[i], HIGH);
    }
  }
}

static void pin_changed(void *user_data, pin_t pin, uint32_t value) {
  chip_state_t *chip = (chip_state_t *)user_data;
  update_outputs(chip);
}

void chip_init(void) {
  chip_state_t *chip = (chip_state_t *)malloc(sizeof(chip_state_t));

  chip->pin_a   = pin_init("A", INPUT);
  chip->pin_b   = pin_init("B", INPUT);
  chip->pin_c   = pin_init("C", INPUT);
  chip->pin_g1  = pin_init("G1", INPUT);
  chip->pin_g2a = pin_init("G2A", INPUT);
  chip->pin_g2b = pin_init("G2B", INPUT);

  char name[4];
  for (int i = 0; i < 8; i++) {
    sprintf(name, "Y%d", i);
    chip->pin_y[i] = pin_init(name, OUTPUT);
    pin_write(chip->pin_y[i], HIGH);
  }

  const pin_watch_config_t watch_config = {
    .edge = BOTH,
    .pin_change = pin_changed,
    .user_data = chip
  };

  pin_watch(chip->pin_a, &watch_config);
  pin_watch(chip->pin_b, &watch_config);
  pin_watch(chip->pin_c, &watch_config);
  pin_watch(chip->pin_g1, &watch_config);
  pin_watch(chip->pin_g2a, &watch_config);
  pin_watch(chip->pin_g2b, &watch_config);

  update_outputs(chip);
}
