package com.bdc.chronology.codegen;

import com.bdc.chronology.ontology.ChronologySpec;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Generates Java chronology classes from YAML specifications.
 *
 * <p>This generator reads chronology YAML files and produces self-contained Java classes that
 * implement {@code ChronologyAlgorithm}. The generated classes contain all the data and logic
 * needed for date conversion, with no external dependencies.
 */
public class ChronologyCodeGenerator {

  private final ObjectMapper mapper;

  public ChronologyCodeGenerator() {
    this.mapper =
        new ObjectMapper(new YAMLFactory())
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
  }

  /**
   * Generates Java source files for all chronologies in a directory.
   *
   * @param inputDir directory containing chronology YAML files
   * @param outputDir directory to write generated Java files
   * @return list of generated file paths
   * @throws IOException if reading or writing fails
   */
  public List<Path> generateAll(Path inputDir, Path outputDir) throws IOException {
    List<Path> generated = new ArrayList<>();

    Files.createDirectories(outputDir);

    try (Stream<Path> paths = Files.walk(inputDir)) {
      List<Path> yamlFiles =
          paths
              .filter(Files::isRegularFile)
              .filter(p -> p.toString().endsWith(".yaml") || p.toString().endsWith(".yml"))
              .toList();

      for (Path yamlFile : yamlFiles) {
        ChronologySpec spec = loadSpec(yamlFile);
        if (spec != null && "chronology".equals(spec.kind())) {
          Path outputFile = generate(spec, outputDir);
          generated.add(outputFile);
        }
      }
    }

    return generated;
  }

  /**
   * Generates a Java source file for a single chronology.
   *
   * @param spec the chronology specification
   * @param outputDir directory to write the generated file
   * @return path to the generated file
   * @throws IOException if writing fails
   */
  public Path generate(ChronologySpec spec, Path outputDir) throws IOException {
    String className = toClassName(spec.id());
    String javaCode = generateClass(spec, className);

    Path outputFile = outputDir.resolve(className + ".java");
    Files.writeString(outputFile, javaCode);

    return outputFile;
  }

  private ChronologySpec loadSpec(Path path) throws IOException {
    try {
      return mapper.readValue(path.toFile(), ChronologySpec.class);
    } catch (Exception e) {
      // Skip files that don't parse as ChronologySpec
      return null;
    }
  }

  private String toClassName(String id) {
    // Convert ID like "HIJRI_UAQ" to "HijriUaqChronology"
    StringBuilder sb = new StringBuilder();
    boolean capitalizeNext = true;
    for (char c : id.toCharArray()) {
      if (c == '_' || c == '-') {
        capitalizeNext = true;
      } else if (capitalizeNext) {
        sb.append(Character.toUpperCase(c));
        capitalizeNext = false;
      } else {
        sb.append(Character.toLowerCase(c));
      }
    }
    sb.append("Chronology");
    return sb.toString();
  }

  private String generateClass(ChronologySpec spec, String className) {
    StringBuilder sb = new StringBuilder();

    // Package and imports
    sb.append("package com.bdc.chronology.generated;\n\n");
    sb.append("import com.bdc.chronology.ontology.ChronologyDate;\n");
    sb.append("import com.bdc.chronology.ontology.algorithms.ChronologyAlgorithm;\n\n");

    // Class javadoc
    sb.append("/**\n");
    sb.append(" * Generated chronology: ").append(spec.metadata().name()).append("\n");
    if (spec.metadata().description() != null) {
      sb.append(" *\n");
      sb.append(" * <p>").append(spec.metadata().description().replace("\n", "\n * ")).append("\n");
    }
    sb.append(" *\n");
    sb.append(" * <p>This class is auto-generated from YAML. Do not edit manually.\n");
    sb.append(" */\n");

    // Class declaration
    sb.append("public final class ")
        .append(className)
        .append(" implements ChronologyAlgorithm {\n\n");

    // Constants
    sb.append("  public static final String ID = \"").append(spec.id()).append("\";\n");
    if (spec.structure() != null && spec.structure().epochJdn() != null) {
      sb.append("  private static final long EPOCH_JDN = ")
          .append(spec.structure().epochJdn())
          .append("L;\n");
    }
    sb.append("\n");

    // Generate based on algorithm type
    String algoType = spec.algorithms() != null ? spec.algorithms().type() : "FORMULA";

    if ("LOOKUP_TABLE".equalsIgnoreCase(algoType)) {
      generateLookupTableFields(sb, spec);
      generateLookupTableMethods(sb, spec, className);
    } else {
      generateFormulaFields(sb, spec);
      generateFormulaMethods(sb, spec, className);
    }

    sb.append("}\n");
    return sb.toString();
  }

