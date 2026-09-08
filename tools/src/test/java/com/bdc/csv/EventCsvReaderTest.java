package com.bdc.csv;

import static org.junit.jupiter.api.Assertions.*;

import com.bdc.diff.BlessedArtifactLoader;
import com.bdc.emitter.CsvEmitter;
import com.bdc.model.Event;
import com.bdc.model.EventType;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EventCsvReaderTest {
  @TempDir Path temp;
  private final EventCsvReader reader = new EventCsvReader();

  private Path file(String csv) throws IOException {
    Path path = temp.resolve("events.csv");
    Files.writeString(path, csv);
    return path;
  }

  @Test
  void emitterRoundTripPreservesCompleteRecordsAndDuplicates() throws Exception {
    Event event =
        new Event(
            LocalDate.of(2024, 3, 1),
            EventType.CLOSED,
            "  Name, with \"quotes\"\nand\n\nblank lines | & <text>  ",
            "source");
    for (String chronology : new String[] {null, "UMM_AL_QURA"}) {
      String csv = new CsvEmitter().emitToString(List.of(event, event), chronology);
      Path path = file(csv);
      EventCsvReader.Table table = reader.read(path);
      assertEquals(2, table.records().size());
      assertEquals(table.records().getFirst(), table.records().getLast());
      assertEquals(
          event.description(),
          table.records().getFirst().get(table.header().indexOf("description")));
      assertEquals(chronology == null ? 3 : 4, table.header().size());
      List<Event> loaded =
          new BlessedArtifactLoader()
              .loadBlessedEvents(temp.getParent(), temp.getFileName().toString());
      assertEquals(
          List.of(
              new Event(event.date(), event.type(), event.description(), "blessed"),
              new Event(event.date(), event.type(), event.description(), "blessed")),
          loaded);
      String rendered = reader.formatRecord(table.records().getFirst());
      assertEquals(
          table.records().getFirst(),
          reader
              .read(file(String.join(",", table.header()) + "\n" + rendered))
              .records()
              .getFirst());
    }
  }

  @Test
  void supportsBomCrlfBlankLinesEmptyFieldsAndReorderedHeaders() throws Exception {
    var table =
        reader.read(
            file(
                "\uFEFFdescription,date,alternate_date,type\r\n\r\n  \r\n"
                    + "\" A\r\nB \",2024-01-01,,CLOSED\r\n,2024-01-02,,NOTABLE\r\n"));
    assertEquals(List.of("description", "date", "alternate_date", "type"), table.header());
    assertEquals(
        List.of(
            List.of(" A\r\nB ", "2024-01-01", "", "CLOSED"),
            List.of("", "2024-01-02", "", "NOTABLE")),
        table.records());
    assertThrows(UnsupportedOperationException.class, () -> table.records().getFirst().add("x"));
  }

  @Test
  void headerOnlyAndMissingBaselineRemainEmpty() throws Exception {
    assertTrue(reader.read(file("date,type,description\n")).records().isEmpty());
    assertTrue(new BlessedArtifactLoader().loadBlessedEvents(temp, "missing").isEmpty());
  }

  @Test
  void malformedRecordsAreErrorsWithFileAndRecordContext() throws Exception {
    for (String record :
        List.of(
            "2024-01-01,CLOSED",
            "2024-01-01,CLOSED,too,many",
            "2024-01-01,CLOSED,name,",
            "2024-02-30,CLOSED,invalid date",
            "2024-01-01,UNKNOWN,invalid type",
            "2024-01-01,CLOSED,\"unterminated",
            "2024-01-01,CLOSED,\"name\"extra")) {
      Path path = file("date,type,description\n2024-01-01,NOTABLE,\"valid\nmultiline\"\n" + record);
      IOException e = assertThrows(IOException.class, () -> reader.read(path), record);
      assertTrue(e.getMessage().contains(path.toString()), e.getMessage());
      assertTrue(e.getMessage().contains("CSV record 3"), e.getMessage());
    }
    for (String header :
        List.of("", "date,type", "date,type,description,type", "date,type,description,")) {
      Path path = file(header);
      assertTrue(
          assertThrows(IOException.class, () -> reader.read(path))
              .getMessage()
              .contains("CSV record 1"));
    }
  }
}
