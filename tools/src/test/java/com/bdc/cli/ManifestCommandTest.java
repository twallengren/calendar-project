package com.bdc.cli;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

class ManifestCommandTest {

  @TempDir Path tempDir;

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private void writeCalendar(Path blessed, String id, String metadataJson) throws Exception {
    Path dir = blessed.resolve(id);
    Files.createDirectories(dir);
    Files.writeString(dir.resolve("metadata.json"), metadataJson);
  }

  private int runManifest() {
    return new CommandLine(new ManifestCommand()).execute("--blessed-dir", tempDir.toString());
  }

  @Test
  void derivesAliasesMapWithMicsFirst() throws Exception {
    Files.writeString(
        tempDir.resolve("manifest.json"),
        """
        {
          "schema_version": "1.0",
          "calendars": {
            "US-NYSE": {"kind": "market"},
            "SA-TADAWUL": {"kind": "market"}
          }
        }
        """);
    writeCalendar(tempDir, "US-NYSE", """
        {"mic": "XNYS", "aliases": ["NYSE"]}
        """);
    writeCalendar(
        tempDir, "SA-TADAWUL", """
        {"mic": "XSAU", "aliases": ["TADAWUL"]}
        """);

    assertEquals(0, runManifest());

    JsonNode manifest = MAPPER.readTree(tempDir.resolve("manifest.json").toFile());
    JsonNode aliases = manifest.get("aliases");
    assertNotNull(aliases);
    assertEquals("US-NYSE", aliases.get("XNYS").asText());
    assertEquals("SA-TADAWUL", aliases.get("XSAU").asText());
    assertEquals("US-NYSE", aliases.get("NYSE").asText());
    assertEquals("SA-TADAWUL", aliases.get("TADAWUL").asText());

    // MICs are listed before other aliases.
    java.util.List<String> keys = new java.util.ArrayList<>();
    aliases.fieldNames().forEachRemaining(keys::add);
    assertEquals(java.util.List.of("XNYS", "XSAU", "NYSE", "TADAWUL"), keys);
  }

  @Test
  void conflictingMicsFailTheCommand() throws Exception {
    Files.writeString(
        tempDir.resolve("manifest.json"),
        """
        {
          "schema_version": "1.0",
          "calendars": {
            "CAL1": {"kind": "market"},
            "CAL2": {"kind": "market"}
          }
        }
        """);
    writeCalendar(tempDir, "CAL1", """
        {"mic": "XABC"}
        """);
    writeCalendar(tempDir, "CAL2", """
        {"mic": "XABC"}
        """);

    assertEquals(1, runManifest());
  }
}