  private void generateFormulaFields(StringBuilder sb, ChronologySpec spec) {
    // Month data arrays
    if (spec.structure() != null && spec.structure().months() != null) {
      var months = spec.structure().months();

      sb.append("  private static final int[] DAYS_IN_MONTH = {");
      for (int i = 0; i < months.size(); i++) {
        if (i > 0) sb.append(", ");
        sb.append(months.get(i).days());
      }
      sb.append("};\n");

      // Check if any month has different leap days
      boolean hasLeapVariation =
          months.stream().anyMatch(m -> m.leapDays() != null && m.leapDays() != m.days());

      if (hasLeapVariation) {
        sb.append("  private static final int[] LEAP_DAYS_IN_MONTH = {");
        for (int i = 0; i < months.size(); i++) {
          if (i > 0) sb.append(", ");
          Integer leapDays = months.get(i).leapDays();
          sb.append(leapDays != null ? leapDays : months.get(i).days());
        }
        sb.append("};\n");
      }
    }
    sb.append("\n");
  }

  private void generateFormulaMethods(StringBuilder sb, ChronologySpec spec, String className) {
    int monthCount =
        spec.structure() != null && spec.structure().months() != null
            ? spec.structure().months().size()
            : 12;

    // Constructor
    sb.append("  public ").append(className).append("() {}\n\n");

    // getChronologyId
    sb.append("  @Override\n");
    sb.append("  public String getChronologyId() {\n");
    sb.append("    return ID;\n");
    sb.append("  }\n\n");

    // isLeapYear
    sb.append("  @Override\n");
    sb.append("  public boolean isLeapYear(int year) {\n");
    String leapFormula = spec.algorithms() != null ? spec.algorithms().leapYear() : "false";
    sb.append("    return ").append(leapFormula != null ? leapFormula : "false").append(";\n");
    sb.append("  }\n\n");

    // getDaysInMonth
    boolean hasLeapVariation =
        spec.structure() != null
            && spec.structure().months() != null
            && spec.structure().months().stream()
                .anyMatch(m -> m.leapDays() != null && m.leapDays() != m.days());

    sb.append("  @Override\n");
    sb.append("  public int getDaysInMonth(int year, int month) {\n");
    sb.append("    if (month < 1 || month > ").append(monthCount).append(") {\n");
    sb.append("      throw new IllegalArgumentException(\"Invalid month: \" + month);\n");
    sb.append("    }\n");
    if (hasLeapVariation) {
      sb.append(
          "    return isLeapYear(year) ? LEAP_DAYS_IN_MONTH[month - 1] : DAYS_IN_MONTH[month - 1];\n");
    } else {
      sb.append("    return DAYS_IN_MONTH[month - 1];\n");
    }
    sb.append("  }\n\n");

    // isValidDate
    sb.append("  @Override\n");
    sb.append("  public boolean isValidDate(int year, int month, int day) {\n");
    sb.append("    if (month < 1 || month > ").append(monthCount).append(") return false;\n");
    sb.append("    if (day < 1) return false;\n");
    sb.append("    return day <= getDaysInMonth(year, month);\n");
    sb.append("  }\n\n");

    // toJdn
    sb.append("  @Override\n");
    sb.append("  public long toJdn(int year, int month, int day) {\n");
    sb.append("    long days = daysBeforeYear(year);\n");
    sb.append("    for (int m = 1; m < month; m++) {\n");
    sb.append("      days += getDaysInMonth(year, m);\n");
    sb.append("    }\n");
    sb.append("    days += day - 1;\n");
    sb.append("    return EPOCH_JDN + days;\n");
    sb.append("  }\n\n");

    // fromJdn
    sb.append("  @Override\n");
    sb.append("  public ChronologyDate fromJdn(long jdn) {\n");
    sb.append("    long daysSinceEpoch = jdn - EPOCH_JDN;\n");
    sb.append("    int year = estimateYear(daysSinceEpoch);\n");
    sb.append("    while (daysBeforeYear(year + 1) <= daysSinceEpoch) year++;\n");
    sb.append("    while (daysBeforeYear(year) > daysSinceEpoch) year--;\n");
    sb.append("    long remaining = daysSinceEpoch - daysBeforeYear(year);\n");
    sb.append("    int month = 1;\n");
    sb.append("    while (month <= ").append(monthCount).append(") {\n");
    sb.append("      int dim = getDaysInMonth(year, month);\n");
    sb.append("      if (remaining < dim) break;\n");
    sb.append("      remaining -= dim;\n");
    sb.append("      month++;\n");
    sb.append("    }\n");
    sb.append("    int day = (int) remaining + 1;\n");
    sb.append("    return new ChronologyDate(ID, year, month, day);\n");
    sb.append("  }\n\n");

    // Helper: daysBeforeYear
    sb.append("  private long daysBeforeYear(int year) {\n");
    sb.append("    if (year <= 1) return 0;\n");
    sb.append("    long days = 0;\n");
    sb.append("    for (int y = 1; y < year; y++) {\n");
    sb.append("      for (int m = 1; m <= ").append(monthCount).append("; m++) {\n");
    sb.append("        days += getDaysInMonth(y, m);\n");
    sb.append("      }\n");
    sb.append("    }\n");
    sb.append("    return days;\n");
    sb.append("  }\n\n");

    // Helper: estimateYear
    sb.append("  private int estimateYear(long daysSinceEpoch) {\n");
    sb.append("    return Math.max(1, (int) (daysSinceEpoch / 365) + 1);\n");
    sb.append("  }\n");
  }

