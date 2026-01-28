package com.bdc.model;

import java.time.LocalDate;
import java.util.Objects;

public record Occurrence(String key, LocalDate date, String name, String provenance) {
  public Occurrence {
    Objects.requireNonNull(key, "key must not be null");
    Objects.requireNonNull(date, "date must not be null");
    Objects.requireNonNull(name, "name must not be null");
  }
}
