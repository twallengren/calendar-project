package com.bdc.site;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.bdc.cli.Main;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import picocli.CommandLine;

/**
 * One real {@code site} build, shared by every test class that needs to read published pages.
 *
 * <p>Generating the site writes several thousand files, so the cost is paid once per JVM rather
 * than once per test class. {@link SiteGeneratorTest} deliberately keeps its own two builds — it is
 * checking that two runs are byte-identical, which a cached one cannot show.
 */
final class GeneratedSite {

  static final String GENERATED_AT = "2026-06-01T00:00:00Z";
  static final String BASE_URL = "https://example.test/calendars/";

  private static Path site;

  private GeneratedSite() {}

  /** The output directory of the shared build, generating it on first use. */
  static synchronized Path get() {
    if (site == null) {
      try {
        Path out = Files.createTempDirectory("bdc-site");
        Runtime.getRuntime()
            .addShutdownHook(
                new Thread(
                    () -> {
                      try (var paths = Files.walk(out)) {
                        for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                          Files.deleteIfExists(path);
                        }
                      } catch (IOException e) {
                        System.err.println(
                            "Could not remove generated test site " + out + ": " + e);
                      }
                    },
                    "generated-site-cleanup"));
        int exit =
            new CommandLine(new Main())
                .execute(
                    "site",
                    "--out",
                    out.toString(),
                    "--base-url",
                    BASE_URL,
                    "--site-name",
                    "Business Day Calendars",
                    "--generated-at",
                    GENERATED_AT);
        assertEquals(0, exit, "site command failed");
        site = out;
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }
    return site;
  }

  static String read(String relativePath) throws IOException {
    return Files.readString(get().resolve(relativePath), StandardCharsets.UTF_8);
  }
}