  private void generateLookupTableFields(StringBuilder sb, ChronologySpec spec) {
    // For lookup tables, we need month boundary data
    var months = spec.algorithms() != null ? spec.algorithms().months() : null;

    if (months != null && !months.isEmpty()) {
      // Find year range
      int minYear = months.stream().mapToInt(ChronologySpec.MonthEntry::year).min().orElse(1);
      int maxYear = months.stream().mapToInt(ChronologySpec.MonthEntry::year).max().orElse(1);
      int yearCount = maxYear - minYear + 1;

      sb.append("  // Lookup table data\n");
      sb.append("  private static final int MIN_YEAR = ").append(minYear).append(";\n");
      sb.append("  private static final int MAX_YEAR = ").append(maxYear).append(";\n\n");

      // Build compact arrays - one JDN per year-month and one length per year-month
      // Store as flat arrays: index = (year - MIN_YEAR) * 12 + (month - 1)
      long[] jdnData = new long[yearCount * 12];
      int[] lengthData = new int[yearCount * 12];

      for (var entry : months) {
        int idx = (entry.year() - minYear) * 12 + (entry.month() - 1);
        jdnData[idx] = entry.jdn();
        lengthData[idx] = entry.length();
      }

      // Generate JDN array using compact hex format for smaller code
      sb.append("  // Month start JDNs: index = (year - MIN_YEAR) * 12 + (month - 1)\n");
      sb.append("  private static final long[] MONTH_JDN = {\n");
      for (int i = 0; i < jdnData.length; i++) {
        if (i % 12 == 0) {
          sb.append("    // Year ").append(minYear + i / 12).append("\n    ");
        }
        sb.append(jdnData[i]).append("L");
        if (i < jdnData.length - 1) sb.append(",");
        if ((i + 1) % 12 == 0) sb.append("\n");
        else sb.append(" ");
      }
      sb.append("  };\n\n");

      // Generate length array
      sb.append("  // Month lengths\n");
      sb.append("  private static final int[] MONTH_LEN = {\n");
      for (int i = 0; i < lengthData.length; i++) {
        if (i % 12 == 0) {
          sb.append("    // Year ").append(minYear + i / 12).append("\n    ");
        }
        sb.append(lengthData[i]);
        if (i < lengthData.length - 1) sb.append(",");
        if ((i + 1) % 12 == 0) sb.append("\n");
        else sb.append(" ");
      }
      sb.append("  };\n\n");
    }
  }

