# calendar-project

A tool for defining and generating business-day calendars with YAML-based specifications.

## Features

- YAML-based calendar specifications (source of truth)
- Deterministic compilation to static artifacts (CSV/JSON)
- Calendar inheritance and module composition
- Multi-chronology support: ISO (Gregorian), HIJRI (tabular Islamic), UMM_AL_QURA (Saudi lookup table), JULIAN, PERSIAN, and extensible via YAML
- Julian Day Number (JDN) pivot for cross-calendar translation
- Effective-dated weekends, per-holiday observance rules, early-close times, confirmed/projected status
- Every holiday cites its source (`sources/`); cross-validated against exchange_calendars and QuantLib

## Quick Start

All commands run from the repository root.

### Build

```bash
./gradlew :tools:build
```

### Validate

```bash
./gradlew :tools:run --args="validate --all --strict"
./gradlew :tools:run --args="validate US-NYSE"
```

### Generate Calendar Events

```bash
./gradlew :tools:run --args="generate US-NYSE --from 2024-01-01 --to 2024-12-31 --out generated/US-NYSE"
```

This produces:
- `events.csv` - All events in the date range (`date,type,description,key,source_module,observed_from,close_time,status`)
- `events.json` - The same events with calendar id, timezone, range and coverage
- `metadata.json` - Calendar metadata and statistics

### Query

```bash
./gradlew :tools:run --args="query US-NYSE --is-business-day 2021-12-31"
./gradlew :tools:run --args="query US-NYSE --as-of v10.1.0 --is-business-day 2021-12-31"   # from a published release
./gradlew :tools:run --args="history releases US-NYSE"
```

### Resolve a Calendar

```bash
./gradlew :tools:run --args="resolve US-MARKET-BASE --out build/resolved/US-MARKET-BASE.yaml"
```

### Re-bless

```bash
scripts/bless.sh            # regenerates blessed/ from the manifest ranges; a no-op leaves git clean
```

## Directory Structure

```
calendar-project/
├── tools/              # Java/Gradle CLI tool
├── spec/               # YAML specification docs
├── calendars/          # Calendar YAML specs
├── modules/            # Reusable modules
├── chronologies/       # Chronology definitions (ISO, Julian, Persian, etc.)
├── sources/            # Authoritative source documents and citation tables per market
├── generated/          # Local development/testing output (gitignored)
├── blessed/            # Latest published release artifacts
├── release-history/    # Previous releases, for as-of queries and audit
└── scripts/            # bless.sh, reference-data export, git hooks
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
date,type,description,key,source_module,observed_from,close_time,status
2024-01-01,CLOSED,New Year's Day,new_years_day,module:new_years_day,,,CONFIRMED
2024-07-03,EARLY_CLOSE,Independence Day Eve (Early Close),independence_day_eve,module:independence_day_eve,,13:00,CONFIRMED
2024-07-04,CLOSED,Independence Day,independence_day,module:independence_day,,,CONFIRMED
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
| `HIJRI` | Islamic calendar (tabular arithmetic approximation) |
| `UMM_AL_QURA` | Umm al-Qura calendar (Saudi Arabia, lookup table AH 1356-1500) |
| `JULIAN` | Julian calendar |
| `PERSIAN` | Solar Hijri calendar (Iranian) |

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
