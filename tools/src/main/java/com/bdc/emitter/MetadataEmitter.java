package com.bdc.emitter;

import com.bdc.model.Event;
import com.bdc.model.ResolvedSpec;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MetadataEmitter {

  private final ObjectMapper mapper;

  public MetadataEmitter() {
    this.mapper =
        new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .enable(SerializationFeature.INDENT_OUTPUT)
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
  }

  public void emit(
      ResolvedSpec spec, List<Event> events, LocalDate from, LocalDate to, Path outputPath)
      throws IOException {
    emit(spec, events, from, to, outputPath, null, null);
  }

  public void emit(
      ResolvedSpec spec,
      List<Event> events,
      LocalDate from,
      LocalDate to,
      Path outputPath,
      String gitSha,
      String releaseVersion)
      throws IOException {
    Path parent = outputPath.getParent();
    if (parent != null) {
      Files.createDirectories(parent);
    }

    Map<String, Object> metadata = new LinkedHashMap<>();
    metadata.put("calendar_id", spec.id());
    metadata.put("calendar_name", spec.metadata() != null ? spec.metadata().name() : spec.id());
    metadata.put("generated_at", Instant.now().toString());
    metadata.put("range_start", from.toString());
    metadata.put("range_end", to.toString());
    metadata.put("event_count", events.size());
    metadata.put("resolution_chain", spec.resolutionChain());

    Map<String, Long> countsByType = new LinkedHashMap<>();
    for (Event event : events) {
      countsByType.merge(event.type().name(), 1L, Long::sum);
    }
    metadata.put("counts_by_type", countsByType);

    if (gitSha != null || releaseVersion != null) {
      Map<String, String> sourceVersion = new LinkedHashMap<>();
      if (releaseVersion != null) {
        sourceVersion.put("semantic", releaseVersion);
      }
      if (gitSha != null) {
        sourceVersion.put("git_sha", gitSha);
      }
      metadata.put("source_version", sourceVersion);
    }

    mapper.writeValue(outputPath.toFile(), metadata);
  }

  public String emitToString(ResolvedSpec spec, List<Event> events, LocalDate from, LocalDate to)
      throws IOException {
    Map<String, Object> metadata = new LinkedHashMap<>();
    metadata.put("calendar_id", spec.id());
    metadata.put("calendar_name", spec.metadata() != null ? spec.metadata().name() : spec.id());
    metadata.put("generated_at", Instant.now().toString());
    metadata.put("range_start", from.toString());
    metadata.put("range_end", to.toString());
    metadata.put("event_count", events.size());
    metadata.put("resolution_chain", spec.resolutionChain());

    Map<String, Long> countsByType = new LinkedHashMap<>();
    for (Event event : events) {
      countsByType.merge(event.type().name(), 1L, Long::sum);
    }
    metadata.put("counts_by_type", countsByType);

    return mapper.writeValueAsString(metadata);
  }
}