  private void generateLookupTableMethods(StringBuilder sb, ChronologySpec spec, String className) {
    // Helper method for array index
    sb.append("  private static int idx(int year, int month) {\n");
    sb.append("    return (year - MIN_YEAR) * 12 + (month - 1);\n");
    sb.append("  }\n\n");

    // Constructor
    sb.append("  public ").append(className).append("() {}\n\n");

    // getChronologyId
    sb.append("  @Override\n");
    sb.append("  public String getChronologyId() {\n");
    sb.append("    return ID;\n");
    sb.append("  }\n\n");

    // isLeapYear - for Hijri, a leap year has 355 days (vs 354)
    sb.append("  @Override\n");
    sb.append("  public boolean isLeapYear(int year) {\n");
    sb.append("    if (year < MIN_YEAR || year > MAX_YEAR) return false;\n");
    sb.append("    int total = 0;\n");
    sb.append("    int base = (year - MIN_YEAR) * 12;\n");
    sb.append("    for (int m = 0; m < 12; m++) {\n");
    sb.append("      total += MONTH_LEN[base + m];\n");
    sb.append("    }\n");
    sb.append("    return total == 355;\n");
    sb.append("  }\n\n");

    // getDaysInMonth
    sb.append("  @Override\n");
    sb.append("  public int getDaysInMonth(int year, int month) {\n");
    sb.append("    if (month < 1 || month > 12) {\n");
    sb.append("      throw new IllegalArgumentException(\"Invalid month: \" + month);\n");
    sb.append("    }\n");
    sb.append("    if (year < MIN_YEAR || year > MAX_YEAR) {\n");
    sb.append(
        "      throw new IllegalArgumentException(\"Year \" + year + \" outside supported range [\" + MIN_YEAR + \", \" + MAX_YEAR + \"]\");\n");
    sb.append("    }\n");
    sb.append("    return MONTH_LEN[idx(year, month)];\n");
    sb.append("  }\n\n");

    // isValidDate
    sb.append("  @Override\n");
    sb.append("  public boolean isValidDate(int year, int month, int day) {\n");
    sb.append("    if (year < MIN_YEAR || year > MAX_YEAR) return false;\n");
    sb.append("    if (month < 1 || month > 12) return false;\n");
    sb.append("    if (day < 1) return false;\n");
    sb.append("    return day <= getDaysInMonth(year, month);\n");
    sb.append("  }\n\n");

    // toJdn
    sb.append("  @Override\n");
    sb.append("  public long toJdn(int year, int month, int day) {\n");
    sb.append("    if (year < MIN_YEAR || year > MAX_YEAR) {\n");
    sb.append(
        "      throw new IllegalArgumentException(\"Year \" + year + \" outside supported range\");\n");
    sb.append("    }\n");
    sb.append("    return MONTH_JDN[idx(year, month)] + day - 1;\n");
    sb.append("  }\n\n");

    // fromJdn
    sb.append("  @Override\n");
    sb.append("  public ChronologyDate fromJdn(long jdn) {\n");
    sb.append("    // Binary search for year\n");
    sb.append("    int year = MIN_YEAR;\n");
    sb.append("    for (int y = MIN_YEAR; y <= MAX_YEAR; y++) {\n");
    sb.append("      if (MONTH_JDN[(y - MIN_YEAR) * 12] > jdn) break;\n");
    sb.append("      year = y;\n");
    sb.append("    }\n");
    sb.append("    // Find month\n");
    sb.append("    int base = (year - MIN_YEAR) * 12;\n");
    sb.append("    for (int m = 0; m < 12; m++) {\n");
    sb.append("      long monthStart = MONTH_JDN[base + m];\n");
    sb.append("      int monthLen = MONTH_LEN[base + m];\n");
    sb.append("      if (jdn >= monthStart && jdn < monthStart + monthLen) {\n");
    sb.append("        int day = (int) (jdn - monthStart) + 1;\n");
    sb.append("        return new ChronologyDate(ID, year, m + 1, day);\n");
    sb.append("      }\n");
    sb.append("    }\n");
    sb.append(
        "    throw new IllegalArgumentException(\"JDN \" + jdn + \" outside supported range\");\n");
    sb.append("  }\n");
  }

  /** Main method for command-line usage. */
  public static void main(String[] args) throws IOException {
    if (args.length < 2) {
      System.err.println("Usage: ChronologyCodeGenerator <input-dir> <output-dir>");
      System.exit(1);
    }

    Path inputDir = Path.of(args[0]);
    Path outputDir = Path.of(args[1]);

    ChronologyCodeGenerator generator = new ChronologyCodeGenerator();
    List<Path> generated = generator.generateAll(inputDir, outputDir);

    System.out.println("Generated " + generated.size() + " chronology classes:");
    for (Path path : generated) {
      System.out.println("  " + path);
    }
  }
}
