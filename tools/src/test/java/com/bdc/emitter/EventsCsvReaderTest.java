package com.bdc.emitter;

import static org.junit.jupiter.api.Assertions.*;

import com.bdc.model.Event;
import com.bdc.model.EventStatus;
import com.bdc.model.EventType;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class EventsCsvReaderTest {

  private final EventsCsvReader reader = new EventsCsvReader();

  @Test
  void readsLegacyThreeColumnFormat() {
    List<Event> events =
        reader.parse(
            List.of("date,type,description", "2024-01-01,CLOSED,\"Holiday, with comma\""), "x");
    assertEquals(1, events.size());
    assertEquals("Holiday, with comma", events.get(0).description());
    assertNull(events.get(0).key());
  }

  @Test
  void readsExtendedFormatAndComments() {
    List<Event> events =
        reader.parse(
            List.of(
                "# generated-by: test",
                "date,type,description,key,source_module,observed_from,close_time,status",
                "2021-12-24,CLOSED,Christmas Day,christmas,module:christmas,2021-12-25,,CONFIRMED",
                "2024-12-24,EARLY_CLOSE,Eve,christmas_eve,module:christmas_eve,,13:00,PROJECTED"),
            "x");
    assertEquals(2, events.size());
    assertEquals("christmas", events.get(0).key());
    assertEquals(LocalDate.of(2021, 12, 25), events.get(0).observedFrom());
    assertEquals(LocalTime.of(13, 0), events.get(1).closeTime());
    assertEquals(EventStatus.PROJECTED, events.get(1).status());
    assertEquals(EventType.EARLY_CLOSE, events.get(1).type());
  }

  @Test
  void roundTripsThroughCsvEmitter() {
    Event event =
        new Event(
            LocalDate.of(2025, 7, 3),
            EventType.EARLY_CLOSE,
            "Eve \"quoted\"",
            "p",
            "eve",
            "module:eve",
            null,
            LocalTime.of(13, 0),
            EventStatus.CONFIRMED);
    String csv = new CsvEmitter().emitToString(List.of(event));
    List<Event> back = reader.parse(List.of(csv.split("\n")), "p");
    assertEquals(event, back.get(0));
  }

  @Test
  void rejectsMissingRequiredColumns() {
    assertThrows(
        IllegalArgumentException.class, () -> reader.parse(List.of("foo,bar", "1,2"), "x"));
  }
}
