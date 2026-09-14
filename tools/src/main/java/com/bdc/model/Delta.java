package com.bdc.model;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.time.LocalDate;
import java.util.List;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "action")
@JsonSubTypes({
  @JsonSubTypes.Type(value = Delta.Add.class, name = "add"),
  @JsonSubTypes.Type(value = Delta.Remove.class, name = "remove"),
  @JsonSubTypes.Type(value = Delta.Reclassify.class, name = "reclassify")
})
public sealed interface Delta permits Delta.Add, Delta.Remove, Delta.Reclassify {

  List<SourceCitation> source();

  record Add(
      String key,
      String name,
      LocalDate date,
      EventType classification,
      List<SourceCitation> source)
      implements Delta {
    public Add {
      source = source == null ? List.of() : List.copyOf(source);
    }

    public Add(String key, String name, LocalDate date, EventType classification) {
      this(key, name, date, classification, List.of());
    }
  }

  record Remove(String key, LocalDate date, List<SourceCitation> source) implements Delta {
    public Remove {
      source = source == null ? List.of() : List.copyOf(source);
    }

    public Remove(String key, LocalDate date) {
      this(key, date, List.of());
    }
  }

  record Reclassify(
      String key, LocalDate date, EventType newClassification, List<SourceCitation> source)
      implements Delta {
    public Reclassify {
      source = source == null ? List.of() : List.copyOf(source);
    }

    public Reclassify(String key, LocalDate date, EventType newClassification) {
      this(key, date, newClassification, List.of());
    }
  }
}
