package com.bdc.emitter;

import com.bdc.model.CalendarSpec;
import com.bdc.model.ResolvedSpec;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SpecEmitter {

  private final ObjectMapper yamlMapper;

  public SpecEmitter() {
    YAMLFactory yamlFactory =
        new YAMLFactory()
            .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
            .enable(YAMLGenerator.Feature.MINIMIZE_QUOTES);
    this.yamlMapper =
        new ObjectMapper(yamlFactory)
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
  }

  public void emitCalendarSpec(CalendarSpec spec, Path outputPath) throws IOException {
    Path parent = outputPath.getParent();
    if (parent != null) {
      Files.createDirectories(parent);
    }
    yamlMapper.writeValue(outputPath.toFile(), spec);
  }

  public void emitResolvedSpec(ResolvedSpec spec, Path outputPath) throws IOException {
    Path parent = outputPath.getParent();
    if (parent != null) {
      Files.createDirectories(parent);
    }

    Map<String, Object> resolved = new LinkedHashMap<>();
    resolved.put("kind", "resolved_calendar");
    resolved.put("id", spec.id());

    if (spec.metadata() != null) {
      Map<String, Object> metadata = new LinkedHashMap<>();
      metadata.put("name", spec.metadata().name());
      metadata.put("description", spec.metadata().description());
      metadata.put("chronology", spec.metadata().chronology());
      resolved.put("metadata", metadata);
    }

    if (spec.weekendPolicy() != null && !spec.weekendPolicy().weekendDays().isEmpty()) {
      Map<String, Object> weekendPolicy = new LinkedHashMap<>();
      weekendPolicy.put(
          "days", spec.weekendPolicy().weekendDays().stream().map(Enum::name).toList());
      resolved.put("weekend_policy", weekendPolicy);
    }

    resolved.put("resolution_chain", spec.resolutionChain());

    if (!spec.references().isEmpty()) {
      List<Map<String, Object>> refs = new ArrayList<>();
      for (var ref : spec.references()) {
        Map<String, Object> refMap = new LinkedHashMap<>();
        refMap.put("key", ref.key());
        refMap.put("formula", ref.formula());
        refs.add(refMap);
      }
      resolved.put("references", refs);
    }

    if (!spec.eventSources().isEmpty()) {
      List<Map<String, Object>> sources = new ArrayList<>();
      for (var source : spec.eventSources()) {
        Map<String, Object> sourceMap = new LinkedHashMap<>();
        sourceMap.put("key", source.key());
        sourceMap.put("name", source.name());
        if (source.defaultClassification() != null) {
          sourceMap.put("classification", source.defaultClassification().name());
        }
        if (source.activeYears() != null && !source.activeYears().isEmpty()) {
          List<Object> yearsOutput = new ArrayList<>();
          for (var range : source.activeYears()) {
            if (range.start() != null && range.end() != null && range.start().equals(range.end())) {
              // Single year
              yearsOutput.add(range.start());
            } else {
              // Range - use Arrays.asList to allow nulls
              yearsOutput.add(Arrays.asList(range.start(), range.end()));
            }
          }
          sourceMap.put("active_years", yearsOutput);
        }
        if (source.rule() != null) {
          sourceMap.put("rule", ruleToMap(source.rule()));
        }
        sources.add(sourceMap);
      }
      resolved.put("event_sources", sources);
    }

    if (!spec.deltas().isEmpty()) {
      List<Map<String, Object>> deltas = new ArrayList<>();
      for (var delta : spec.deltas()) {
        deltas.add(deltaToMap(delta));
      }
      resolved.put("deltas", deltas);
    }

    yamlMapper.writeValue(outputPath.toFile(), resolved);
  }

  private Map<String, Object> ruleToMap(com.bdc.model.Rule rule) {
    Map<String, Object> map = new LinkedHashMap<>();
    switch (rule) {
      case com.bdc.model.Rule.FixedMonthDay r -> {
        map.put("type", "fixed_month_day");
        map.put("month", r.month());
        map.put("day", r.day());
        if (r.chronology() != null && !"ISO".equals(r.chronology())) {
          map.put("chronology", r.chronology());
        }
      }
      case com.bdc.model.Rule.NthWeekdayOfMonth r -> {
        map.put("type", "nth_weekday_of_month");
        map.put("month", r.month());
        map.put("weekday", r.weekday().name());
        map.put("nth", r.nth());
      }
      case com.bdc.model.Rule.ExplicitDates r -> {
        map.put("type", "explicit_dates");
        map.put(
            "dates",
            r.dates().stream()
                .map(
                    ad -> {
                      if (ad.comment() != null && !ad.comment().isBlank()) {
                        Map<String, Object> dateMap = new LinkedHashMap<>();
                        dateMap.put("date", ad.date().toString());
                        dateMap.put("comment", ad.comment());
                        return dateMap;
                      }
                      return ad.date().toString();
                    })
                .toList());
      }
      case com.bdc.model.Rule.RelativeToReference r -> {
        map.put("type", "relative_to_reference");
        map.put("reference", r.reference());
        map.put("offset_days", r.offsetDays());
      }
    }
    return map;
  }

  private Map<String, Object> deltaToMap(com.bdc.model.Delta delta) {
    Map<String, Object> map = new LinkedHashMap<>();
    switch (delta) {
      case com.bdc.model.Delta.Add d -> {
        map.put("action", "add");
        map.put("key", d.key());
        map.put("name", d.name());
        map.put("date", d.date().toString());
        map.put("classification", d.classification().name());
      }
      case com.bdc.model.Delta.Remove d -> {
        map.put("action", "remove");
        map.put("key", d.key());
        map.put("date", d.date().toString());
      }
      case com.bdc.model.Delta.Reclassify d -> {
        map.put("action", "reclassify");
        map.put("key", d.key());
        map.put("date", d.date().toString());
        map.put("new_classification", d.newClassification().name());
      }
    }
    return map;
  }
}
