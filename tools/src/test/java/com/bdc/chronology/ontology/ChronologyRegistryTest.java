package com.bdc.chronology.ontology;

import static org.junit.jupiter.api.Assertions.*;

import com.bdc.chronology.ontology.algorithms.ChronologyAlgorithm;
import com.bdc.chronology.ontology.algorithms.IsoAlgorithm;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ChronologyRegistryTest {

  private ChronologyRegistry registry;

  @BeforeEach
  void setUp() {
    registry = ChronologyRegistry.getInstance();
    registry.reset(); // Ensure clean state with built-in algorithms
  }

  @Test
  void getInstance_returnsSameInstance() {
    ChronologyRegistry instance1 = ChronologyRegistry.getInstance();
    ChronologyRegistry instance2 = ChronologyRegistry.getInstance();
    assertSame(instance1, instance2);
  }

  @Test
  void builtInChronologies_areRegistered() {
    assertTrue(registry.hasChronology("ISO"));
    assertTrue(registry.hasChronology("HIJRI"));
    assertTrue(registry.hasChronology("JULIAN"));
  }

  @Test
  void hasChronology_caseInsensitive() {
    assertTrue(registry.hasChronology("iso"));
    assertTrue(registry.hasChronology("ISO"));
    assertTrue(registry.hasChronology("Iso"));
    assertTrue(registry.hasChronology("hijri"));
    assertTrue(registry.hasChronology("HIJRI"));
  }

  @Test
  void getAlgorithm_existingChronology_returnsAlgorithm() {
    ChronologyAlgorithm algorithm = registry.getAlgorithm("ISO");
    assertNotNull(algorithm);
    assertEquals("ISO", algorithm.getChronologyId());
  }

  @Test
  void getAlgorithm_unknownChronology_throwsException() {
    assertThrows(IllegalArgumentException.class, () -> registry.getAlgorithm("UNKNOWN"));
  }

  @Test
  void toIsoDate_isoChronology_returnsSameDate() {
    LocalDate result = registry.toIsoDate(2025, 6, 15, "ISO");
    assertEquals(LocalDate.of(2025, 6, 15), result);
  }

  @Test
  void toIsoDate_nullChronology_defaultsToIso() {
    LocalDate result = registry.toIsoDate(2025, 6, 15, null);
    assertEquals(LocalDate.of(2025, 6, 15), result);
  }

  @Test
  void toIsoDate_hijriChronology_convertsCorrectly() {
    LocalDate result = registry.toIsoDate(1446, 1, 1, "HIJRI");
    assertNotNull(result);
    // Hijri 1446-1-1 should be in 2024
    assertEquals(2024, result.getYear());
  }

  @Test
  void fromIsoDate_iso_returnsCorrectDate() {
    LocalDate isoDate = LocalDate.of(2025, 6, 15);
    ChronologyDate result = registry.fromIsoDate(isoDate, "ISO");

    assertEquals("ISO", result.chronologyId());
    assertEquals(2025, result.year());
    assertEquals(6, result.month());
    assertEquals(15, result.day());
  }

  @Test
  void fromIsoDate_hijri_returnsCorrectDate() {
    LocalDate isoDate = LocalDate.of(2024, 7, 7);
    ChronologyDate result = registry.fromIsoDate(isoDate, "HIJRI");

    assertEquals("HIJRI", result.chronologyId());
    // Should be around Hijri 1446
    assertTrue(result.year() >= 1445 && result.year() <= 1446);
  }

  @Test
  void convert_isoToHijri_works() {
    ChronologyDate isoDate = new ChronologyDate("ISO", 2024, 7, 7);
    ChronologyDate hijriDate = registry.convert(isoDate, "HIJRI");

    assertEquals("HIJRI", hijriDate.chronologyId());
    assertTrue(hijriDate.year() >= 1445);
  }

  @Test
  void convert_sameChronology_returnsSameDate() {
    ChronologyDate original = new ChronologyDate("ISO", 2025, 6, 15);
    ChronologyDate result = registry.convert(original, "ISO");

    assertSame(original, result);
  }

  @Test
  void getYearRange_iso_returnsCorrectRange() {
    LocalDate start = LocalDate.of(2024, 1, 1);
    LocalDate end = LocalDate.of(2025, 12, 31);
    int[] range = registry.getYearRange(start, end, "ISO");

    assertEquals(2024, range[0]);
    assertEquals(2025, range[1]);
  }

  @Test
  void getYearRange_hijri_spansMultipleYears() {
    // 2024-01-01 to 2024-12-31 spans Hijri years 1445-1446
    LocalDate start = LocalDate.of(2024, 1, 1);
    LocalDate end = LocalDate.of(2024, 12, 31);
    int[] range = registry.getYearRange(start, end, "HIJRI");

    assertTrue(range[0] <= 1445);
    assertTrue(range[1] >= 1446);
  }

  @Test
  void isValidDate_validIsoDate_returnsTrue() {
    assertTrue(registry.isValidDate(2025, 6, 15, "ISO"));
    assertTrue(registry.isValidDate(2024, 2, 29, "ISO")); // Leap year
  }

  @Test
  void isValidDate_invalidIsoDate_returnsFalse() {
    assertFalse(registry.isValidDate(2025, 2, 29, "ISO")); // Not a leap year
    assertFalse(registry.isValidDate(2025, 13, 1, "ISO")); // Invalid month
  }

  @Test
  void roundTrip_jdn_preservesDate() {
    ChronologyDate original = new ChronologyDate("ISO", 2025, 6, 15);
    long jdn = registry.toJdn(original);
    ChronologyDate result = registry.fromJdn(jdn, "ISO");

    assertEquals(original.year(), result.year());
    assertEquals(original.month(), result.month());
    assertEquals(original.day(), result.day());
  }

  @Test
  void getRegisteredChronologies_returnsAllBuiltIn() {
    var chronologies = registry.getRegisteredChronologies();

    assertTrue(chronologies.contains("ISO"));
    assertTrue(chronologies.contains("HIJRI"));
    assertTrue(chronologies.contains("JULIAN"));
    assertTrue(chronologies.size() >= 3);
  }

  @Test
  void reset_restoresBuiltInAlgorithms() {
    // Register a custom algorithm
    registry.register(
        new IsoAlgorithm() {
          @Override
          public String getChronologyId() {
            return "CUSTOM";
          }
        });
    assertTrue(registry.hasChronology("CUSTOM"));

    // Reset should remove custom and keep built-in
    registry.reset();
    assertFalse(registry.hasChronology("CUSTOM"));
    assertTrue(registry.hasChronology("ISO"));
    assertTrue(registry.hasChronology("HIJRI"));
    assertTrue(registry.hasChronology("JULIAN"));
  }
}
