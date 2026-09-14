package com.bdc.cli;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * Recomputes {@code blessed/manifest.json}'s {@code aliases} map from every published calendar's
 * {@code metadata.json} ({@code mic} plus any {@code aliases}).
 *
 * <p>This is the one place the exchange-code alias table is assembled: {@code
 * python/scripts/sync_data.py} and {@code data/build.gradle.kts} both read the result back out of
 * {@code blessed/manifest.json} rather than hand-maintaining their own copy of it. Run by {@code
 * scripts/bless.sh} after every calendar is (re)generated.
 *
 * <p>Fails if two calendars claim the same mic or alias case-insensitively - the same check {@code
 * validate --strict} runs against the specs, run again here against what was actually published, in
 * case {@code blessed/} is stale relative to {@code calendars/}.
 */
@Command(
    name = "manifest",
    description = "Recompute blessed/manifest.json's aliases map from calendar metadata")
public class ManifestCommand implements Callable<Integer> {

  @Option(
      names = {"--blessed-dir"},
      defaultValue = "blessed",
      description = "Blessed artifacts directory")
  private Path blessedDir;

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Override
  public Integer call() {
    try {
      Path manifestPath = blessedDir.resolve("manifest.json");
      ObjectNode manifestRoot = (ObjectNode) MAPPER.readTree(manifestPath.toFile());
      JsonNode calendarsNode = manifestRoot.get("calendars");
      if (calendarsNode == null || !calendarsNode.isObject()) {
        System.err.println("manifest failed: " + manifestPath + " has no 'calendars' object");
        return 1;
      }

      List<String> ids = new ArrayList<>();
      calendarsNode.fieldNames().forEachRemaining(ids::add);
      Collections.sort(ids);

      // MICs and other aliases are tracked separately so the map can list MICs first; each is a
      // sorted map in its own right so ties within a group are alphabetical.
      Map<String, String> micTokens = new TreeMap<>();
      Map<String, String> aliasTokens = new TreeMap<>();
      List<String> conflicts = new ArrayList<>();

      for (String id : ids) {
        Path metadataPath = blessedDir.resolve(id).resolve("metadata.json");
        if (!Files.exists(metadataPath)) {
          continue;
        }
        JsonNode metadata = MAPPER.readTree(metadataPath.toFile());
        JsonNode micNode = metadata.get("mic");
        if (micNode != null && micNode.isTextual() && !micNode.asText().isBlank()) {
          addToken(micTokens, aliasTokens, micNode.asText(), id, conflicts);
        }
        JsonNode aliasesNode = metadata.get("aliases");
        if (aliasesNode != null && aliasesNode.isArray()) {
          for (JsonNode alias : aliasesNode) {
            if (alias.isTextual() && !alias.asText().isBlank()) {
              addToken(aliasTokens, micTokens, alias.asText(), id, conflicts);
            }
          }
        }
      }

      if (!conflicts.isEmpty()) {
        System.err.println("manifest failed: conflicting mic/alias tokens:");
        for (String conflict : conflicts) {
          System.err.println("  " + conflict);
        }
        return 1;
      }

      Map<String, String> combined = new LinkedHashMap<>();
      combined.putAll(micTokens);
      combined.putAll(aliasTokens);

      ObjectNode aliasesOut = MAPPER.createObjectNode();
      combined.forEach(aliasesOut::put);
      manifestRoot.set("aliases", aliasesOut);

      Files.writeString(manifestPath, JqStyleJson.render(manifestRoot));
      System.out.println("Wrote " + combined.size() + " alias(es) to " + manifestPath);
      return 0;
    } catch (IOException e) {
      System.err.println("manifest failed: " + e.getMessage());
      return 1;
    }
  }

  /**
   * Records {@code token -> id} in {@code into}, uppercased, failing loudly if it (or its casing
   * twin) is already claimed by another calendar in either {@code into} or {@code other}.
   */
  private static void addToken(
      Map<String, String> into,
      Map<String, String> other,
      String token,
      String id,
      List<String> conflicts) {
    String key = token.toUpperCase(Locale.ROOT);
    String existing = into.get(key);
    if (existing == null) {
      existing = other.get(key);
    }
    if (existing != null && !existing.equals(id)) {
      conflicts.add("'" + token + "' is claimed by both " + existing + " and " + id);
      return;
    }
    into.put(key, id);
  }
}
