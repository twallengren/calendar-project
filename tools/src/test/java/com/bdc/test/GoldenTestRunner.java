package com.bdc.test;

import com.bdc.emitter.CsvEmitter;
import com.bdc.emitter.MetadataEmitter;
import com.bdc.generator.EventGenerator;
import com.bdc.loader.SpecRegistry;
import com.bdc.model.Event;
import com.bdc.model.ResolvedSpec;
import com.bdc.resolver.SpecResolver;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Utility for running golden tests (snapshot-based regression tests).
 *
 * Golden tests compare generated output against pre-recorded expected outputs.
 * To update goldens, set the UPDATE_GOLDENS environment variable or system property to "true".
 */
public class GoldenTestRunner {

    private static final Path GOLDEN_DIR = Path.of("tools/src/test/resources/golden");
    private static final boolean UPDATE_MODE = Boolean.parseBoolean(
        System.getenv().getOrDefault("UPDATE_GOLDENS",
            System.getProperty("updateGoldens", "false"))
    );

    private final SpecRegistry registry;
    private final SpecResolver resolver;
    private final EventGenerator generator;
    private final CsvEmitter csvEmitter;
    private final MetadataEmitter metadataEmitter;

    public GoldenTestRunner(SpecRegistry registry) {
        this.registry = registry;
        this.resolver = new SpecResolver(registry);
        this.generator = new EventGenerator();
        this.csvEmitter = new CsvEmitter();
        this.metadataEmitter = new MetadataEmitter();
    }

    /**
     * Creates a GoldenTestRunner for test calendars.
     */
    public static GoldenTestRunner forTestCalendars() throws Exception {
        SpecRegistry registry = new SpecRegistry();
        registry.loadCalendarsFromDirectory(Path.of("tools/src/test/resources/test-calendars/calendars"));
        registry.loadModulesFromDirectory(Path.of("tools/src/test/resources/test-calendars/modules"));
        return new GoldenTestRunner(registry);
    }

    /**
     * Creates a GoldenTestRunner for production calendars.
     */
    public static GoldenTestRunner forProductionCalendars() throws Exception {
        SpecRegistry registry = new SpecRegistry();
        registry.loadCalendarsFromDirectory(Path.of("calendars"));
        registry.loadModulesFromDirectory(Path.of("modules"));
        return new GoldenTestRunner(registry);
    }

    /**
     * Assert that generated CSV matches the golden file.
     * If UPDATE_GOLDENS is true, updates the golden file instead.
     */
    public void assertCsvGoldenMatch(String calendarId, int year) throws IOException {
        assertCsvGoldenMatch(calendarId, year, year);
    }

    /**
     * Assert that generated CSV matches the golden file for a year range.
     */
    public void assertCsvGoldenMatch(String calendarId, int fromYear, int toYear) throws IOException {
        ResolvedSpec spec = resolver.resolve(calendarId);
        LocalDate from = LocalDate.of(fromYear, 1, 1);
        LocalDate to = LocalDate.of(toYear, 12, 31);

        List<Event> events = generator.generate(spec, from, to);
        String actual = csvEmitter.emitToString(events);

        String goldenName = String.format("%s-%d-%d.csv", calendarId, fromYear, toYear);
        if (fromYear == toYear) {
            goldenName = String.format("%s-%d.csv", calendarId, fromYear);
        }
        Path goldenPath = GOLDEN_DIR.resolve(goldenName);

        assertGoldenMatch(goldenPath, actual, goldenName);
    }

    /**
     * Assert that generated metadata JSON matches the golden file.
     * Note: Metadata contains timestamps, so use assertCsvGoldenMatch for deterministic tests.
     */
    public void assertMetadataGoldenMatch(String calendarId, int year) throws IOException {
        assertMetadataGoldenMatch(calendarId, year, year);
    }

    /**
     * Assert that generated metadata JSON matches the golden file for a year range.
     * Note: Metadata contains timestamps, so this test may be flaky unless you
     * normalize the generated_at field.
     */
    public void assertMetadataGoldenMatch(String calendarId, int fromYear, int toYear) throws IOException {
        ResolvedSpec spec = resolver.resolve(calendarId);
        LocalDate from = LocalDate.of(fromYear, 1, 1);
        LocalDate to = LocalDate.of(toYear, 12, 31);

        List<Event> events = generator.generate(spec, from, to);
        String actual = metadataEmitter.emitToString(spec, events, from, to);

        // Normalize the generated_at timestamp for deterministic comparison
        actual = normalizeMetadataTimestamp(actual);

        String goldenName = String.format("%s-%d-%d-metadata.json", calendarId, fromYear, toYear);
        if (fromYear == toYear) {
            goldenName = String.format("%s-%d-metadata.json", calendarId, fromYear);
        }
        Path goldenPath = GOLDEN_DIR.resolve(goldenName);

        assertGoldenMatch(goldenPath, actual, goldenName);
    }

    private String normalizeMetadataTimestamp(String json) {
        // Replace the generated_at timestamp with a fixed value for deterministic comparison
        return json.replaceAll(
            "\"generated_at\"\\s*:\\s*\"[^\"]+\"",
            "\"generated_at\" : \"NORMALIZED_TIMESTAMP\""
        );
    }

    /**
     * Assert that arbitrary content matches a golden file.
     */
    public void assertGoldenMatch(String goldenName, String actual) throws IOException {
        Path goldenPath = GOLDEN_DIR.resolve(goldenName);
        assertGoldenMatch(goldenPath, actual, goldenName);
    }

    private void assertGoldenMatch(Path goldenPath, String actual, String displayName) throws IOException {
        if (UPDATE_MODE) {
            Files.createDirectories(goldenPath.getParent());
            Files.writeString(goldenPath, actual);
            System.out.println("Updated golden file: " + goldenPath);
            return;
        }

        if (!Files.exists(goldenPath)) {
            fail("Golden file not found: " + goldenPath +
                 "\nRun with -DupdateGoldens=true or UPDATE_GOLDENS=true to create it." +
                 "\nActual content:\n" + actual);
        }

        String expected = Files.readString(goldenPath);
        assertEquals(expected, actual,
            "Golden match failed for " + displayName +
            "\nRun with -DupdateGoldens=true to update the golden file.");
    }

    /**
     * Check if we're in update mode.
     */
    public static boolean isUpdateMode() {
        return UPDATE_MODE;
    }
}
