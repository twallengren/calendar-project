package com.bdc.cli;

import static org.junit.jupiter.api.Assertions.*;

import com.bdc.generator.EventGenerator;
import com.bdc.loader.SpecRegistry;
import com.bdc.model.Event;
import com.bdc.model.EventType;
import com.bdc.model.ResolvedSpec;
import com.bdc.resolver.SpecResolver;
import com.bdc.source.SourceRegister;
import com.bdc.trust.CompletenessScope;
import com.bdc.trust.CoverageQuality;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

class ScaffoldCommandTest {

  @TempDir Path tempDir;

  private ByteArrayOutputStream stdout;
  private ByteArrayOutputStream stderr;
  private PrintStream originalOut;
  private PrintStream originalErr;

  @BeforeEach
  void setUp() throws Exception {
    stdout = new ByteArrayOutputStream();
    stderr = new ByteArrayOutputStream();
    originalOut = System.out;
    originalErr = System.err;
    System.setOut(new PrintStream(stdout));
    System.setErr(new PrintStream(stderr));

    // A minimal repo layout: the weekend module scaffold reuses by default, a minimal blessed
    // manifest, and a copy of the export script - the same three pre-existing pieces a real
    // repo would have before onboarding a new market.
    Path weekendModuleSrc = Path.of("modules/policies/weekend_sat_sun.yaml");
    Path weekendModuleDst = tempDir.resolve("modules/policies/weekend_sat_sun.yaml");
    Files.createDirectories(weekendModuleDst.getParent());
    Files.copy(weekendModuleSrc, weekendModuleDst, StandardCopyOption.REPLACE_EXISTING);

    Path manifestDst = tempDir.resolve("blessed/manifest.json");
    Files.createDirectories(manifestDst.getParent());
    Files.writeString(
        manifestDst,
        """
        {
          "schema_version": "1.0",
          "blessed_at": "2020-01-01T00:00:00Z",
          "blessed_by": "test",
          "calendars": {},
          "release_version": {
            "semantic": "0.0.0",
            "git_sha": "0000000000000000000000000000000000000000",
            "generation_date": "2020-01-01"
          }
        }
        """);

    Path exportScriptSrc = Path.of("scripts/reference/export_reference_calendars.py");
    Path exportScriptDst = tempDir.resolve("scripts/reference/export_reference_calendars.py");
    Files.createDirectories(exportScriptDst.getParent());
    Files.copy(exportScriptSrc, exportScriptDst, StandardCopyOption.REPLACE_EXISTING);
  }

  @AfterEach
  void tearDown() {
    System.setOut(originalOut);
    System.setErr(originalErr);
  }

  private int runScaffold(String... extraArgs) {
    ScaffoldCommand cmd = new ScaffoldCommand();
    CommandLine cmdLine = new CommandLine(cmd);
    String[] baseArgs = {
      "--market", "ZZ-TEST",
      "--name", "Zz Test Market",
      "--timezone", "UTC",
      "--mic", "XZZT",
      "--root", tempDir.toString()
    };
    String[] args = new String[baseArgs.length + extraArgs.length];
    System.arraycopy(baseArgs, 0, args, 0, baseArgs.length);
    System.arraycopy(extraArgs, 0, args, baseArgs.length, extraArgs.length);
    return cmdLine.execute(args);
  }

  @Test
  void call_freshRoot_createsExpectedFiles() throws Exception {
    int exitCode = runScaffold();

    assertEquals(0, exitCode, stderr.toString());
    assertTrue(Files.exists(tempDir.resolve("calendars/ZZ-TEST.yaml")));
    assertTrue(Files.exists(tempDir.resolve("modules/groups/zz_test_holidays.yaml")));
    assertTrue(Files.exists(tempDir.resolve("modules/holidays/zz_test_new_years_day.yaml")));
    Path registerPath = tempDir.resolve("sources/ZZ-TEST/register.json");
    assertTrue(Files.exists(registerPath));
    assertTrue(Files.exists(tempDir.resolve("sources/ZZ-TEST/README.md")));
    SourceRegister register = SourceRegister.read(registerPath, tempDir.resolve("sources"));
    assertTrue(register.contains("TODO-zz_test-primary"));
    String sourcesReadme = Files.readString(tempDir.resolve("sources/ZZ-TEST/README.md"));
    assertTrue(sourcesReadme.contains("| `TODO-zz_test-primary` |"));
    assertTrue(sourcesReadme.contains("## Completeness and exception review"));

    String calendar = Files.readString(tempDir.resolve("calendars/ZZ-TEST.yaml"));
    assertFalse(calendar.contains("verified_through"));
    assertEquals(3, occurrences(calendar, "quality: INCOMPLETE"));

    String manifest = Files.readString(tempDir.resolve("blessed/manifest.json"));
    assertFalse(manifest.contains("\"ZZ-TEST\""));

    String exportScript =
        Files.readString(tempDir.resolve("scripts/reference/export_reference_calendars.py"));
    assertTrue(exportScript.contains("(\"ZZ-TEST\", \"XZZT\","));

    String stdoutText = stdout.toString();
    assertTrue(stdoutText.contains("GoldenTests"));
    assertTrue(stdoutText.contains("CONTRIBUTING.md"));
    assertTrue(stdoutText.contains("sources/ZZ-TEST/register.json"));
    assertTrue(stdoutText.contains("python3 scripts/sources.py"));
    assertTrue(stdoutText.contains("UNSCHEDULED_EXCEPTIONS"));
  }

