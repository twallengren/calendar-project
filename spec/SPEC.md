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
event_sources: [...]          # Event source definitions
classifications:
  event-key: CLOSED | NOTABLE | PERIOD_MARKER
deltas: [...]                 # Modifications
```

## Module Spec

```yaml
kind: module
id: string
references:
  - key: string      # Unique identifier for this reference
    formula: string  # Formula to compute dates (e.g., EASTER_WESTERN)
policies:
  weekends: [SATURDAY, SUNDAY]
event_sources: [...]
```

## Event Source Types

### explicit_dates

```yaml
rule:
  type: explicit_dates
  key: good_friday
  name: Good Friday
  dates:
    - 2024-03-29
    - 2025-04-18
```

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
- `NOTABLE` - Notable event (not closure)
- `PERIOD_MARKER` - Period boundary marker

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
