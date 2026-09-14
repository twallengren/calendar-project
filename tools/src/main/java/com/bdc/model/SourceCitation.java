package com.bdc.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;

/**
 * A structured citation for the authoritative source backing an event source or module.
 *
 * <p>Either {@code id} (referencing an entry in a {@code sources/<market>/README.md} table), {@code
 * url}, or {@code file} should be present. All fields are optional at the schema level so partial
 * citations can be recorded and completed later; {@code validate --strict} requires at least one of
 * {@code id}, {@code url} or {@code file}.
 */
public record SourceCitation(
    String id,
    String title,
    String publisher,
    String url,
    String file,
    LocalDate retrieved,
    @JsonProperty("ref") String ref,
    String note) {

  /** Convenience factory for an id-only citation. */
  public static SourceCitation ofId(String id) {
    return new SourceCitation(id, null, null, null, null, null, null, null);
  }

  /** True if the citation points at something resolvable (an id, a URL, or a file). */
  public boolean isResolvable() {
    return notBlank(id) || notBlank(url) || notBlank(file);
  }

  private static boolean notBlank(String s) {
    return s != null && !s.isBlank();
  }
}
