package com.bdc.emitter;

import com.bdc.chronology.ontology.ChronologyDate;
import com.bdc.chronology.ontology.ChronologyRegistry;
import com.bdc.model.Event;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Writes events as CSV.
 *
 * <p>Columns: {@code date[,<chronology>_date],type,description,key,source_module,observed_from,
 * close_time,status}. The first three columns are stable; consumers should address columns by
 * header name.
 */
public class CsvEmitter {

  public static final List<String> BASE_COLUMNS = List.of("date", "type", "description");
  public static final List<String> EXTRA_COLUMNS =
      List.of("key", "source_module", "observed_from", "close_time", "status");

  private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

  public void emit(List<Event> events, Path outputPath) throws IOException {
    emit(events, outputPath, null);
  }

  public void emit(List<Event> events, Path outputPath, String outputChronology)
      throws IOException {
    Path parent = outputPath.getParent();
    if (parent != null) {
      Files.createDirectories(parent);
    }

    try (BufferedWriter writer = Files.newBufferedWriter(outputPath)) {
      writer.write(getHeader(outputChronology));
      writer.newLine();

      for (Event event : events) {
        writer.write(formatRow(event, outputChronology));
        writer.newLine();
      }
    }
  }

  public String emitToString(List<Event> events) {
    return emitToString(events, null);
  }

  public String emitToString(List<Event> events, String outputChronology) {
    StringBuilder sb = new StringBuilder();
    sb.append(getHeader(outputChronology)).append("\n");

    for (Event event : events) {
      sb.append(formatRow(event, outputChronology)).append("\n");
    }

    return sb.toString();
  }

  private String getHeader(String outputChronology) {
    List<String> columns = new ArrayList<>();
    columns.add("date");
    if (outputChronology != null) {
      columns.add(outputChronology.toLowerCase() + "_date");
    }
    columns.add("type");
    columns.add("description");
    columns.addAll(EXTRA_COLUMNS);
    return String.join(",", columns);
  }

  private String formatRow(Event event, String outputChronology) {
    List<String> cells = new ArrayList<>();
    cells.add(event.date().toString());
    if (outputChronology != null) {
      String altDateStr;
      try {
        ChronologyDate altDate =
            ChronologyRegistry.getInstance().fromIsoDate(event.date(), outputChronology);
        altDateStr =
            String.format("%04d-%02d-%02d", altDate.year(), altDate.month(), altDate.day());
      } catch (IllegalArgumentException e) {
        altDateStr = "";
      }
      cells.add(altDateStr);
    }
    cells.add(event.type().name());
    cells.add(escapeCsv(event.description()));
    cells.add(escapeCsv(event.key()));
    cells.add(escapeCsv(event.sourceModule()));
    cells.add(event.observedFrom() != null ? event.observedFrom().toString() : "");
    cells.add(event.closeTime() != null ? TIME.format(event.closeTime()) : "");
    cells.add(event.status() != null ? event.status().name() : "");
    return String.join(",", cells);
  }

  static String escapeCsv(String value) {
    if (value == null) {
      return "";
    }
    if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
      return "\"" + value.replace("\"", "\"\"") + "\"";
    }
    return value;
  }
}
