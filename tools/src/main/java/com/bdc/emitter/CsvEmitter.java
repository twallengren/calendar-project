package com.bdc.emitter;

import com.bdc.chronology.ontology.ChronologyDate;
import com.bdc.chronology.ontology.ChronologyRegistry;
import com.bdc.model.Event;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class CsvEmitter {

  private static final String HEADER = "date,type,description";

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
    if (outputChronology != null) {
      return "date," + outputChronology.toLowerCase() + "_date,type,description";
    }
    return HEADER;
  }

  private String formatRow(Event event, String outputChronology) {
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
      return String.format(
          "%s,%s,%s,%s",
          event.date().toString(), altDateStr, event.type().name(), escapeCsv(event.description()));
    }
    return String.format(
        "%s,%s,%s", event.date().toString(), event.type().name(), escapeCsv(event.description()));
  }

  private String escapeCsv(String value) {
    if (value == null) {
      return "";
    }
    if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
      return "\"" + value.replace("\"", "\"\"") + "\"";
    }
    return value;
  }
}
