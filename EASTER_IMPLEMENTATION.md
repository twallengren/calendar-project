# Easter Feature Implementation Plan

## Goal
Add two new mechanisms to support Easter-relative holidays:
1. **Reference formulas** - internal date calculations (like Easter) that don't produce events
2. **Relative-to-reference rule** - holidays defined as N days before/after a reference

## New YAML Syntax

### Good Friday Module (with Easter as internal reference)
```yaml
kind: module
id: good_friday

references:
  - key: easter
    formula: EASTER_WESTERN

event_sources:
  - key: good_friday
    name: Good Friday
    default_classification: CLOSED
    rule:
      type: relative_to_reference
      key: good_friday
      name: Good Friday
      reference: easter
      offset_days: -2
```

The `references` section defines computed dates that rules can reference without producing calendar events.

## Files to Modify

### 1. `tools/src/main/java/com/bdc/model/Rule.java`
Add new record type to sealed interface:

```java
record RelativeToReference(
    String key,
    String name,
    String reference,
    int offsetDays
) implements Rule {}
```

Update `@JsonSubTypes` to include `relative_to_reference`.

### 2. New: `tools/src/main/java/com/bdc/model/Reference.java`
```java
public record Reference(
    String key,
    String formula
) {}
```

### 3. `tools/src/main/java/com/bdc/model/ModuleSpec.java`
Add `references` field to the record:
```java
public record ModuleSpec(
    String kind,
    String id,
    List<String> uses,
    Policies policies,
    List<Reference> references,  // NEW
    @JsonProperty("event_sources")
    List<EventSource> eventSources
)
```

### 4. `tools/src/main/java/com/bdc/model/ResolvedSpec.java`
Add `references` field:
```java
public record ResolvedSpec(
    String id,
    CalendarSpec.Metadata metadata,
    WeekendPolicy weekendPolicy,
    List<Reference> references,  // NEW
    List<EventSource> eventSources,
    Map<String, EventType> classifications,
    List<Delta> deltas,
    List<String> resolutionChain
)
```

### 5. `tools/src/main/java/com/bdc/resolver/SpecResolver.java`
Update `resolveModuleRecursive()` to collect references:
```java
// Add to method signature and body:
List<Reference> mergedReferences = new ArrayList<>();
// In resolveModuleRecursive:
if (module.references() != null) {
    mergedReferences.addAll(module.references());
}
```

### 6. New: `tools/src/main/java/com/bdc/formula/EasterCalculator.java`
```java
package com.bdc.formula;

import java.time.LocalDate;

public class EasterCalculator {
    /**
     * Calculates Easter Sunday using Gauss' algorithm for Western (Catholic/Protestant) Easter.
     * Valid for years in the Gregorian calendar (1583 onwards).
     */
    public static LocalDate westernEaster(int year) {
        int a = year % 19;
        int b = year / 100;
        int c = year % 100;
        int d = b / 4;
        int e = b % 4;
        int f = (b + 8) / 25;
        int g = (b - f + 1) / 3;
        int h = (19 * a + b - d - g + 15) % 30;
        int i = c / 4;
        int k = c % 4;
        int l = (32 + 2 * e + 2 * i - h - k) % 7;
        int m = (a + 11 * h + 22 * l) / 451;
        int month = (h + l - 7 * m + 114) / 31;
        int day = ((h + l - 7 * m + 114) % 31) + 1;
        return LocalDate.of(year, month, day);
    }
}
```

### 7. New: `tools/src/main/java/com/bdc/formula/ReferenceResolver.java`
```java
package com.bdc.formula;

import com.bdc.chronology.DateRange;
import com.bdc.model.Reference;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ReferenceResolver {
    private final Map<String, List<LocalDate>> resolved = new HashMap<>();

    public void resolve(List<Reference> references, DateRange range) {
        if (references == null) return;

        int[] years = range.isoYearRange();
        for (Reference ref : references) {
            List<LocalDate> dates = new ArrayList<>();
            for (int year = years[0]; year <= years[1]; year++) {
                LocalDate date = switch (ref.formula()) {
                    case "EASTER_WESTERN" -> EasterCalculator.westernEaster(year);
                    default -> throw new IllegalArgumentException("Unknown formula: " + ref.formula());
                };
                if (range.contains(date)) {
                    dates.add(date);
                }
            }
            resolved.put(ref.key(), dates);
        }
    }

    public List<LocalDate> getDates(String key) {
        return resolved.getOrDefault(key, List.of());
    }
}
```

### 8. `tools/src/main/java/com/bdc/generator/RuleExpander.java`
- Add `ReferenceResolver` field
- Add setter method
- Update switch statement:
```java
case Rule.RelativeToReference r -> expandRelativeToReference(r, range, provenance);
```
- Add expansion method:
```java
private List<Occurrence> expandRelativeToReference(
        Rule.RelativeToReference rule, DateRange range, String provenance) {
    if (referenceResolver == null) {
        throw new IllegalStateException("ReferenceResolver not set");
    }
    List<LocalDate> refDates = referenceResolver.getDates(rule.reference());
    if (refDates.isEmpty()) {
        throw new IllegalArgumentException("Unknown reference: " + rule.reference());
    }
    List<Occurrence> occurrences = new ArrayList<>();
    for (LocalDate refDate : refDates) {
        LocalDate date = refDate.plusDays(rule.offsetDays());
        if (range.contains(date)) {
            occurrences.add(new Occurrence(rule.key(), date, rule.name(), provenance));
        }
    }
    return occurrences;
}
```

