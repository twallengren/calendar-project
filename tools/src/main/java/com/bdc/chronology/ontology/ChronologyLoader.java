package com.bdc.chronology.ontology;

import com.bdc.chronology.ontology.algorithms.ChronologyAlgorithm;
import com.bdc.chronology.ontology.algorithms.FormulaAlgorithm;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Loads chronology definitions from YAML files.
 *
 * <p>This class handles the deserialization of chronology YAML files and the creation of
 * appropriate algorithm implementations based on the algorithm type specified.
 */
public class ChronologyLoader {

  private static final String KIND_CHRONOLOGY = "chronology";
  private static final String KIND_TABLE = "chronology_table";

  private final ObjectMapper mapper;

  public ChronologyLoader() {
    this.mapper =
        new ObjectMapper(new YAMLFactory())
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
  }

  /**
   * Loads a chronology specification from a YAML file.
   *
   * @param path the path to the YAML file
   * @return the ChronologySpec
   * @throws IOException if the file cannot be read
   */
  public ChronologySpec loadSpec(Path path) throws IOException {
    return mapper.readValue(path.toFile(), ChronologySpec.class);
  }

  /**
   * Loads a chronology specification from an input stream.
   *
   * @param inputStream the input stream
   * @return the ChronologySpec
   * @throws IOException if the stream cannot be read
   */
  public ChronologySpec loadSpec(InputStream inputStream) throws IOException {
    return mapper.readValue(inputStream, ChronologySpec.class);
  }

  /**
   * Loads a chronology table from a YAML file.
   *
   * @param path the path to the YAML file
   * @return the ChronologyTable
   * @throws IOException if the file cannot be read
   */
  public ChronologyTable loadTable(Path path) throws IOException {
    return mapper.readValue(path.toFile(), ChronologyTable.class);
  }

  /**
   * Detects the kind of a YAML file.
   *
   * @param path the path to the YAML file
   * @return the kind ("chronology" or "chronology_table")
   * @throws IOException if the file cannot be read
   */
  @SuppressWarnings("unchecked")
  public String detectKind(Path path) throws IOException {
    Map<String, Object> raw = mapper.readValue(path.toFile(), Map.class);
    return (String) raw.get("kind");
  }

  /**
   * Creates a ChronologyAlgorithm from a ChronologySpec.
   *
   * @param spec the chronology specification
   * @return the algorithm implementation
   * @throws IllegalArgumentException if the algorithm type is unsupported
   */
  public ChronologyAlgorithm createAlgorithm(ChronologySpec spec) {
    String type = spec.algorithms().type();

    return switch (type.toUpperCase()) {
      case "FORMULA" -> new FormulaAlgorithm(spec);
      case "LOOKUP_TABLE" ->
          throw new UnsupportedOperationException("LOOKUP_TABLE algorithm not yet implemented");
      case "METONIC_CYCLE" ->
          throw new UnsupportedOperationException("METONIC_CYCLE algorithm not yet implemented");
      default -> throw new IllegalArgumentException("Unknown algorithm type: " + type);
    };
  }

  /**
   * Loads all chronologies from a directory and registers them.
   *
   * @param directory the directory containing YAML files
   * @param registry the registry to register algorithms with
   * @return list of loaded chronology IDs
   * @throws IOException if a file cannot be read
   */
  public List<String> loadDirectory(Path directory, ChronologyRegistry registry)
      throws IOException {
    List<String> loaded = new ArrayList<>();

    try (Stream<Path> paths = Files.walk(directory)) {
      List<Path> yamlFiles =
          paths
              .filter(Files::isRegularFile)
              .filter(p -> p.toString().endsWith(".yaml") || p.toString().endsWith(".yml"))
              .toList();

      for (Path path : yamlFiles) {
        String kind = detectKind(path);
        if (KIND_CHRONOLOGY.equals(kind)) {
          ChronologySpec spec = loadSpec(path);
          ChronologyAlgorithm algorithm = createAlgorithm(spec);
          registry.register(algorithm);
          loaded.add(spec.id());
        }
        // Tables are loaded separately when needed by the algorithm
      }
    }

    return loaded;
  }

  /**
   * Loads chronologies from a classpath resource directory.
   *
   * @param resourcePath the resource path (e.g., "/chronologies")
   * @param registry the registry to register algorithms with
   * @throws IOException if resources cannot be read
   */
  public void loadFromClasspath(String resourcePath, ChronologyRegistry registry)
      throws IOException {
    // This would require listing classpath resources, which is complex
    // For now, we support explicit resource loading
    throw new UnsupportedOperationException(
        "Classpath directory loading not yet implemented. Use loadFromClasspathResource() for individual files.");
  }

  /**
   * Loads a single chronology from a classpath resource.
   *
   * @param resourcePath the resource path (e.g., "/chronologies/julian.yaml")
   * @param registry the registry to register with
   * @throws IOException if the resource cannot be read
   */
  public void loadFromClasspathResource(String resourcePath, ChronologyRegistry registry)
      throws IOException {
    try (InputStream is = getClass().getResourceAsStream(resourcePath)) {
      if (is == null) {
        throw new IOException("Resource not found: " + resourcePath);
      }
      ChronologySpec spec = loadSpec(is);
      ChronologyAlgorithm algorithm = createAlgorithm(spec);
      registry.register(algorithm);
    }
  }
}
