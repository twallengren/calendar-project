package com.bdc.classifier;

import static org.junit.jupiter.api.Assertions.*;

import com.bdc.model.*;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ClassifierTest {

  private OccurrenceClassifier classifier;

  @BeforeEach
  void setUp() {
    classifier = new OccurrenceClassifier();
  }

  @Test
  void classifyWithExplicitClassification() {
    Occurrence occ = new Occurrence("diwali", LocalDate.of(2024, 11, 1), "Diwali", "test");

    ResolvedSpec spec =
        new ResolvedSpec(
            "test",
            null,
            WeekendPolicy.SAT_SUN,
            List.of(),
            List.of(),
            Map.of("diwali", EventType.NOTABLE),
            List.of(),
            List.of());

    List<Event> events = classifier.classify(List.of(occ), spec);

    assertEquals(1, events.size());
    assertEquals(EventType.NOTABLE, events.get(0).type());
  }

  @Test
  void classifyWithSourceDefault() {
    Occurrence occ =
        new Occurrence("christmas", LocalDate.of(2024, 12, 25), "Christmas Day", "test");

    EventSource source = new EventSource("christmas", "Christmas Day", null, EventType.CLOSED);

    ResolvedSpec spec =
        new ResolvedSpec(
            "test",
            null,
            WeekendPolicy.SAT_SUN,
            List.of(),
            List.of(source),
            Map.of(),
            List.of(),
            List.of());

    List<Event> events = classifier.classify(List.of(occ), spec);

    assertEquals(1, events.size());
    assertEquals(EventType.CLOSED, events.get(0).type());
  }

  @Test
  void classifyWithDeltaReclassification() {
    Occurrence occ =
        new Occurrence("christmas", LocalDate.of(2024, 12, 25), "Christmas Day", "test");

    Delta.Reclassify delta =
        new Delta.Reclassify("christmas", LocalDate.of(2024, 12, 25), EventType.PERIOD_MARKER);

    ResolvedSpec spec =
        new ResolvedSpec(
            "test",
            null,
            WeekendPolicy.SAT_SUN,
            List.of(),
            List.of(),
            Map.of("christmas", EventType.CLOSED),
            List.of(delta),
            List.of());

    List<Event> events = classifier.classify(List.of(occ), spec);

    assertEquals(1, events.size());
    assertEquals(EventType.PERIOD_MARKER, events.get(0).type());
  }
}
