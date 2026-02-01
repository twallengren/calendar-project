package com.bdc.cli;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

class DiffCommandTest {

  @TempDir Path tempDir;

  private ByteArrayOutputStream stdout;
  private ByteArrayOutputStream stderr;
  private PrintStream originalOut;
  private PrintStream originalErr;

  @BeforeEach
  void setUp() {
    stdout = new ByteArrayOutputStream();
    stderr = new ByteArrayOutputStream();
    originalOut = System.out;
    originalErr = System.err;
    System.setOut(new PrintStream(stdout));
    System.setErr(new PrintStream(stderr));
  }

  @AfterEach
  void tearDown() {
    System.setOut(originalOut);
    System.setErr(originalErr);
  }

  private Path createTestArtifactStructure(String calendarId, String tx, String content)
      throws Exception {
    Path resolvedDir = tempDir.resolve("artifacts/resolved").resolve(calendarId);
    Files.createDirectories(resolvedDir);
    Path artifactFile = resolvedDir.resolve(tx + ".yaml");
    Files.writeString(artifactFile, content);
    return tempDir.resolve("artifacts");
  }

  @Test
  void call_identicalFiles_printsNoChanges() throws Exception {
    String content = """
        id: TEST
        metadata:
          name: Test
        """;
    Path artifactsDir = createTestArtifactStructure("TEST", "2024-01-01T00:00:00Z", content);
    createTestArtifactStructure("TEST", "2024-01-02T00:00:00Z", content);

    DiffCommand cmd = new DiffCommand();
    CommandLine cmdLine = new CommandLine(cmd);

    int exitCode =
        cmdLine.execute(
            "resolved",
            "TEST",
            "--tx1",
            "2024-01-01T00:00:00Z",
            "--tx2",
            "2024-01-02T00:00:00Z",
            "--artifacts-dir",
            artifactsDir.toString());

    assertEquals(0, exitCode);
    String output = stdout.toString();
    assertTrue(output.contains("No differences"));
  }

  @Test
  void call_differentFiles_showsDiff() throws Exception {
    String content1 = "id: TEST\nversion: 1\n";
    String content2 = "id: TEST\nversion: 2\n";

    Path resolvedDir = tempDir.resolve("artifacts/resolved/TEST");
    Files.createDirectories(resolvedDir);
    Files.writeString(resolvedDir.resolve("2024-01-01T00:00:00Z.yaml"), content1);
    Files.writeString(resolvedDir.resolve("2024-01-02T00:00:00Z.yaml"), content2);

    DiffCommand cmd = new DiffCommand();
    CommandLine cmdLine = new CommandLine(cmd);

    int exitCode =
        cmdLine.execute(
            "resolved",
            "TEST",
            "--tx1",
            "2024-01-01T00:00:00Z",
            "--tx2",
            "2024-01-02T00:00:00Z",
            "--artifacts-dir",
            tempDir.resolve("artifacts").toString());

    assertEquals(0, exitCode);
    String output = stdout.toString();
    assertTrue(output.contains("version: 1") || output.contains("version: 2"));
    assertTrue(output.contains("different") || output.contains("Line"));
  }

  @Test
  void call_missingFirstFile_returnsError() throws Exception {
    Path resolvedDir = tempDir.resolve("artifacts/resolved/TEST");
    Files.createDirectories(resolvedDir);
    Files.writeString(resolvedDir.resolve("2024-01-02T00:00:00Z.yaml"), "id: TEST\n");

    DiffCommand cmd = new DiffCommand();
    CommandLine cmdLine = new CommandLine(cmd);

    int exitCode =
        cmdLine.execute(
            "resolved",
            "TEST",
            "--tx1",
            "2024-01-01T00:00:00Z",
            "--tx2",
            "2024-01-02T00:00:00Z",
            "--artifacts-dir",
            tempDir.resolve("artifacts").toString());

    assertEquals(1, exitCode);
    String errOutput = stderr.toString();
    assertTrue(errOutput.contains("not found"));
  }