### 9. `tools/src/main/java/com/bdc/generator/EventGenerator.java`
Update `generate()` to resolve references before rule expansion:
```java
public List<Event> generate(ResolvedSpec spec, LocalDate from, LocalDate to) {
    DateRange range = new DateRange(from, to);

    // NEW: Resolve references first (two-pass approach)
    ReferenceResolver refResolver = new ReferenceResolver();
    refResolver.resolve(spec.references(), range);
    ruleExpander.setReferenceResolver(refResolver);

    // Rest unchanged...
}
```

### 10. Update `modules/holidays/good_friday.yaml`
```yaml
kind: module
id: good_friday

references:
  - key: easter
    formula: EASTER_WESTERN

event_sources:
  - key: good_friday
    name: Good Friday
    default_classification: CLOSED
    rule:
      type: relative_to_reference
      key: good_friday
      name: Good Friday
      reference: easter
      offset_days: -2
```

### 11. Update `spec/SPEC.md`
Add documentation for:

#### References (new section)
```yaml
references:
  - key: string      # Unique identifier for this reference
    formula: string  # Formula to compute dates (e.g., EASTER_WESTERN)
```

Available formulas:
- `EASTER_WESTERN` - Western (Catholic/Protestant) Easter using Gauss' algorithm

#### relative_to_reference (new rule type)
```yaml
rule:
  type: relative_to_reference
  key: good_friday
  name: Good Friday
  reference: easter      # Key of a reference defined in this module
  offset_days: -2        # Days to add (negative = before, positive = after)
```

### 12. Add tests

**`tools/src/test/java/com/bdc/formula/EasterCalculatorTest.java`**
```java
package com.bdc.formula;

import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;

class EasterCalculatorTest {

    @Test
    void easter2024() {
        assertEquals(LocalDate.of(2024, 3, 31), EasterCalculator.westernEaster(2024));
    }

    @Test
    void easter2025() {
        assertEquals(LocalDate.of(2025, 4, 20), EasterCalculator.westernEaster(2025));
    }

    @Test
    void easter2026() {
        assertEquals(LocalDate.of(2026, 4, 5), EasterCalculator.westernEaster(2026));
    }

    @Test
    void easter2027() {
        assertEquals(LocalDate.of(2027, 3, 28), EasterCalculator.westernEaster(2027));
    }

    @Test
    void easter2028() {
        assertEquals(LocalDate.of(2028, 4, 16), EasterCalculator.westernEaster(2028));
    }
}
```

**`tools/src/test/java/com/bdc/generator/RuleExpanderTest.java`** (additions)
```java
@Test
void expandRelativeToReference_goodFriday() {
    // Set up reference resolver with Easter
    ReferenceResolver refResolver = new ReferenceResolver();
    refResolver.resolve(
        List.of(new Reference("easter", "EASTER_WESTERN")),
        range2024
    );
    expander.setReferenceResolver(refResolver);

    Rule.RelativeToReference rule = new Rule.RelativeToReference(
        "good_friday", "Good Friday", "easter", -2
    );

    List<Occurrence> occurrences = expander.expand(rule, range2024, "test");

    assertEquals(1, occurrences.size());
    // Easter 2024 is March 31, Good Friday is March 29
    assertEquals(LocalDate.of(2024, 3, 29), occurrences.get(0).date());
}
```

## Implementation Order

1. `EasterCalculator.java` + `EasterCalculatorTest.java`
2. `Reference.java` model
3. `ReferenceResolver.java`
4. Update `ModuleSpec.java` (add references field)
5. Update `ResolvedSpec.java` (add references field)
6. Update `SpecResolver.java` (collect references during resolution)
7. Add `RelativeToReference` to `Rule.java`
8. Update `RuleExpander.java` (add setter + expansion logic)
9. Update `EventGenerator.java` (wire up reference resolution)
10. Update `good_friday.yaml`
11. Add/update tests in `RuleExpanderTest.java`
12. Update `spec/SPEC.md`
13. Run full test suite

## Verification

1. Run tests:
   ```bash
   cd tools && ./gradlew test
   ```

2. Regenerate US-NYSE calendar and verify Good Friday dates match the original explicit dates:
   - 2024-03-29 (Easter 2024-03-31 minus 2 days) ✓
   - 2025-04-18 (Easter 2025-04-20 minus 2 days) ✓
   - 2026-04-03 (Easter 2026-04-05 minus 2 days) ✓
   - 2027-03-26 (Easter 2027-03-28 minus 2 days) ✓
   - 2028-04-14 (Easter 2028-04-16 minus 2 days) ✓

## Future Extensions

This design supports future additions:
- **Additional formulas**: `EASTER_ORTHODOX`, `PASSOVER`, `DIWALI`, etc.
- **Additional relative holidays**: Easter Monday (+1), Ascension Day (+39), Pentecost (+49)
- **Cross-module references**: References could potentially be defined in separate modules and imported
