# calendar-project

A tool for defining and generating business-day calendars with YAML-based specifications.

## Features

- YAML-based calendar specifications (source of truth)
- Deterministic compilation to static artifacts (CSV/JSON)
- Calendar inheritance and module composition
- Multi-chronology support: ISO (Gregorian), HIJRI (Islamic), JULIAN, and extensible via YAML
- Julian Day Number (JDN) pivot for cross-calendar translation

## Quick Start

### Build

```bash
cd tools
./gradlew build
```

### Validate a Calendar

```bash
./gradlew run --args="validate US-MARKET-BASE"
```

### Generate Calendar Events

For local development/testing:
```bash
./gradlew run --args="generate US-MARKET-BASE --from 2024-01-01 --to 2024-12-31 --out generated/US-MARKET-BASE"
```

To store as a versioned artifact (for bitemporality):
```bash
./gradlew run --args="generate US-MARKET-BASE --from 2024-01-01 --to 2024-12-31 --store"
```

This produces:
- `events.csv` - All events in the date range
- `metadata.json` - Calendar metadata and statistics

### Resolve a Calendar

```bash
./gradlew run --args="resolve US-MARKET-BASE --out build/resolved/US-MARKET-BASE.yaml"
```

## Directory Structure

```
calendar-project/
├── tools/              # Java/Gradle CLI tool
├── spec/               # YAML specification docs
├── calendars/          # Calendar YAML specs
├── modules/            # Reusable modules
├── chronologies/       # Chronology definitions (ISO, Julian, Persian, etc.)
├── generated/          # Local development/testing output
├── blessed/            # Latest published versions (e.g., NYSE:latest)
├── artifacts/          # Historical versions for bitemporality (e.g., NYSE:<hash>)
└── docs/               # Additional documentation
```

## Example Calendar

```yaml
kind: calendar
id: US-MARKET-BASE

metadata:
  name: US Market Base Calendar
  chronology: ISO

uses:
  - weekend_sat_sun

event_sources:
  - key: christmas
    name: Christmas Day
    default_classification: CLOSED
    rule:
      type: fixed_month_day
      month: 12
      day: 25
```

## Output Format

### events.csv

```csv
date,type,description
2024-01-01,CLOSED,New Year's Day
2024-07-04,CLOSED,Independence Day
```

### metadata.json

```json
{
  "calendar_id": "US-MARKET-BASE",
  "event_count": 6,
  "counts_by_type": {
    "CLOSED": 6
  }
}
```

## Chronology Support

The system supports multiple calendar systems through a YAML-based ontology:

| Chronology | Description |
|------------|-------------|
| `ISO` | Gregorian calendar (default) |
| `HIJRI` | Islamic calendar (Umm al-Qura) |
| `JULIAN` | Julian calendar |

### Using Non-ISO Chronologies

```yaml
event_sources:
  - key: eid_al_fitr
    name: Eid al-Fitr
    rule:
      type: fixed_month_day
      month: 10      # Shawwal
      day: 1
      chronology: HIJRI
```

### Adding Custom Chronologies

New calendars can be defined in YAML without code changes. See `chronologies/` directory for examples and `spec/SPEC.md` for the full schema.

```yaml
kind: chronology
id: MY_CALENDAR
metadata:
  name: My Custom Calendar
structure:
  epoch_jdn: 1721424
  months:
    - {name: Month1, days: 30}
    - {name: Month2, days: 29, leap_days: 30}
algorithms:
  type: FORMULA
  leap_year: "year % 4 == 0"
```
