package com.bdc.emitter;

import com.bdc.model.CalendarSpec;
import com.bdc.model.Delta;
import com.bdc.model.EventSource;
import com.bdc.model.ResolvedSpec;
import com.bdc.model.Rule;
import com.bdc.model.SourceCitation;
import com.bdc.model.WeekendPeriod;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Emits calendar and resolved specs as YAML. The resolved form is a faithful, regenerable input.
 */
public class SpecEmitter {

  private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

  private final ObjectMapper yamlMapper;

  public SpecEmitter() {
    YAMLFactory yamlFactory =
        new YAMLFactory()
            .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
            .disable(YAMLGenerator.Feature.USE_NATIVE_TYPE_ID) // deltas as `action: remove`
            .enable(YAMLGenerator.Feature.MINIMIZE_QUOTES);
    this.yamlMapper =
        new ObjectMapper(yamlFactory)
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);
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
    yamlMapper.writeValue(outputPath.toFile(), resolvedToMap(spec));
  }

  public String resolvedToString(ResolvedSpec spec) throws IOException {
    return yamlMapper.writeValueAsString(resolvedToMap(spec));
  }

  /** Builds the resolved-calendar document. */
  public static Map<String, Object> resolvedToMap(ResolvedSpec spec) {
    Map<String, Object> resolved = new LinkedHashMap<>();
    resolved.put("kind", "resolved_calendar");
    resolved.put("id", spec.id());

    if (spec.metadata() != null) {
      Map<String, Object> metadata = new LinkedHashMap<>();
      metadata.put("name", spec.metadata().name());
      metadata.put("description", spec.metadata().description());
      metadata.put("chronology", spec.metadata().chronology());
      if (spec.metadata().timezone() != null) {
        metadata.put("timezone", spec.metadata().timezone());
      }
      if (spec.metadata().mic() != null) {
        metadata.put("mic", spec.metadata().mic());
      }
      if (spec.metadata().aliases() != null && !spec.metadata().aliases().isEmpty()) {
        metadata.put("aliases", spec.metadata().aliases());
      }
      CalendarSpec.Coverage cov = spec.metadata().coverage();
      if (cov != null) {
        Map<String, Object> covMap = new LinkedHashMap<>();
        if (cov.from() != null) covMap.put("from", cov.from().toString());
        if (cov.to() != null) covMap.put("to", cov.to().toString());
        if (cov.verifiedThrough() != null) {
          covMap.put("verified_through", cov.verifiedThrough().toString());
        }
        metadata.put("coverage", covMap);
      }
      resolved.put("metadata", metadata);
    }

    if (spec.weekendPolicy() != null && !spec.weekendPolicy().weekendDays().isEmpty()) {
      Map<String, Object> weekendPolicy = new LinkedHashMap<>();
      weekendPolicy.put(
          "days", spec.weekendPolicy().weekendDays().stream().map(Enum::name).toList());
      if (spec.weekendPolicy().isEffectiveDated()) {
        List<Map<String, Object>> periods = new ArrayList<>();
        for (WeekendPeriod p : spec.weekendPolicy().periods()) {
          Map<String, Object> pm = new LinkedHashMap<>();
          pm.put("days", p.days().stream().map(Enum::name).toList());
          if (p.from() != null) pm.put("from", p.from().toString());
          if (p.to() != null) pm.put("to", p.to().toString());
          periods.add(pm);
        }
        weekendPolicy.put("periods", periods);
      }
      resolved.put("weekend_policy", weekendPolicy);
    }
    resolved.put("weekend_shift_policy", spec.weekendShiftPolicy().name());

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
        sources.add(eventSourceToMap(source, spec.sourceOrigins().get(source.key())));
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
    return resolved;
  }

  public static Map<String, Object> eventSourceToMap(EventSource source, String origin) {
    Map<String, Object> sourceMap = new LinkedHashMap<>();
    sourceMap.put("key", source.key());
    sourceMap.put("name", source.name());
    if (origin != null) {
      sourceMap.put("origin", origin);
    }
    if (source.defaultClassification() != null) {
      sourceMap.put("classification", source.defaultClassification().name());
    }
    if (source.shiftPolicy() != null) {
      sourceMap.put("shift_policy", source.shiftPolicy().name());
    } else if (Boolean.TRUE.equals(source.shiftable())) {
      sourceMap.put("shiftable", true);
    }
    if (!source.displaces().isEmpty()) {
      sourceMap.put("displaces", source.displaces());
    }
    if (source.onlyIfWeekday() != null) {
      sourceMap.put("only_if_weekday", source.onlyIfWeekday().stream().map(Enum::name).toList());
    }
    if (source.closeTime() != null) {
      sourceMap.put("close_time", TIME.format(source.closeTime()));
    }
    if (source.status() != null) {
      sourceMap.put("status", source.status().name());
    }
    if (source.activeYears() != null && !source.activeYears().isEmpty()) {
      List<Object> yearsOutput = new ArrayList<>();
      for (var range : source.activeYears()) {
        if (range.start() != null && range.end() != null && range.start().equals(range.end())) {
          yearsOutput.add(range.start());
        } else {
          yearsOutput.add(Arrays.asList(range.start(), range.end()));
        }
      }
      sourceMap.put("active_years", yearsOutput);
    }
    if (!source.source().isEmpty()) {
      List<Map<String, Object>> citations = new ArrayList<>();
      for (SourceCitation c : source.source()) {
        Map<String, Object> cm = new LinkedHashMap<>();
        if (c.id() != null) cm.put("id", c.id());
        if (c.title() != null) cm.put("title", c.title());
        if (c.publisher() != null) cm.put("publisher", c.publisher());
        if (c.url() != null) cm.put("url", c.url());
        if (c.file() != null) cm.put("file", c.file());
        if (c.retrieved() != null) cm.put("retrieved", c.retrieved().toString());
        if (c.ref() != null) cm.put("ref", c.ref());
        if (c.note() != null) cm.put("note", c.note());
        citations.add(cm);
      }
      sourceMap.put("source", citations);
    }
    if (source.rule() != null) {
      sourceMap.put("rule", ruleToMap(source.rule()));
    }
    return sourceMap;
  }

  public static Map<String, Object> ruleToMap(Rule rule) {
    Map<String, Object> map = new LinkedHashMap<>();
    switch (rule) {
      case Rule.NativeRecurring r -> {
        map.put("chronology", r.chronology());
        map.put("month_codes", r.monthCodes());
        if (r.nativeYears() != null && !r.nativeYears().isEmpty())
          map.put("native_years", r.nativeYears());
        if (r.spanDays() != 1) map.put("duration_days", r.spanDays());
        switch (r) {
          case Rule.NativeFixedMonthDay n -> {
            map.put("type", "native_fixed_month_day");
            map.put("day", n.day());
          }
          case Rule.NativeNthWeekday n -> {
            map.put("type", "native_nth_weekday");
            map.put("weekday", n.weekday().name());
            map.put("nth", n.nth());
          }
          case Rule.NativeRelativeToReference n -> {
            map.put("type", "native_relative_to_reference");
            map.put("day", n.day());
            map.put("offset_days", n.offsetDays());
          }
        }
      }
      case Rule.NativeExplicitDates r -> {
        map.put("type", "native_explicit_dates");
        map.put(
            "dates",
            r.dates().stream()
                .map(
                    d ->
                        Map.of(
                            "chronology_id",
                            d.chronologyId(),
                            "year",
                            d.year(),
                            "month_code",
                            d.monthCode(),
                            "day",
                            d.day()))
                .toList());
      }
      case Rule.FixedMonthDay r -> {
        map.put("type", "fixed_month_day");
        map.put("month", r.month());
        map.put("day", r.day());
        if (r.chronology() != null && !"ISO".equalsIgnoreCase(r.chronology())) {
          map.put("chronology", r.chronology());
        }
        if (r.hasEndDate()) {
          map.put("end_month", r.endMonth());
          map.put("end_day", r.endDay());
        }
        if (r.durationDays() != null) {
          map.put("duration_days", r.durationDays());
        }
      }
      case Rule.NthWeekdayOfMonth r -> {
        map.put("type", "nth_weekday_of_month");
        map.put("month", r.month());
        map.put("weekday", r.weekday().name());
        map.put("nth", r.nth());
        if (r.durationDays() != null) {
          map.put("duration_days", r.durationDays());
        }
      }
      case Rule.ExplicitDates r -> {
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
                        return (Object) dateMap;
                      }
                      return ad.date().toString();
                    })
                .toList());
      }
      case Rule.RelativeToReference r -> {
        map.put("type", "relative_to_reference");
        if (r.usesNamedReference()) {
          map.put("reference", r.reference());
        }
        if (r.usesFixedReference()) {
          map.put("reference_month", r.referenceMonth());
          map.put("reference_day", r.referenceDay());
        }
        if (r.offsetDays() != null) {
          map.put("offset_days", r.offsetDays());
        }
        if (r.usesWeekdayOffset()) {
          Map<String, Object> wo = new LinkedHashMap<>();
          wo.put("weekday", r.offsetWeekday().weekday().name());
          wo.put("nth", r.offsetWeekday().nth());
          wo.put("direction", r.offsetWeekday().direction().name());
          map.put("offset_weekday", wo);
        }
        if (r.durationDays() != null) {
          map.put("duration_days", r.durationDays());
        }
      }
    }
    return map;
  }

  public static Map<String, Object> deltaToMap(Delta delta) {
    Map<String, Object> map = new LinkedHashMap<>();
    switch (delta) {
      case Delta.Add d -> {
        map.put("action", "add");
        map.put("key", d.key());
        map.put("name", d.name());
        map.put("date", d.date().toString());
        map.put("classification", d.classification().name());
      }
      case Delta.Remove d -> {
        map.put("action", "remove");
        map.put("key", d.key());
        map.put("date", d.date().toString());
      }
      case Delta.Reclassify d -> {
        map.put("action", "reclassify");
        map.put("key", d.key());
        map.put("date", d.date().toString());
        map.put("new_classification", d.newClassification().name());
      }
    }
    return map;
  }
}
