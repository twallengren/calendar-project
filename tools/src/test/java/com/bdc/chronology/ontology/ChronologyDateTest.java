package com.bdc.chronology.ontology;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ChronologyDateTest {

  @BeforeEach
  void setUp() {
    ChronologyRegistry.getInstance().reset();
  }

  @Test
  void constructor_validDate_succeeds() {
    ChronologyDate date = new ChronologyDate("ISO", 2025, 6, 15);

    assertEquals("ISO", date.chronologyId());
    assertEquals(2025, date.year());
    assertEquals(6, date.month());
    assertEquals(15, date.day());
  }

  @Test
  void constructor_nullChronologyId_throwsException() {
    assertThrows(NullPointerException.class, () -> new ChronologyDate(null, 2025, 6, 15));
  }

  @Test
  void constructor_invalidMonth_throwsException() {
    assertThrows(IllegalArgumentException.class, () -> new ChronologyDate("ISO", 2025, 0, 15));
    assertThrows(IllegalArgumentException.class, () -> new ChronologyDate("ISO", 2025, 14, 15));
  }

  @Test
  void constructor_invalidDay_throwsException() {
    assertThrows(IllegalArgumentException.class, () -> new ChronologyDate("ISO", 2025, 6, 0));
    assertThrows(IllegalArgumentException.class, () -> new ChronologyDate("ISO", 2025, 6, 32));
  }

  @Test
  void iso_factoryMethod_works() {
    ChronologyDate date = ChronologyDate.iso(2025, 6, 15);

    assertEquals("ISO", date.chronologyId());
    assertEquals(2025, date.year());
    assertEquals(6, date.month());
    assertEquals(15, date.day());
  }

  @Test
  void toJdn_isoDate_returnsCorrectJdn() {
    ChronologyDate date = ChronologyDate.iso(2000, 1, 1);
    assertEquals(2451545L, date.toJdn());
  }

  @Test
  void fromJdn_knownJdn_returnsCorrectDate() {
    ChronologyDate date = ChronologyDate.fromJdn(2451545L, "ISO");

    assertEquals("ISO", date.chronologyId());
    assertEquals(2000, date.year());
    assertEquals(1, date.month());
    assertEquals(1, date.day());
  }

  @Test
  void toIsoDate_isoChronology_returnsCorrectLocalDate() {
    ChronologyDate date = ChronologyDate.iso(2025, 6, 15);
    LocalDate result = date.toIsoDate();

    assertEquals(LocalDate.of(2025, 6, 15), result);
  }

  @Test
  void fromIsoDate_works() {
    LocalDate isoDate = LocalDate.of(2025, 6, 15);
    ChronologyDate date = ChronologyDate.fromIsoDate(isoDate, "ISO");

    assertEquals("ISO", date.chronologyId());
    assertEquals(2025, date.year());
    assertEquals(6, date.month());
    assertEquals(15, date.day());
  }

  @Test
  void toChronology_sameChronology_returnsSame() {
    ChronologyDate original = ChronologyDate.iso(2025, 6, 15);
    ChronologyDate result = original.toChronology("ISO");

    assertSame(original, result);
  }

  @Test
  void toChronology_differentChronology_converts() {
    ChronologyDate isoDate = ChronologyDate.iso(2024, 7, 7);
    ChronologyDate hijriDate = isoDate.toChronology("HIJRI");

    assertEquals("HIJRI", hijriDate.chronologyId());
    // Verify by converting back
    ChronologyDate backToIso = hijriDate.toChronology("ISO");
    assertEquals(isoDate.year(), backToIso.year());
    assertEquals(isoDate.month(), backToIso.month());
    assertEquals(isoDate.day(), backToIso.day());
  }

  @Test
  void roundTrip_isoToHijriToIso_preservesDate() {
    ChronologyDate original = ChronologyDate.iso(2025, 6, 15);
    ChronologyDate hijri = original.toChronology("HIJRI");
    ChronologyDate roundTripped = hijri.toChronology("ISO");

    assertEquals(original.year(), roundTripped.year());
    assertEquals(original.month(), roundTripped.month());
    assertEquals(original.day(), roundTripped.day());
  }

  @Test
  void roundTrip_localDateToChronologyDateAndBack_preserves() {
    LocalDate original = LocalDate.of(2025, 6, 15);
    ChronologyDate chrDate = ChronologyDate.fromIsoDate(original, "ISO");
    LocalDate result = chrDate.toIsoDate();

    assertEquals(original, result);
  }

  @Test
  void toString_formatsCorrectly() {
    ChronologyDate date = ChronologyDate.iso(2025, 6, 15);
    assertEquals("ISO[2025-06-15]", date.toString());

    ChronologyDate hijri = new ChronologyDate("HIJRI", 1446, 1, 1);
    assertEquals("HIJRI[1446-01-01]", hijri.toString());
  }

  @Test
  void equals_sameValues_equal() {
    ChronologyDate date1 = ChronologyDate.iso(2025, 6, 15);
    ChronologyDate date2 = ChronologyDate.iso(2025, 6, 15);

    assertEquals(date1, date2);
    assertEquals(date1.hashCode(), date2.hashCode());
  }

  @Test
  void equals_differentValues_notEqual() {
    ChronologyDate date1 = ChronologyDate.iso(2025, 6, 15);
    ChronologyDate date2 = ChronologyDate.iso(2025, 6, 16);

    assertNotEquals(date1, date2);
  }
}
