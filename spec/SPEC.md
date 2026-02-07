# Calendar Specification

This document defines the YAML schema for calendar and module specifications.

## Calendar Spec

```yaml
kind: calendar
id: string                    # Unique identifier
metadata:
  name: string                # Human-readable name
  description: string         # Optional description
  chronology: ISO             # ISO (default) or HIJRI
extends: [calendar-ids]       # Parent calendars to inherit from
uses: [module-ids]            # Modules to include
weekend_shift_policy: NONE    # How to handle holidays on weekends (see below)
event_sources: [...]          # Event source definitions
classifications:
  event-key: CLOSED | NOTABLE | PERIOD_MARKER
deltas: [...]                 # Modifications
```

## Module Spec

```yaml
kind: module
id: string
uses: [module-ids]            # Other modules to include (for composing groups)
references:
  - key: string               # Unique identifier for this reference
    formula: string           # Formula to compute dates (e.g., EASTER_WESTERN)
policies:
  weekends: [SATURDAY, SUNDAY]
event_sources: [...]
```

Modules can compose other modules using `uses` to create holiday groups:

```yaml
kind: module
id: us_nyse_holidays
uses:
  - us_market_base
  - mlk_day
  - presidents_day
  - good_friday
```

## Event Source Structure

Each event source has the following fields:

```yaml
event_sources:
  - key: string                  # Unique identifier
    name: string                 # Display name
    default_classification: CLOSED  # Event type (default: CLOSED)
    shiftable: true              # Whether to shift on weekends (see below)
    active_years: [...]          # Optional: list of active year ranges (see below)
    rule: {...}                  # Rule definition (see types below)
```

### Shiftable

