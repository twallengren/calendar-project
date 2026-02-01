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
    start_date: 2022-01-01       # Optional: first date the event is active
    end_date: 2030-12-31         # Optional: last date the event is active
    rule: {...}                  # Rule definition (see types below)
```

### Shiftable

Controls whether this event shifts when it falls on a weekend (per the calendar's `weekend_shift_policy`). Defaults to `true` for `fixed_month_day` rules, `false` for others.

### Date Constraints (Timebox)

Use `start_date` and `end_date` to constrain when an event is active:

```yaml
# Juneteenth became a federal holiday in 2021
- key: juneteenth
  name: Juneteenth National Independence Day
  start_date: 2022-01-01
  rule:
    type: fixed_month_day
    month: 6
    day: 19
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
```yaml
rule:
  type: relative_to_reference
  key: good_friday
  name: Good Friday
  reference: easter      # Key of a reference defined in this module
  offset_days: -2        # Days to add (negative = before, positive = after)
```

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

### ISO (Gregorian)

Default chronology. Uses standard ISO dates (YYYY-MM-DD).

### HIJRI (Islamic)

Uses the Islamic civil/tabular calendar. Dates specified as Hijri month/day are automatically converted to ISO dates for the relevant years in the generation range.

Example - Eid al-Fitr (Shawwal 1):

```yaml
rule:
  type: fixed_month_day
  key: eid_al_fitr
  name: Eid al-Fitr
  month: 10        # Shawwal
  day: 1
  chronology: HIJRI
```
