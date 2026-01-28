package com.bdc.emitter;

import com.bdc.model.Event;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class CsvEmitter {

  private static final String HEADER = "date,type,description";

  public void emit(List<Event> events, Path outputPath) throws IOException {
    Files.createDirectories(outputPath.getParent());

    try (BufferedWriter writer = Files.newBufferedWriter(outputPath)) {
      writer.write(HEADER);
      writer.newLine();

      for (Event event : events) {
        writer.write(formatRow(event));
        writer.newLine();
      }
    }
  }

  public String emitToString(List<Event> events) {
    StringBuilder sb = new StringBuilder();
    sb.append(HEADER).append("\n");

    for (Event event : events) {
      sb.append(formatRow(event)).append("\n");
    }

    return sb.toString();
  }

  private String formatRow(Event event) {
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
