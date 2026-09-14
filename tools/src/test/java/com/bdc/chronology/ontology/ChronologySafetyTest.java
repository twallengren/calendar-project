package com.bdc.chronology.ontology;

import static org.junit.jupiter.api.Assertions.*;

import com.bdc.chronology.codegen.ChronologyCodeGenerator;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ChronologySafetyTest {
  @Test
  void validatesEntireTypedFormulaBeforeGeneration() {
    for (String expression :
        new String[] {
          "false && garbage",
          "true || System.exit(1)",
          "year; return true",
          "year + true",
          "year == 1 )",
          "year %",
          "1 + 2",
          "true || year",
          "year == 1 &&"
        }) {
      assertThrows(
          IllegalArgumentException.class, () -> FormulaSyntax.validate(expression), expression);
    }
    FormulaSyntax.validate("(year % 4 == 0 && year % 100 != 0) || year % 400 == 0");
  }

  @Test
  void unknownSchemaFieldsAreFatal(@TempDir Path scratch) throws Exception {
    String yaml = "kind: chronology\nid: BAD\nmisspelled: true\n";
    assertThrows(
        java.io.IOException.class,
        () ->
            new ChronologyLoader()
                .loadSpec(new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8))));
    Path inputs = Files.createDirectory(scratch.resolve("input"));
    Files.writeString(inputs.resolve("bad.yaml"), yaml);
    assertThrows(
        java.io.IOException.class,
        () -> new ChronologyCodeGenerator().generateAll(inputs, scratch.resolve("output")));
  }

  @Test
  void unsupportedAlgorithmCannotFallThroughToFormula(@TempDir Path scratch) throws Exception {
    var algorithms =
        new ChronologySpec.Algorithms("METONIC_CYCLE", "false", null, null, null, null, null, null);
    var spec =
        new ChronologySpec(
            "chronology", "BAD", new ChronologySpec.Metadata("Bad", null), null, algorithms);
    assertThrows(
        IllegalArgumentException.class,
        () -> new ChronologyCodeGenerator().generate(spec, scratch));
    assertEquals(0, Files.list(scratch).count());
  }

  @Test
  void committedSourcesMatchScratchGenerationIncludingMembership(@TempDir Path scratch)
      throws Exception {
    new ChronologyCodeGenerator().generateAll(Path.of("chronologies"), scratch);
    Path committed = Path.of("tools/src/main/java-generated/com/bdc/chronology/generated");
    try (var actual = Files.list(scratch);
        var expected = Files.list(committed)) {
      assertEquals(
          expected.map(p -> p.getFileName().toString()).sorted().toList(),
          actual.map(p -> p.getFileName().toString()).sorted().toList());
    }
    try (var actual = Files.list(scratch)) {
      for (Path path : actual.toList())
        assertArrayEquals(
            Files.readAllBytes(committed.resolve(path.getFileName())),
            Files.readAllBytes(path),
            path.toString());
    }
  }
}