Controls whether this event shifts when it falls on a weekend (per the calendar's `weekend_shift_policy`). Defaults to `true` for `fixed_month_day` rules, `false` for others.

### Active Years

Use `active_years` to specify which years the event is active. Supports:

- Single years: `1972`
- Open-ended ranges: `[null, 1968]` (from inception through 1968) or `[2022, null]` (from 2022 onwards)
- Closed ranges: `[1990, 2000]` (1990 through 2000 inclusive)

If `active_years` is omitted, the event is active for all years.

```yaml
# Juneteenth became a federal holiday in 2021
- key: juneteenth
  name: Juneteenth National Independence Day
  active_years:
    - [2022, null]   # Active from 2022 onwards
  rule:
    type: fixed_month_day
    month: 6
    day: 19
```

```yaml
# Election Day: every year through 1968, then only presidential years
- key: election_day
  name: Election Day
  active_years:
    - [null, 1968]   # Annual through 1968
    - 1972           # Presidential election
    - 1976           # Presidential election
    - 1980           # Presidential election
  rule:
    type: relative_to_reference
    reference_month: 11
    reference_day: 1
    offset_weekday:
      weekday: TUESDAY
      nth: 1
      direction: AFTER
```

## Event Source Rule Types

### explicit_dates

List specific dates, optionally with comments:

```yaml
rule:
  type: explicit_dates
  key: good_friday
  name: Good Friday
  dates:
    - 2024-03-29
    - 2025-04-18
```

With comments (for historical context):

```yaml
rule:
  type: explicit_dates
  key: national_mourning
  name: National Day of Mourning
  dates:
    - date: 1994-04-27
      comment: Richard Nixon
    - date: 2004-06-11
      comment: Ronald Reagan
```

The comment is appended to the event name in output: "National Day of Mourning (Richard Nixon)"

### fixed_month_day

```yaml
rule:
  type: fixed_month_day
  key: christmas
  name: Christmas Day
  month: 12
  day: 25
  chronology: ISO    # or HIJRI
```

### nth_weekday_of_month

```yaml
rule:
  type: nth_weekday_of_month
  key: labor_day
  name: Labor Day
  month: 9
  weekday: MONDAY
  nth: 1             # 1 = first, -1 = last
```

### relative_to_reference

Calculate dates relative to a reference point. Supports two reference types and two offset types.

#### With Named Reference (e.g., Easter)

```yaml
rule:
  type: relative_to_reference
  key: good_friday
  name: Good Friday
  reference: easter      # Key of a reference defined in this module
  offset_days: -2        # Days to add (negative = before, positive = after)
```

#### With Fixed Month/Day Reference

```yaml
rule:
  type: relative_to_reference
  key: week_after_nov1
  name: Week After November 1st
  reference_month: 11
  reference_day: 1
  offset_days: 7
```

#### With Weekday Offset

Find the nth weekday before or after a reference date. Useful for rules like "first Tuesday after November 1st" (Election Day).

```yaml
rule:
  type: relative_to_reference
  key: election_day
  name: Election Day
  reference_month: 11
  reference_day: 1
  offset_weekday:
    weekday: TUESDAY     # MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY, SUNDAY
    nth: 1               # 1 = first, 2 = second, etc.
    direction: AFTER     # AFTER or BEFORE (strictly after/before, not including reference date)
```

Note: The weekday offset finds occurrences strictly before or after the reference date. If November 1st is a Tuesday and you're looking for the first Tuesday after November 1st, you'll get November 8th (not November 1st).

## Event Types

- `CLOSED` - Market/business closed
- `EARLY_CLOSE` - Early closure (partial day)
- `NOTABLE` - Notable event (not closure)
- `PERIOD_MARKER` - Period boundary marker
- `WEEKEND` - Weekend day (generated automatically based on weekend policy)

## Weekend Shift Policy

Controls how holidays that fall on weekends are observed. Set at the calendar level.

- `NONE` - No shifting; weekend holidays stay on weekends (default)
- `NEAREST_WEEKDAY` - US-style: Saturday shifts to Friday, Sunday shifts to Monday (independent)
- `NEXT_AVAILABLE_WEEKDAY` - UK-style: Weekend holidays shift to next available weekday (cascading)

Example:

```yaml
kind: calendar
id: US-NYSE
weekend_shift_policy: NEAREST_WEEKDAY
uses:
  - weekend_sat_sun
  - us_nyse_holidays
```

## Delta Operations

### Add

```yaml
- action: add
  key: special_close
  name: Special Early Close
  date: 2024-12-24
  classification: NOTABLE
```

### Remove

```yaml
- action: remove
  key: some_event
  date: 2024-01-01
```

### Reclassify

```yaml
- action: reclassify
  key: some_event
  date: 2024-01-01
  new_classification: NOTABLE
```

## Resolution Order

1. Resolve `extends` (parent calendars) in order
2. Resolve `uses` (modules) in order
3. Merge local content
4. Apply deltas in order
5. Normalize output

## Chronology Support

The calendar system supports multiple chronologies (calendar systems) through a YAML-based ontology. All chronologies use Julian Day Number (JDN) as a universal pivot for cross-calendar translation.

### Built-in Chronologies

| ID | Name | Description |
|----|------|-------------|
| `ISO` | Gregorian Calendar | Default. Standard ISO dates (YYYY-MM-DD) |
| `HIJRI` | Islamic Calendar | Umm al-Qura variant via Java's HijrahChronology |
| `JULIAN` | Julian Calendar | Historical Julian calendar (every 4th year is leap) |

### Using Chronologies in Rules

Specify a chronology in `fixed_month_day` rules:

```yaml
rule:
  type: fixed_month_day
  key: eid_al_fitr
  name: Eid al-Fitr
  month: 10        # Shawwal
  day: 1
  chronology: HIJRI
```

The date is automatically converted to ISO dates for each relevant year in the generation range.

### Year Ranges

When working with non-ISO chronologies, year ranges are automatically translated:

```java
DateRange range = new DateRange(LocalDate.of(2024, 1, 1), LocalDate.of(2025, 12, 31));
int[] hijriYears = range.yearRange("HIJRI");  // Returns [1445, 1447] approximately
int[] julianYears = range.yearRange("JULIAN"); // Returns [2023, 2025]
```

## Chronology Ontology

New chronologies can be added without code changes using YAML definitions.

### Chronology Spec Schema

```yaml
kind: chronology
id: string                    # Unique identifier (e.g., JULIAN, PERSIAN)

metadata:
  name: string                # Human-readable name
  description: string         # Description of the calendar

structure:
  epoch_jdn: number           # Julian Day Number of the epoch (year 1, month 1, day 1)
  week:
    days_per_week: 7
    first_day: MONDAY         # First day of the week
  months:
    - name: string            # Month name
      days: number            # Days in common year
      leap_days: number       # Days in leap year (optional, if different)

algorithms:
  type: FORMULA | LOOKUP_TABLE | METONIC_CYCLE
  leap_year: string           # Leap year formula (for FORMULA type)
  table: string               # Table ID (for LOOKUP_TABLE type)
  fallback: string            # Fallback algorithm ID (for LOOKUP_TABLE type)
```

### Algorithm Types

**FORMULA** - Simple algorithmic calendars with a leap year formula:

```yaml
kind: chronology
id: JULIAN
metadata:
  name: Julian Calendar
  description: Calendar introduced by Julius Caesar

structure:
  epoch_jdn: 1721424
  months:
    - {name: January, days: 31}
    - {name: February, days: 28, leap_days: 29}
    - {name: March, days: 31}
    # ... etc

algorithms:
  type: FORMULA
  leap_year: "year % 4 == 0"
```

**Supported leap year formula expressions:**
- Variables: `year`
- Operators: `%` (modulo), `==`, `!=`, `&&`, `||`
- Parentheses for grouping
- Boolean literals: `true`, `false`

Examples:
- Julian: `"year % 4 == 0"`
- Gregorian: `"(year % 4 == 0 && year % 100 != 0) || (year % 400 == 0)"`

**LOOKUP_TABLE** - Observation-based calendars (e.g., Islamic Hijri):

```yaml
kind: chronology
id: HIJRI_UAQ
metadata:
  name: Islamic Calendar (Umm al-Qura)

algorithms:
  type: LOOKUP_TABLE
  table: HIJRI_UAQ_TABLE
  fallback: HIJRI_TABULAR
```

**METONIC_CYCLE** - Calendars with 19-year cycles (e.g., Hebrew):

```yaml
kind: chronology
id: HEBREW
metadata:
  name: Hebrew Calendar

algorithms:
  type: METONIC_CYCLE
  cycle_length: 19
  leap_years: [3, 6, 8, 11, 14, 17, 19]
```

### Chronology Table Schema

For lookup-based calendars:

```yaml
kind: chronology_table
id: string                    # Unique identifier

metadata:
  name: string
  description: string
  source: string              # Data source or authority
  valid_from: string          # Earliest valid date
  valid_to: string            # Latest valid date

entries:
  - year: number              # Year in the chronology
    month: number             # Month (1-based, optional for year tables)
    jdn: number               # Julian Day Number of first day
    length: number            # Number of days (for month tables)
```

### Code Generation

Chronologies are compiled from YAML to Java source code at build time:

```bash
./gradlew generateChronologies
```

This reads YAML files from `chronologies/` and generates Java classes in `tools/build/generated/sources/chronology/`. The generated classes are self-contained with all conversion logic and data embedded - no runtime YAML parsing or external dependencies.

**Example generated class structure:**
- Formula calendars: Contains leap year logic and month definitions
- Lookup table calendars: Contains embedded JDN/length arrays for all months

### Extracting Calendar Data

For observation-based calendars, data can be extracted from external sources:

```bash
# Extract Umm al-Qura Hijri data from Java's built-in HijrahChronology
./gradlew extractHijriData
```

This generates a complete YAML spec with embedded lookup table data.

### Directory Structure

```
chronologies/
  iso.yaml
  julian.yaml
  persian.yaml
  hebrew.yaml
  hijri/
    hijri_tabular.yaml
    hijri_umm_al_qura.yaml
    tables/
      umm_al_qura_data.yaml
```

## Julian Day Number

Julian Day Number (JDN) is a continuous count of days since the beginning of the Julian Period (January 1, 4713 BCE). It serves as a universal pivot for converting between calendar systems.

### Key Epochs

| Calendar | Epoch Date | JDN |
|----------|------------|-----|
| Gregorian | January 1, 1 CE | 1721426 |
| Julian | January 1, 1 CE | 1721424 |
| Hijri | July 16, 622 CE (Julian) | 1948440 |

### Cross-Calendar Conversion

All conversions go through JDN:

```
Source Calendar → JDN → Target Calendar
```

Example: Convert Hijri 1446-06-15 to ISO date:

```java
ChronologyRegistry registry = ChronologyRegistry.getInstance();

// Hijri to JDN
long jdn = registry.getAlgorithm("HIJRI").toJdn(1446, 6, 15);

// JDN to ISO
ChronologyDate isoDate = registry.fromJdn(jdn, "ISO");
LocalDate localDate = isoDate.toIsoDate();
```

Or using the facade:

```java
LocalDate isoDate = ChronologyTranslator.toIsoDate(1446, 6, 15, "HIJRI");
```

### ChronologyDate Record

A chronology-agnostic date representation:

```java
// Create a date
ChronologyDate date = new ChronologyDate("HIJRI", 1446, 6, 15);

// Convert to JDN
long jdn = date.toJdn();

// Convert to ISO LocalDate
LocalDate isoDate = date.toIsoDate();

// Convert to another chronology
ChronologyDate julianDate = date.toChronology("JULIAN");

// Factory methods
ChronologyDate isoDate = ChronologyDate.iso(2025, 6, 15);
ChronologyDate fromIso = ChronologyDate.fromIsoDate(LocalDate.now(), "HIJRI");
```
