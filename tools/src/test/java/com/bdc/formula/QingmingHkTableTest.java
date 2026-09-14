package com.bdc.formula;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.bdc.chronology.DateRange;
import com.bdc.model.Reference;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Rows transcribed from HKO annual Gregorian-Lunar conversion text tables. */
class QingmingHkTableTest {
  @Test
  void returnsEveryRetainedHkoBrightAndClearDate() throws Exception {
    var hkoDate = DateTimeFormatter.ofPattern("uuuu/M/d");
    for (int year = 2016; year <= 2029; year++) {
      Path original = Path.of("sources/HK-HKEX/hko/T" + year + "e.txt");
      List<String> rows =
          Files.readAllLines(original, StandardCharsets.ISO_8859_1).stream()
              .filter(row -> row.contains("Bright & Clear"))
              .toList();
      assertEquals(1, rows.size(), original.toString());
      LocalDate published = LocalDate.parse(rows.getFirst().trim().split("\\s+", 2)[0], hkoDate);
      assertEquals(published, QingmingHkTable.date(year), original.toString());
    }
  }

  @Test
  void referenceResolverUsesTableAndRejectsOutsideIt() {
    var resolver = new ReferenceResolver();
    resolver.resolve(
        List.of(new Reference("ching_ming", "QINGMING_HK")),
        new DateRange(LocalDate.of(2018, 1, 1), LocalDate.of(2027, 12, 31)));
    assertEquals(10, resolver.getDates("ching_ming").size());
    assertThrows(
        IllegalArgumentException.class,
        () ->
            resolver.resolve(
                List.of(new Reference("ching_ming", "QINGMING_HK")),
                new DateRange(LocalDate.of(2015, 1, 1), LocalDate.of(2016, 12, 31))));
    assertThrows(IllegalArgumentException.class, () -> QingmingHkTable.date(2030));
  }
}
