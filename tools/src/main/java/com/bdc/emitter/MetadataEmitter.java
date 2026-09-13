package com.bdc.emitter;

import com.bdc.model.CalendarSpec;
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

/**
 * Writes {@code metadata.json}. The generation timestamp can be injected so that a re-blessing run
 * with unchanged specs reproduces byte-identical output.
 */
public class MetadataEmitter {

  private final ObjectMapper mapper;
  private final Instant fixedGeneratedAt;

  public MetadataEmitter() {
    this(null);
  }

  /**
   * @param fixedGeneratedAt timestamp to write as {@code generated_at}, or null for the current
   *     time
   */
  public MetadataEmitter(Instant fixedGeneratedAt) {
    this.fixedGeneratedAt = fixedGeneratedAt;
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
    mapper.writeValue(outputPath.toFile(), build(spec, events, from, to, gitSha, releaseVersion));
  }

  public String emitToString(ResolvedSpec spec, List<Event> events, LocalDate from, LocalDate to)
      throws IOException {
    return mapper.writeValueAsString(build(spec, events, from, to, null, null));
  }

  private Map<String, Object> build(
      ResolvedSpec spec,
      List<Event> events,
      LocalDate from,
      LocalDate to,
      String gitSha,
      String releaseVersion) {
    Map<String, Object> metadata = new LinkedHashMap<>();
    metadata.put("calendar_id", spec.id());
    metadata.put("calendar_name", spec.metadata() != null ? spec.metadata().name() : spec.id());
    metadata.put(
        "kind",
        spec.metadata() != null ? spec.metadata().kind() : CalendarSpec.Metadata.KIND_MARKET);
    metadata.put(
        "generated_at", (fixedGeneratedAt != null ? fixedGeneratedAt : Instant.now()).toString());
    metadata.put("range_start", from.toString());
    metadata.put("range_end", to.toString());
    if (spec.timezone() != null) {
      metadata.put("timezone", spec.timezone());
    }
    CalendarSpec.Coverage coverage = spec.coverage();
    if (coverage != null) {
      Map<String, String> cov = new LinkedHashMap<>();
      if (coverage.from() != null) cov.put("from", coverage.from().toString());
      if (coverage.to() != null) cov.put("to", coverage.to().toString());
      if (coverage.verifiedThrough() != null) {
        cov.put("verified_through", coverage.verifiedThrough().toString());
      }
      metadata.put("coverage", cov);
    }
    metadata.put("event_count", events.size());
    metadata.put("resolution_chain", spec.resolutionChain());

    Map<String, Long> countsByType = new LinkedHashMap<>();
    Map<String, Long> countsByStatus = new LinkedHashMap<>();
    for (Event event : events) {
      countsByType.merge(event.type().name(), 1L, Long::sum);
      if (event.status() != null) {
        countsByStatus.merge(event.status().name(), 1L, Long::sum);
      }
    }
    metadata.put("counts_by_type", countsByType);
    metadata.put("counts_by_status", countsByStatus);

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
    return metadata;
  }
}