  @Test
  void call_existingRegisterWithoutForce_refusesToOverwrite() throws Exception {
    Path register = tempDir.resolve("sources/ZZ-TEST/register.json");
    Files.createDirectories(register.getParent());
    Files.writeString(register, "{}");

    int exitCode = runScaffold();

    assertEquals(1, exitCode);
    assertTrue(stderr.toString().contains("sources/ZZ-TEST/register.json"));
    assertFalse(Files.exists(tempDir.resolve("calendars/ZZ-TEST.yaml")));
  }

  @Test
  void call_secondRunWithoutForce_refusesToOverwrite() {
    assertEquals(0, runScaffold());

    int exitCode = runScaffold();

    assertEquals(1, exitCode);
    assertTrue(stderr.toString().contains("Refusing to overwrite"));
  }

  @Test
  void call_secondRunWithForce_overwrites() {
    assertEquals(0, runScaffold());

    int exitCode = runScaffold("--force");

    assertEquals(0, exitCode, stderr.toString());
  }

  @Test
  void call_dryRun_writesNothing() throws Exception {
    int exitCode = runScaffold("--dry-run");

    assertEquals(0, exitCode, stderr.toString());
    assertFalse(Files.exists(tempDir.resolve("calendars/ZZ-TEST.yaml")));
    assertFalse(Files.exists(tempDir.resolve("modules/groups/zz_test_holidays.yaml")));
    assertFalse(Files.exists(tempDir.resolve("modules/holidays/zz_test_new_years_day.yaml")));
    assertFalse(Files.exists(tempDir.resolve("sources/ZZ-TEST/register.json")));
    assertFalse(Files.exists(tempDir.resolve("sources/ZZ-TEST/README.md")));
    assertTrue(stdout.toString().contains("sources/ZZ-TEST/register.json"));

    String manifest = Files.readString(tempDir.resolve("blessed/manifest.json"));
    assertFalse(manifest.contains("ZZ-TEST"));

    String exportScript =
        Files.readString(tempDir.resolve("scripts/reference/export_reference_calendars.py"));
    assertFalse(exportScript.contains("ZZ-TEST"));
  }

  @Test
  void call_generatedCalendar_loadsAndGeneratesNewYearsDay() throws Exception {
    assertEquals(0, runScaffold(), stderr.toString());

    SpecRegistry registry = new SpecRegistry();
    registry.loadCalendarsFromDirectory(tempDir.resolve("calendars"));
    registry.loadModulesFromDirectory(tempDir.resolve("modules"));
    registry.assertNoLoadErrors();

    SpecResolver resolver = new SpecResolver(registry);
    ResolvedSpec resolved = resolver.resolve("ZZ-TEST");
    assertEquals(
        java.util.Set.of(CompletenessScope.values()),
        resolved.coverage().quality().stream()
            .map(interval -> interval.scope())
            .collect(java.util.stream.Collectors.toSet()));
    assertTrue(
        resolved.coverage().quality().stream()
            .allMatch(
                interval ->
                    interval.quality() == CoverageQuality.INCOMPLETE
                        && interval.evidenceIds().isEmpty()
                        && interval.from().equals(LocalDate.of(2020, 1, 1))
                        && interval.to().equals(LocalDate.of(2030, 12, 31))));

    EventGenerator generator = new EventGenerator();
    List<Event> events =
        generator.generate(resolved, LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 31));

    boolean hasNewYearsDay =
        events.stream()
            .anyMatch(
                e ->
                    e.date().equals(LocalDate.of(2024, 1, 1))
                        && e.type() == EventType.CLOSED
                        && "zz_test_new_years_day".equals(e.key()));
    assertTrue(hasNewYearsDay, "expected a CLOSED zz_test_new_years_day event on 2024-01-01");
  }

  private static int occurrences(String text, String substring) {
    return (text.length() - text.replace(substring, "").length()) / substring.length();
  }
}
