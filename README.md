# calendar-project

A tool for defining and generating business-day calendars with YAML-based specifications.

## Features

- YAML-based calendar specifications (source of truth)
- Deterministic compilation to static artifacts (CSV/JSON)
- Calendar inheritance and module composition
- Support for Gregorian and Hijri (Islamic) chronologies

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
