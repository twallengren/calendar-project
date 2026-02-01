package com.bdc.emitter;

import static org.junit.jupiter.api.Assertions.*;

import com.bdc.model.Event;
import com.bdc.model.EventType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CsvEmitterTest {

  private CsvEmitter emitter;

  @TempDir Path tempDir;

  @BeforeEach
  void setUp() {
    emitter = new CsvEmitter();
  }

  @Test
  void emit_singleEvent_writesCorrectCsv() throws Exception {
    Event event = new Event(LocalDate.of(2025, 1, 1), EventType.CLOSED, "New Year's Day", "test");
    Path outputPath = tempDir.resolve("events.csv");

    emitter.emit(List.of(event), outputPath);

    String content = Files.readString(outputPath);
    assertTrue(content.contains("date,type,description"));
    assertTrue(content.contains("2025-01-01,CLOSED,New Year's Day"));
  }

  @Test
  void emit_multipleEvents_writesAllRows() throws Exception {
    List<Event> events =
        List.of(
            new Event(LocalDate.of(2025, 1, 1), EventType.CLOSED, "New Year's Day", "test"),
            new Event(LocalDate.of(2025, 12, 25), EventType.CLOSED, "Christmas", "test"),
            new Event(LocalDate.of(2025, 7, 4), EventType.CLOSED, "Independence Day", "test"));
    Path outputPath = tempDir.resolve("events.csv");

    emitter.emit(events, outputPath);

    List<String> lines = Files.readAllLines(outputPath);
    assertEquals(4, lines.size()); // header + 3 events
    assertTrue(lines.get(0).contains("date,type,description"));
  }

  @Test
  void emit_emptyList_writesHeaderOnly() throws Exception {
    Path outputPath = tempDir.resolve("empty.csv");

    emitter.emit(List.of(), outputPath);

    List<String> lines = Files.readAllLines(outputPath);
    assertEquals(1, lines.size());
    assertEquals("date,type,description", lines.get(0));
  }

  @Test
  void emitToString_returnsCorrectFormat() {
    Event event = new Event(LocalDate.of(2025, 1, 1), EventType.CLOSED, "Test Holiday", "test");

    String result = emitter.emitToString(List.of(event));

    assertTrue(result.startsWith("date,type,description\n"));
    assertTrue(result.contains("2025-01-01,CLOSED,Test Holiday"));
  }

  @Test
  void emit_descriptionWithComma_escapesCorrectly() throws Exception {
    Event event =
        new Event(LocalDate.of(2025, 1, 1), EventType.CLOSED, "Holiday, with comma", "test");
    Path outputPath = tempDir.resolve("comma.csv");

    emitter.emit(List.of(event), outputPath);

    String content = Files.readString(outputPath);
    assertTrue(content.contains("\"Holiday, with comma\""));
  }

  @Test
  void emit_descriptionWithQuotes_escapesCorrectly() throws Exception {
    Event event =
        new Event(LocalDate.of(2025, 1, 1), EventType.CLOSED, "Holiday \"special\"", "test");
    Path outputPath = tempDir.resolve("quotes.csv");

    emitter.emit(List.of(event), outputPath);

    String content = Files.readString(outputPath);
    assertTrue(content.contains("\"Holiday \"\"special\"\"\""));
  }

  @Test
  void emit_descriptionWithNewline_escapesCorrectly() throws Exception {
    Event event =
        new Event(LocalDate.of(2025, 1, 1), EventType.CLOSED, "Holiday\nwith newline", "test");
    Path outputPath = tempDir.resolve("newline.csv");

    emitter.emit(List.of(event), outputPath);

    String content = Files.readString(outputPath);
    assertTrue(content.contains("\"Holiday\nwith newline\""));
  }

  @Test
  void emit_createsParentDirectories() throws Exception {
    Event event = new Event(LocalDate.of(2025, 1, 1), EventType.CLOSED, "Test", "test");
    Path outputPath = tempDir.resolve("nested/dir/events.csv");

    emitter.emit(List.of(event), outputPath);

    assertTrue(Files.exists(outputPath));
  }

  @Test
  void emit_allEventTypes_formatsCorrectly() throws Exception {
    List<Event> events =
        List.of(
            new Event(LocalDate.of(2025, 1, 1), EventType.CLOSED, "Closed", "test"),
            new Event(LocalDate.of(2025, 1, 2), EventType.EARLY_CLOSE, "Early Close", "test"),
            new Event(LocalDate.of(2025, 1, 3), EventType.NOTABLE, "Notable", "test"),
            new Event(LocalDate.of(2025, 1, 4), EventType.PERIOD_MARKER, "Period Marker", "test"),
            new Event(LocalDate.of(2025, 1, 5), EventType.WEEKEND, "Weekend", "test"));
    Path outputPath = tempDir.resolve("all-types.csv");

    emitter.emit(events, outputPath);

    String content = Files.readString(outputPath);
    assertTrue(content.contains("CLOSED"));
    assertTrue(content.contains("EARLY_CLOSE"));
    assertTrue(content.contains("NOTABLE"));
    assertTrue(content.contains("PERIOD_MARKER"));
    assertTrue(content.contains("WEEKEND"));
  }

  @Test
  void emitToString_multipleEvents_correctNewlines() {
    List<Event> events =
        List.of(
            new Event(LocalDate.of(2025, 1, 1), EventType.CLOSED, "Event1", "test"),
            new Event(LocalDate.of(2025, 1, 2), EventType.CLOSED, "Event2", "test"));

    String result = emitter.emitToString(events);
    String[] lines = result.split("\n");

    assertEquals(3, lines.length); // header + 2 events
  }
}