  @Test
  void call_missingSecondFile_returnsError() throws Exception {
    Path resolvedDir = tempDir.resolve("artifacts/resolved/TEST");
    Files.createDirectories(resolvedDir);
    Files.writeString(resolvedDir.resolve("2024-01-01T00:00:00Z.yaml"), "id: TEST\n");

    DiffCommand cmd = new DiffCommand();
    CommandLine cmdLine = new CommandLine(cmd);

    int exitCode =
        cmdLine.execute(
            "resolved",
            "TEST",
            "--tx1",
            "2024-01-01T00:00:00Z",
            "--tx2",
            "2024-01-02T00:00:00Z",
            "--artifacts-dir",
            tempDir.resolve("artifacts").toString());

    assertEquals(1, exitCode);
    String errOutput = stderr.toString();
    assertTrue(errOutput.contains("not found"));
  }

  @Test
  void call_generatedMode_diffEventsCSV() throws Exception {
    // Create generated artifacts structure
    Path generatedDir =
        tempDir.resolve("artifacts/generated/TEST/2024-01-01_2024-12-31/2024-01-01T00:00:00Z");
    Files.createDirectories(generatedDir);
    Files.writeString(
        generatedDir.resolve("events.csv"), "date,type,description\n2024-01-01,CLOSED,New Year\n");

    Path generatedDir2 =
        tempDir.resolve("artifacts/generated/TEST/2024-01-01_2024-12-31/2024-01-02T00:00:00Z");
    Files.createDirectories(generatedDir2);
    Files.writeString(
        generatedDir2.resolve("events.csv"), "date,type,description\n2024-01-01,CLOSED,New Year\n");

    DiffCommand cmd = new DiffCommand();
    CommandLine cmdLine = new CommandLine(cmd);

    int exitCode =
        cmdLine.execute(
            "generated",
            "TEST",
            "--tx1",
            "2024-01-01T00:00:00Z",
            "--tx2",
            "2024-01-02T00:00:00Z",
            "--valid-from",
            "2024-01-01",
            "--valid-to",
            "2024-12-31",
            "--artifacts-dir",
            tempDir.resolve("artifacts").toString());

    assertEquals(0, exitCode);
    String output = stdout.toString();
    assertTrue(output.contains("No differences") || output.contains("Comparing generated"));
  }

  @Test
  void call_invalidArtifactType_returnsError() {
    DiffCommand cmd = new DiffCommand();
    CommandLine cmdLine = new CommandLine(cmd);

    int exitCode =
        cmdLine.execute(
            "invalid-type",
            "TEST",
            "--tx1",
            "2024-01-01T00:00:00Z",
            "--tx2",
            "2024-01-02T00:00:00Z",
            "--artifacts-dir",
            tempDir.toString());

    assertEquals(1, exitCode);
    String errOutput = stderr.toString();
    assertTrue(errOutput.contains("Unknown artifact type") || errOutput.contains("resolved"));
  }

  @Test
  void call_generatedWithValidRange_usesYearShortcut() throws Exception {
    // Create generated artifacts structure
    Path generatedDir =
        tempDir.resolve("artifacts/generated/TEST/2024-01-01_2024-12-31/2024-01-01T00:00:00Z");
    Files.createDirectories(generatedDir);
    Files.writeString(
        generatedDir.resolve("events.csv"), "date,type,description\n2024-01-01,CLOSED,Event1\n");

    Path generatedDir2 =
        tempDir.resolve("artifacts/generated/TEST/2024-01-01_2024-12-31/2024-01-02T00:00:00Z");
    Files.createDirectories(generatedDir2);
    Files.writeString(
        generatedDir2.resolve("events.csv"), "date,type,description\n2024-01-01,CLOSED,Event1\n");

    DiffCommand cmd = new DiffCommand();
    CommandLine cmdLine = new CommandLine(cmd);

    int exitCode =
        cmdLine.execute(
            "generated",
            "TEST",
            "--tx1",
            "2024-01-01T00:00:00Z",
            "--tx2",
            "2024-01-02T00:00:00Z",
            "--valid-range",
            "2024",
            "--artifacts-dir",
            tempDir.resolve("artifacts").toString());

    assertEquals(0, exitCode);
  }
}
