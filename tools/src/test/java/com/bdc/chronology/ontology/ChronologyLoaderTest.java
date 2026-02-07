package com.bdc.chronology.ontology;

import static org.junit.jupiter.api.Assertions.*;

import com.bdc.chronology.ontology.algorithms.ChronologyAlgorithm;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ChronologyLoaderTest {

  private ChronologyLoader loader;
  private ChronologyRegistry registry;

  @BeforeEach
  void setUp() {
    loader = new ChronologyLoader();
    registry = ChronologyRegistry.getInstance();
    registry.reset();
  }

  @Test
  void loadSpec_validYaml_parsesCorrectly() throws IOException {
    String yaml =
        """
        kind: chronology
        id: TEST_CAL
        metadata:
          name: Test Calendar
          description: A test calendar for unit testing
        structure:
          epoch_jdn: 1721424
          week:
            days_per_week: 7
            first_day: MONDAY
          months:
            - {name: January, days: 31}
            - {name: February, days: 28, leap_days: 29}
        algorithms:
          type: FORMULA
          leap_year: "year % 4 == 0"
        """;

    InputStream is = new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8));
    ChronologySpec spec = loader.loadSpec(is);

    assertEquals("chronology", spec.kind());
    assertEquals("TEST_CAL", spec.id());
    assertEquals("Test Calendar", spec.metadata().name());
    assertEquals("A test calendar for unit testing", spec.metadata().description());
    assertEquals(1721424L, spec.structure().epochJdn());
    assertEquals(7, spec.structure().week().daysPerWeek());
    assertEquals("MONDAY", spec.structure().week().firstDay());
    assertEquals(2, spec.structure().months().size());
    assertEquals("January", spec.structure().months().get(0).name());
    assertEquals(31, spec.structure().months().get(0).days());
    assertEquals("February", spec.structure().months().get(1).name());
    assertEquals(28, spec.structure().months().get(1).days());
    assertEquals(29, spec.structure().months().get(1).leapDays());
    assertEquals("FORMULA", spec.algorithms().type());
    assertEquals("year % 4 == 0", spec.algorithms().leapYear());
  }

  @Test
  void loadSpec_fromFile_works(@TempDir Path tempDir) throws IOException {
    String yaml =
        """
        kind: chronology
        id: FILE_TEST
        metadata:
          name: File Test
          description: Test loading from file
        structure:
          epoch_jdn: 0
          months:
            - {name: Month1, days: 30}
        algorithms:
          type: FORMULA
          leap_year: "false"
        """;

    Path yamlFile = tempDir.resolve("test.yaml");
    Files.writeString(yamlFile, yaml);

    ChronologySpec spec = loader.loadSpec(yamlFile);

    assertEquals("FILE_TEST", spec.id());
    assertEquals("File Test", spec.metadata().name());
  }

  @Test
  void createAlgorithm_formulaType_createsFormulaAlgorithm() throws IOException {
    String yaml =
        """
        kind: chronology
        id: FORMULA_TEST
        metadata:
          name: Formula Test
          description: Test formula algorithm
        structure:
          epoch_jdn: 1721424
          months:
            - {name: January, days: 31}
            - {name: February, days: 28, leap_days: 29}
            - {name: March, days: 31}
            - {name: April, days: 30}
            - {name: May, days: 31}
            - {name: June, days: 30}
            - {name: July, days: 31}
            - {name: August, days: 31}
            - {name: September, days: 30}
            - {name: October, days: 31}
            - {name: November, days: 30}
            - {name: December, days: 31}
        algorithms:
          type: FORMULA
          leap_year: "year % 4 == 0"
        """;

    InputStream is = new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8));
    ChronologySpec spec = loader.loadSpec(is);
    ChronologyAlgorithm algorithm = loader.createAlgorithm(spec);

    assertEquals("FORMULA_TEST", algorithm.getChronologyId());
    assertTrue(algorithm.isLeapYear(2000));
    assertFalse(algorithm.isLeapYear(2001));
  }

  @Test
  void createAlgorithm_lookupTableType_throwsUnsupported() throws IOException {
    String yaml =
        """
        kind: chronology
        id: LOOKUP_TEST
        metadata:
          name: Lookup Test
          description: Test lookup table
        structure:
          epoch_jdn: 0
          months:
            - {name: Month1, days: 30}
        algorithms:
          type: LOOKUP_TABLE
          table: SOME_TABLE
        """;

    InputStream is = new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8));
    ChronologySpec spec = loader.loadSpec(is);

    assertThrows(UnsupportedOperationException.class, () -> loader.createAlgorithm(spec));
  }

  @Test
  void detectKind_chronology_returnsChronology(@TempDir Path tempDir) throws IOException {
    String yaml = """
        kind: chronology
        id: KIND_TEST
        """;

    Path yamlFile = tempDir.resolve("test.yaml");
    Files.writeString(yamlFile, yaml);

    assertEquals("chronology", loader.detectKind(yamlFile));
  }

  @Test
  void detectKind_chronologyTable_returnsTable(@TempDir Path tempDir) throws IOException {
    String yaml = """
        kind: chronology_table
        id: TABLE_TEST
        """;

    Path yamlFile = tempDir.resolve("test.yaml");
    Files.writeString(yamlFile, yaml);

    assertEquals("chronology_table", loader.detectKind(yamlFile));
  }

  @Test
  void loadDirectory_loadsAllChronologies(@TempDir Path tempDir) throws IOException {
    String yaml1 =
        """
        kind: chronology
        id: CAL_ONE
        metadata:
          name: Calendar One
          description: First calendar
        structure:
          epoch_jdn: 0
          months:
            - {name: Month1, days: 30}
        algorithms:
          type: FORMULA
          leap_year: "false"
        """;

    String yaml2 =
        """
        kind: chronology
        id: CAL_TWO
        metadata:
          name: Calendar Two
          description: Second calendar
        structure:
          epoch_jdn: 100
          months:
            - {name: Month1, days: 31}
        algorithms:
          type: FORMULA
          leap_year: "true"
        """;

    Files.writeString(tempDir.resolve("cal1.yaml"), yaml1);
    Files.writeString(tempDir.resolve("cal2.yaml"), yaml2);

    List<String> loaded = loader.loadDirectory(tempDir, registry);

    assertEquals(2, loaded.size());
    assertTrue(loaded.contains("CAL_ONE"));
    assertTrue(loaded.contains("CAL_TWO"));
    assertTrue(registry.hasChronology("CAL_ONE"));
    assertTrue(registry.hasChronology("CAL_TWO"));
  }

  @Test
  void loadDirectory_ignoresNonChronologyFiles(@TempDir Path tempDir) throws IOException {
    String chronology =
        """
        kind: chronology
        id: REAL_CAL
        metadata:
          name: Real Calendar
          description: A real chronology
        structure:
          epoch_jdn: 0
          months:
            - {name: Month1, days: 30}
        algorithms:
          type: FORMULA
          leap_year: "false"
        """;

    String table =
        """
        kind: chronology_table
        id: SOME_TABLE
        metadata:
          name: Some Table
        entries: []
        """;

    Files.writeString(tempDir.resolve("real.yaml"), chronology);
    Files.writeString(tempDir.resolve("table.yaml"), table);

    List<String> loaded = loader.loadDirectory(tempDir, registry);

    assertEquals(1, loaded.size());
    assertEquals("REAL_CAL", loaded.get(0));
  }

  @Test
  void loadTable_validYaml_parsesCorrectly() throws IOException {
    String yaml =
        """
        kind: chronology_table
        id: TEST_TABLE
        metadata:
          name: Test Table
          description: A test lookup table
          source: Unit tests
          valid_from: "1400-01-01"
          valid_to: "1500-12-30"
        entries:
          - {year: 1400, jdn: 2000000}
          - {year: 1401, jdn: 2000354}
          - {year: 1400, month: 1, jdn: 2000000, length: 30}
          - {year: 1400, month: 2, jdn: 2000030, length: 29}
        """;

    Path tempFile = Files.createTempFile("table", ".yaml");
    Files.writeString(tempFile, yaml);

    try {
      ChronologyTable table = loader.loadTable(tempFile);

      assertEquals("chronology_table", table.kind());
      assertEquals("TEST_TABLE", table.id());
      assertEquals("Test Table", table.metadata().name());
      assertEquals("Unit tests", table.metadata().source());
      assertEquals(4, table.entries().size());

      // Test findByYear
      ChronologyTable.Entry yearEntry = table.findByYear(1400);
      assertNotNull(yearEntry);
      assertEquals(1400, yearEntry.year());
      assertEquals(2000000L, yearEntry.jdn());

      // Test findByYearMonth
      ChronologyTable.Entry monthEntry = table.findByYearMonth(1400, 2);
      assertNotNull(monthEntry);
      assertEquals(1400, monthEntry.year());
      assertEquals(2, monthEntry.month());
      assertEquals(29, monthEntry.length());
    } finally {
      Files.delete(tempFile);
    }
  }
}
