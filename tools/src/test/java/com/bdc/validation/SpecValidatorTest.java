package com.bdc.validation;

import static org.junit.jupiter.api.Assertions.*;

import com.bdc.loader.SpecRegistry;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SpecValidatorTest {

  @TempDir Path dir;

  private ValidationResult validate(String calendar, String modules) throws Exception {
    Path cal = dir.resolve("calendars");
    Path mod = dir.resolve("modules");
    Files.createDirectories(cal);
    Files.createDirectories(mod);
    Files.writeString(cal.resolve("cal.yaml"), calendar);
    int i = 0;
    for (String doc : modules.split("---\n")) {
      if (!doc.isBlank()) Files.writeString(mod.resolve("m" + (i++) + ".yaml"), doc);
    }
    SpecRegistry registry = new SpecRegistry();
    registry.loadCalendarsFromDirectory(cal);
    registry.loadModulesFromDirectory(mod);
    return new SpecValidator(registry).validate("CAL");
  }

  private static List<String> codes(ValidationResult r) {
    return r.issues().stream().map(ValidationIssue::code).toList();
  }

  @Test
  void productionShapedCalendarsAreClean() throws Exception {
    SpecRegistry registry = new SpecRegistry();
    registry.loadCalendarsFromDirectory(Path.of("calendars"));
    registry.loadModulesFromDirectory(Path.of("modules"));
    assertTrue(registry.loadErrors().isEmpty(), registry.loadErrors().toString());
    SpecValidator validator = new SpecValidator(registry);
    for (String id : registry.getAllCalendars().keySet()) {
      ValidationResult r = validator.validate(id);
      assertTrue(r.isClean(), id + ": " + r.issues());
    }
  }

  @Test
  void unknownModuleAndFormulaAndChronology() throws Exception {
    ValidationResult r =
        validate(
            """
            kind: calendar
            id: CAL
            uses: [missing, m]
            """,
            """
            kind: module
            id: m
            references:
              - {key: easter, formula: EASTER_ORTHODOX}
            event_sources:
              - key: a
                name: A
                source: doc
                rule: {type: fixed_month_day, month: 1, day: 1, chronology: MARTIAN}
              - key: b
                name: B
                source: doc
                rule: {type: relative_to_reference, reference: passover, offset_days: 1}
            """);
    List<String> codes = codes(r);
    assertTrue(codes.contains("UNKNOWN_MODULE"), codes.toString());
    assertTrue(codes.contains("UNKNOWN_CHRONOLOGY"), codes.toString());
    assertTrue(codes.contains("UNKNOWN_FORMULA"), codes.toString());
    assertTrue(codes.contains("UNKNOWN_REFERENCE"), codes.toString());
    assertTrue(r.hasErrors());
  }

  @Test
  void invalidRulesAndIdentityMismatch() throws Exception {
    ValidationResult r =
        validate(
            """
            kind: calendar
            id: CAL
            uses: [m]
            classifications: {ghost: NOTABLE}
            deltas:
              - {action: remove, key: nobody, date: 2024-01-01}
            """,
            """
            kind: module
            id: m
            source: doc
            event_sources:
              - key: a
                name: A
                rule: {type: nth_weekday_of_month, key: not_a, name: A, month: 1, weekday: MONDAY, nth: 0}
              - key: b
                name: B
                rule: {type: explicit_dates, dates: [2024-03-01, 2024-01-01, 2024-01-01]}
              - key: c
                name: C
                default_classification: EARLY_CLOSE
                rule: {type: fixed_month_day, month: 13, day: 1}
            """);
    List<String> codes = codes(r);
    assertTrue(codes.contains("RULE_IDENTITY_MISMATCH"), codes.toString());
    assertTrue(codes.contains("INVALID_RULE"), codes.toString());
    assertTrue(codes.contains("UNSORTED_DATES"), codes.toString());
    assertTrue(codes.contains("EARLY_CLOSE_WITHOUT_TIME"), codes.toString());
    assertTrue(codes.contains("DEAD_CLASSIFICATION"), codes.toString());
    assertTrue(codes.contains("DEAD_DELTA"), codes.toString());
    assertTrue(codes.contains("MISSING_COVERAGE"), codes.toString());
  }

  @Test
  void missingSourceAndRedundantUsesAreWarnings() throws Exception {
    ValidationResult r =
        validate(
            """
            kind: calendar
            id: CAL
            metadata:
              name: X
              coverage: {from: 2020-01-01, to: 2030-12-31}
            weekend_shift_policy: NEAREST_WEEKDAY
            uses: [group, leaf]
            """,
            """
            kind: module
            id: group
            uses: [leaf]
            ---
            kind: module
            id: leaf
            event_sources:
              - key: a
                name: A
                rule: {type: fixed_month_day, month: 1, day: 1}
            """);
    List<String> codes = codes(r);
    assertFalse(r.hasErrors(), r.issues().toString());
    assertTrue(codes.contains("MISSING_SOURCE"), codes.toString());
    assertTrue(codes.contains("REDUNDANT_USES"), codes.toString());
  }

  @Test
  void displacesKeysMustExistAndCyclesAreWarned() throws Exception {
    ValidationResult r =
        validate(
            """
            kind: calendar
            id: CAL
            uses: [m]
            """,
            """
            kind: module
            id: m
            source: doc
            event_sources:
              - key: a
                name: A
                displaces: [b, ghost]
                rule: {type: fixed_month_day, month: 12, day: 25}
              - key: b
                name: B
                displaces: [a]
                rule: {type: fixed_month_day, month: 12, day: 26}
            """);
    List<String> codes = codes(r);
    assertTrue(codes.contains("UNKNOWN_DISPLACES"), codes.toString());
    assertTrue(codes.contains("DISPLACES_CYCLE"), codes.toString());
    assertTrue(r.hasErrors(), "an unknown displaces key is an error: " + r.issues());
  }

  @Test
  void dropShiftPolicyOnAClosedSourceIsAnError() throws Exception {
    ValidationResult r =
        validate(
            """
            kind: calendar
            id: CAL
            uses: [m]
            """,
            """
            kind: module
            id: m
            source: doc
            event_sources:
              - key: a
                name: A
                shift_policy: DROP
                rule: {type: fixed_month_day, month: 12, day: 25}
              - key: b
                name: B
                default_classification: EARLY_CLOSE
                close_time: "12:30"
                shift_policy: DROP
                rule: {type: fixed_month_day, month: 12, day: 24}
            """);
    List<String> codes = codes(r);
    assertTrue(codes.contains("INVALID_SHIFT_POLICY"), codes.toString());
    assertEquals(
        1,
        r.errors().stream().filter(i -> i.code().equals("INVALID_SHIFT_POLICY")).count(),
        "only the CLOSED source is flagged: " + r.issues());
  }

  @Test
  void chronologyTableRangeIsChecked() throws Exception {
    ValidationResult r =
        validate(
            """
            kind: calendar
            id: CAL
            metadata:
              name: X
              coverage: {from: 2020-01-01, to: 2099-12-31}
            uses: [m]
            """,
            """
            kind: module
            id: m
            source: doc
            event_sources:
              - key: eid
                name: Eid
                shiftable: false
                rule: {type: fixed_month_day, month: 10, day: 1, chronology: UMM_AL_QURA}
            """);
    assertTrue(codes(r).contains("CHRONOLOGY_RANGE"), r.issues().toString());
  }
}
