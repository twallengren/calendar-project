package com.bdc.emitter;

import com.bdc.model.Event;
import com.bdc.model.EventStatus;
import com.bdc.model.EventType;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads an events CSV written by {@link CsvEmitter} (any version) or a reference CSV of the same
 * shape.
 *
 * <p>Columns are addressed by header name. {@code date} and {@code type} are required; {@code
 * description} defaults to empty; {@code key}, {@code source_module}, {@code observed_from}, {@code
 * close_time} and {@code status} are optional. Lines starting with {@code #} are ignored so that
 * reference files can carry a provenance header. Quoted fields with embedded commas and doubled
 * quotes are supported.
 */
public class EventsCsvReader {

  public List<Event> read(Path path) throws IOException {
    return parse(Files.readAllLines(path), "blessed");
  }

  public List<Event> read(Path path, String provenance) throws IOException {
    return parse(Files.readAllLines(path), provenance);
  }

  public List<Event> parse(List<String> lines, String provenance) {
    List<Event> events = new ArrayList<>();
    Map<String, Integer> columns = null;
    int lineNo = 0;
    for (String raw : lines) {
      lineNo++;
      String line = raw.strip();
      if (line.isEmpty() || line.startsWith("#")) {
        continue;
      }
      List<String> cells = splitCsv(line);
      if (columns == null) {
        columns = new HashMap<>();
        for (int i = 0; i < cells.size(); i++) {
          columns.put(cells.get(i).strip().toLowerCase(), i);
        }
        if (!columns.containsKey("date") || !columns.containsKey("type")) {
          throw new IllegalArgumentException(
              "events CSV header must contain 'date' and 'type' columns, got: " + line);
        }
        continue;
      }
      try {
        LocalDate date = LocalDate.parse(cell(cells, columns, "date"));
        EventType type = EventType.valueOf(cell(cells, columns, "type").strip());
        String description = cell(cells, columns, "description");
        String key = blankToNull(cell(cells, columns, "key"));
        String sourceModule = blankToNull(cell(cells, columns, "source_module"));
        String observedFrom = blankToNull(cell(cells, columns, "observed_from"));
        String closeTime = blankToNull(cell(cells, columns, "close_time"));
        String status = blankToNull(cell(cells, columns, "status"));
        events.add(
            new Event(
                date,
                type,
                description,
                provenance,
                key,
                sourceModule,
                observedFrom != null ? LocalDate.parse(observedFrom) : null,
                closeTime != null ? LocalTime.parse(closeTime) : null,
                status != null ? EventStatus.valueOf(status) : null));
      } catch (RuntimeException e) {
        throw new IllegalArgumentException(
            "Malformed events CSV at line " + lineNo + ": " + raw + " (" + e.getMessage() + ")", e);
      }
    }
    return events;
  }

  private static String cell(List<String> cells, Map<String, Integer> columns, String name) {
    Integer idx = columns.get(name);
    if (idx == null || idx >= cells.size()) {
      return "";
    }
    return cells.get(idx);
  }

  private static String blankToNull(String s) {
    return s == null || s.isBlank() ? null : s.strip();
  }

  /** Splits one CSV line, honouring double-quoted fields. */
  static List<String> splitCsv(String line) {
    List<String> cells = new ArrayList<>();
    StringBuilder current = new StringBuilder();
    boolean inQuotes = false;
    for (int i = 0; i < line.length(); i++) {
      char c = line.charAt(i);
      if (inQuotes) {
        if (c == '"') {
          if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
            current.append('"');
            i++;
          } else {
            inQuotes = false;
          }
        } else {
          current.append(c);
        }
      } else if (c == '"') {
        inQuotes = true;
      } else if (c == ',') {
        cells.add(current.toString());
        current.setLength(0);
      } else {
        current.append(c);
      }
    }
    cells.add(current.toString());
    return cells;
  }
}
