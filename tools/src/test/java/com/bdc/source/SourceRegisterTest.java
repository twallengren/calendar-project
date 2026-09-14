package com.bdc.source;

import static org.junit.jupiter.api.Assertions.*;

import com.bdc.loader.SpecRegistry;
import com.bdc.resolver.SpecResolver;
import com.bdc.validation.SourceRegisterValidator;
import com.bdc.validation.ValidationResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SourceRegisterTest {
  @TempDir Path temp;

  private Path register(String localFiles) throws IOException {
    Path dir = Files.createDirectories(temp.resolve("MARKET"));
    Path register = dir.resolve("register.json");
    Files.writeString(
        register,
        """
        {"schema_version":"1.0","entries":[{"id":"notice","title":"Notice","publisher":"Authority",
        "location":"notice.txt","retrieved":"2026-09-14","covers":"2026","notes":"",
        "local_files":%s,"support_intervals":[]}]}
        """
            .formatted(localFiles));
    return register;
  }

  @Test
  void checksumMismatchAndMissingEvidenceFailClosed() throws Exception {
    Path path = register("[{\"path\":\"MARKET/notice.txt\",\"sha256\":\"bad\"}]");
    assertThrows(IOException.class, () -> SourceRegister.read(path, temp));
    Files.writeString(temp.resolve("MARKET/notice.txt"), "changed evidence");
    assertTrue(
        assertThrows(IOException.class, () -> SourceRegister.read(path, temp))
            .getMessage()
            .contains("checksum"));
  }

  @Test
  void canonicalEntryOverridesEditedHumanTable() throws Exception {
    SourceRegister register = SourceRegister.read(register("[]"), temp);
    String text =
        """
        # Explanation

        | id | title | publisher | url / file | retrieved | covers | notes |
        |----|-------|-----------|------------|-----------|--------|-------|
        | `wrong` | bad | bad | bad | bad | bad | bad |

        Keep this prose.
        """;
    String generated = register.markdown(text);
    assertTrue(generated.contains("`notice`"));
    assertFalse(generated.contains("`wrong`"));
    assertTrue(generated.contains("Keep this prose."));
  }

  @Test
  void unknownSchemaFieldCannotMasqueradeAsSupport() throws Exception {
    Path path = register("[]");
    Files.writeString(
        path,
        Files.readString(path)
            .replace(
                "\"support_intervals\":[]", "\"support_intervals\":[],\"supported_intervals\":[]"));
    assertTrue(
        assertThrows(IOException.class, () -> SourceRegister.read(path, temp))
            .getMessage()
            .contains("unknown"));
  }

  @Test
  void verifiedClaimsRequireContinuousCitedSupport() throws Exception {
    Path path = register("[]");
    String original = Files.readString(path);
    var from = java.time.LocalDate.of(2026, 1, 1);
    var to = java.time.LocalDate.of(2026, 1, 3);
    var coverage =
        new com.bdc.model.CalendarSpec.Coverage(
            from,
            to,
            to,
            java.util.List.of(
                new com.bdc.trust.CoverageInterval(
                    com.bdc.trust.CompletenessScope.SCHEDULED_CLOSURES,
                    from,
                    to,
                    com.bdc.trust.CoverageQuality.VERIFIED,
                    java.util.List.of("notice"))));
    var metadata = new com.bdc.model.CalendarSpec.Metadata("Test", "Test", "ISO", "UTC", coverage);
    var spec =
        new com.bdc.model.ResolvedSpec("TEST", metadata, null, null, null, null, null, null, null);
    for (String support :
        java.util.List.of(
            "[]",
            "[{\"from\":\"2026-01-01\",\"to\":\"2026-01-01\",\"scope\":\"SCHEDULED_CLOSURES\"},{\"from\":\"2026-01-03\",\"to\":\"2026-01-03\",\"scope\":\"SCHEDULED_CLOSURES\"}]")) {
      Files.writeString(
          path, original.replace("\"support_intervals\":[]", "\"support_intervals\":" + support));
      var result = new ValidationResult("TEST");
      new SourceRegisterValidator(temp).validate(spec, result);
      assertTrue(
          result.issues().stream()
              .anyMatch(issue -> issue.code().equals("UNSUPPORTED_COVERAGE_CLAIM")));
    }
    Files.writeString(
        path,
        original.replace(
            "\"support_intervals\":[]",
            "\"support_intervals\":[{\"from\":\"2026-01-01\",\"to\":\"2026-01-03\",\"scope\":\"SCHEDULED_CLOSURES\"}]"));
    var result = new ValidationResult("TEST");
    new SourceRegisterValidator(temp).validate(spec, result);
    assertFalse(result.hasErrors());
  }

  @Test
  void unrelatedDuplicateIdCannotFillTheCalendarsEvidenceGap() throws Exception {
    Path original = register("[]");
    Files.move(original.getParent(), temp.resolve("TEST"));
    Path own = temp.resolve("TEST/register.json");
    Path other = Files.createDirectories(temp.resolve("OTHER")).resolve("register.json");
    Files.writeString(
        other,
        Files.readString(own)
            .replace(
                "\"support_intervals\":[]",
                "\"support_intervals\":[{\"from\":\"2026-01-01\",\"to\":\"2026-12-31\",\"scope\":\"SCHEDULED_CLOSURES\"}]"));
    var day = java.time.LocalDate.of(2026, 1, 1);
    var coverage =
        new com.bdc.model.CalendarSpec.Coverage(
            day,
            day,
            day,
            java.util.List.of(
                new com.bdc.trust.CoverageInterval(
                    com.bdc.trust.CompletenessScope.SCHEDULED_CLOSURES,
                    day,
                    day,
                    com.bdc.trust.CoverageQuality.VERIFIED,
                    java.util.List.of("notice"))));
    var metadata = new com.bdc.model.CalendarSpec.Metadata("Test", "Test", "ISO", "UTC", coverage);
    var validator = new SourceRegisterValidator(temp);
    var result = new ValidationResult("TEST");
    validator.validate(
        new com.bdc.model.ResolvedSpec("TEST", metadata, null, null, null, null, null, null, null),
        result);
    assertTrue(
        result.issues().stream()
            .anyMatch(issue -> issue.code().equals("UNSUPPORTED_COVERAGE_CLAIM")));
    var ambiguous = new ValidationResult("UNRELATED");
    validator.validate(
        new com.bdc.model.ResolvedSpec(
            "UNRELATED", metadata, null, null, null, null, null, null, null),
        ambiguous);
    assertTrue(
        ambiguous.issues().stream().anyMatch(issue -> issue.code().equals("AMBIGUOUS_SOURCE")));
  }

  @Test
  void deltaAndRuleIdsMustResolveEvenWhenCitationHasUrl() throws Exception {
    register("[]");
    Path calendars = Files.createDirectories(temp.resolve("calendars"));
    Files.writeString(
        calendars.resolve("TEST.yaml"),
        """
        kind: calendar
        id: TEST
        event_sources:
          - key: closed
            name: Closed
            source: [{id: unknown, url: 'https://example.test'}]
            rule:
              type: fixed_month_day
              month: 1
              day: 1
        deltas:
          - action: remove
            key: closed
            date: 2026-01-01
            source: [{id: unknown-delta}]
        """);
    SpecRegistry registry = new SpecRegistry();
    registry.loadCalendarsFromDirectory(calendars);
    registry.assertNoLoadErrors();
    // Catalog roots contain only market directories, so move the fixture outside before reading.
    var resolved = new SpecResolver(registry).resolve("TEST");
    Files.delete(calendars.resolve("TEST.yaml"));
    Files.delete(calendars);
    ValidationResult result = new ValidationResult("TEST");
    new SourceRegisterValidator(temp).validate(resolved, result);
    assertEquals(
        2, result.issues().stream().filter(i -> i.code().equals("UNRESOLVED_SOURCE")).count());
  }
}
