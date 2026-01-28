package com.bdc.formula;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class EasterCalculatorTest {

  @Test
  void easter2024() {
    assertEquals(LocalDate.of(2024, 3, 31), EasterCalculator.westernEaster(2024));
  }

  @Test
  void easter2025() {
    assertEquals(LocalDate.of(2025, 4, 20), EasterCalculator.westernEaster(2025));
  }

  @Test
  void easter2026() {
    assertEquals(LocalDate.of(2026, 4, 5), EasterCalculator.westernEaster(2026));
  }

  @Test
  void easter2027() {
    assertEquals(LocalDate.of(2027, 3, 28), EasterCalculator.westernEaster(2027));
  }

  @Test
  void easter2028() {
    assertEquals(LocalDate.of(2028, 4, 16), EasterCalculator.westernEaster(2028));
  }
}
