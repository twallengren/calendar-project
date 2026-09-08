package com.bdc.csv;

import com.bdc.model.EventType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.dataformat.csv.CsvFactory;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvParser;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import java.io.IOException;
import java.io.PushbackReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/** Reads logical CSV records without discarding columns, field whitespace, or duplicate rows. */
public final class EventCsvReader {
  private final ObjectWriter writer =
      new CsvMapper().writer(CsvSchema.emptySchema().withLineSeparator(""));
  private final CsvFactory factory =
      CsvFactory.builder()
          .enable(CsvParser.Feature.SKIP_EMPTY_LINES)
          .disable(CsvParser.Feature.TRIM_SPACES)
          .disable(CsvParser.Feature.EMPTY_STRING_AS_NULL)
          .disable(CsvParser.Feature.ALLOW_TRAILING_COMMA)
          .build();

  public record Table(List<String> header, List<List<String>> records) {
    public Table {
      header = List.copyOf(header);
      records = records.stream().map(List::copyOf).toList();
    }
  }

  public Table read(Path path) throws IOException {
    int recordNumber = 1; // Header is logical record 1; skipped blank lines do not count.
    try (PushbackReader input =
        new PushbackReader(Files.newBufferedReader(path, StandardCharsets.UTF_8))) {
      int first = input.read();
      if (first != -1 && first != '\uFEFF') input.unread(first);
      try (CsvParser parser = factory.createParser(input)) {
        parser.setSchema(CsvSchema.emptySchema());
        List<String> header = null;
        List<List<String>> records = new ArrayList<>();
        while (parser.nextToken() != null) {
          if (parser.currentToken() != JsonToken.START_ARRAY) {
            throw invalid(path, recordNumber, "expected CSV record");
          }
          List<String> fields = new ArrayList<>();
          while (parser.nextToken() != JsonToken.END_ARRAY) {
            if (parser.currentToken() != JsonToken.VALUE_STRING) {
              throw invalid(path, recordNumber, "expected CSV field");
            }
            fields.add(parser.getText());
          }
          if (header == null) {
            if (new HashSet<>(fields).size() != fields.size() || fields.contains("")) {
              throw invalid(path, recordNumber, "header names must be nonempty and unique");
            }
            if (!fields.containsAll(List.of("date", "type", "description"))) {
              throw invalid(path, recordNumber, "header must contain date, type, and description");
            }
            header = List.copyOf(fields);
          } else {
            if (fields.size() != header.size()) {
              throw invalid(
                  path,
                  recordNumber,
                  "expected " + header.size() + " fields, found " + fields.size());
            }
            String date = fields.get(header.indexOf("date"));
            String type = fields.get(header.indexOf("type"));
            try {
              LocalDate.parse(date);
            } catch (DateTimeException e) {
              throw invalid(path, recordNumber, "invalid ISO date: " + date);
            }
            try {
              EventType.valueOf(type);
            } catch (IllegalArgumentException e) {
              throw invalid(path, recordNumber, "unknown event type: " + type);
            }
            records.add(fields);
          }
          recordNumber++;
        }
        if (header == null) throw invalid(path, 1, "missing CSV header");
        return new Table(header, records);
      }
    } catch (JsonProcessingException e) {
      throw new IOException(
          path + ": CSV record " + recordNumber + ": " + e.getOriginalMessage(), e);
    }
  }

  /** Canonical quoting for presenting a decoded record, without a trailing record separator. */
  public String formatRecord(List<String> fields) throws IOException {
    return writer.writeValueAsString(fields);
  }

  private IOException invalid(Path path, int record, String message) {
    return new IOException(path + ": CSV record " + record + ": " + message);
  }
}
