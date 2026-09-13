package com.bdc.model;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class CalendarSpecMetadataTest {

  @Test
  void kind_defaultsToMarket() {
    CalendarSpec.Metadata metadata = new CalendarSpec.Metadata("N", null, "ISO", null, null, null);
    assertEquals("market", metadata.kind());
    assertEquals("market", new CalendarSpec.Metadata("N", null, "ISO").kind());
  }

  @Test
  void kind_baseIsAccepted() {
    assertEquals("base", new CalendarSpec.Metadata("N", null, "ISO", null, null, "base").kind());
  }

  @Test
  void kind_unknownValueIsRejected() {
    IllegalArgumentException e =
        assertThrows(
            IllegalArgumentException.class,
            () -> new CalendarSpec.Metadata("N", null, "ISO", null, null, "exchange"));
    assertTrue(e.getMessage().contains("metadata.kind"), e.getMessage());
  }
}
