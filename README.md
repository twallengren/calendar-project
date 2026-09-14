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

## Get the data

Every release publishes the generated artifacts several zero-install ways, so you rarely need to
clone this repository or build the tool just to read a calendar. All examples below use
**US-NYSE**; swap in any other ID from the [Market status](#market-status) table.

**(a) GitHub Pages JSON API** — the current release's [JSON API v1](spec/SPEC.md#json-api-v1),
served statically from `main`, no auth, no rate limit:
- Index of every published calendar: <https://twallengren.github.io/calendar-project/v1/index.json>
- One calendar-year: <https://twallengren.github.io/calendar-project/v1/calendars/US-NYSE/2027.json>
- All holidays for a calendar: <https://twallengren.github.io/calendar-project/v1/calendars/US-NYSE/holidays.json>
- Subscribe in any calendar app (updates as the site is republished, 2020 onward):
  `webcal://twallengren.github.io/calendar-project/v1/calendars/US-NYSE/holidays-recent.ics`

**(b) jsDelivr**, pointed at any tagged release, for a URL that never changes underneath you:
```
https://cdn.jsdelivr.net/gh/twallengren/calendar-project@v11.0.0/blessed/US-NYSE/events.json
```
jsDelivr caches tagged files **permanently** — if a release needs a correction, it ships under a
new tag, not by overwriting the old one, so pin a version and re-point at the new tag when you
upgrade.

**(c) GitHub Release assets** — loose per-calendar files attached to each
[release](https://github.com/twallengren/calendar-project/releases), no unpacking required:
```
https://github.com/twallengren/calendar-project/releases/download/v11.0.0/US-NYSE-events.csv
```
(`<ID>-events.json` and, for market calendars, `<ID>-holidays.ics` are attached the same way.) A
combined `tar.gz`/`zip` with every calendar's events/metadata/specs, plus `checksums.txt`, is
attached to the same release for bulk use.

**(d) The [`bdc-calendars`](python/README.md) Python package**, which ships the same data inside
its wheel and answers the [Query API](spec/SPEC.md#query-api) offline with **zero runtime
dependencies**:
```
pip install bdc-calendars
```
then `bdc_calendars.get_calendar("XNYS")` (exchange_calendars MICs are accepted as aliases). Its
version is `0.<data major>.<data minor>`, so it tracks the data release; see
[`python/README.md`](python/README.md) for the API, the migration notes from exchange_calendars
and the coverage/status caveats. An optional `bdc-calendars-mcp` MCP server
(`pip install "bdc-calendars[mcp]"`) exposes the same Query API to AI agents over stdio.

**(e) The Java library** — the same Query API the CLI answers from, as two jars: `bdc-calendar-core`
(the query API, **no third-party dependencies**) and `bdc-calendar-data` (the published calendars as
classpath resources). Both carry the data release as their version, so the coordinate names the
dataset:
```kotlin
dependencies {
    implementation("io.github.twallengren:bdc-calendar-core:11.0.0")
    runtimeOnly("io.github.twallengren:bdc-calendar-data:11.0.0")
}
```
```java
DateStream nyse = BusinessCalendars.of("US-NYSE");   // or the MIC, "XNYS"
nyse.isBusinessDay(LocalDate.of(2021, 12, 31));      // true
nyse.closeTime(LocalDate.of(2025, 7, 3));            // Optional[13:00]
BusinessCalendars.joint("US-NYSE", "SA-TADAWUL")     // open only where both are
    .nthBusinessDay(LocalDate.of(2026, 2, 25), 2);   // 2026-03-02
```
*Pending the Maven Central namespace* — until it is claimed, build the jars locally with
`./gradlew publishToMavenLocal` and add `mavenLocal()` to your repositories.

**(f) Point-in-time history** — every option above gives you the *current* release. To ask what a
calendar looked like as of an earlier release (audit, backtest reproducibility), use the CLI's
`--as-of` against a checked-out copy, which reads `release-history/`:
```bash
./gradlew :tools:run --args="query US-NYSE --as-of v10.1.0 --is-business-day 2021-12-31"
./gradlew :tools:run --args="history releases US-NYSE"
```

## Browse

`./gradlew :tools:run --args="site --out site --base-url <url>"` builds the whole public site into
one directory with no framework, no build step and no backend: the [`/v1/` JSON
API](spec/SPEC.md#json-api-v1), the release changelog, and a browsable HTML page per market
(`/<ID>/`), per year (`/<ID>/<year>/` — a month grid plus a dated closure table) and per closure
(`/<ID>/<date>/`), with `sitemap.xml` and `robots.txt`. The pages render from the published JSON
API rather than from `blessed/` directly, so browsing the site exercises the same contract external
consumers depend on. Serve it locally with `python3 -m http.server -d site` — every page works with
JavaScript disabled. Hosted at `https://twallengren.github.io/calendar-project/` once GitHub Pages
is switched on (Pages deployment is not wired up yet).

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

## Market status

One row per blessed calendar: coverage, closure/early-close/projected counts, cited sources, and
cross-validation results against third-party reference data. Regenerate with
`tools status --format markdown` after `scripts/bless.sh` (see `spec/SPEC.md` for the JSON shape
and `tools crossvalidate`, which produces the cross-validation column).

| ID | Name | Kind | Timezone | Coverage | Verified Through | Closures | Early Closes | Projected | Sources | Cross-validation | Release |
|----|------|------|----------|----------|-------------------|----------|---------------|-----------|---------|-------------------|---------|
| BE-EURONEXT-BRUSSELS | Euronext Brussels Trading Calendar | market | Europe/Brussels | 2010-01-01 to 2030-12-31 | 2026-12-31 | 127 | 29 | 0 | 8 | exchange_calendars-XBRU: ok (allowlisted=2) | 11.0.0 |
| CA-TSX | Toronto Stock Exchange | market | America/Toronto | 2000-01-01 to 2030-12-31 | 2026-12-31 | 302 | 8 | 4 | 12 | exchange_calendars-XTSE: ok (allowlisted=9); quantlib-tsx: ok (allowlisted=0) | 11.0.0 |
| DE-XETRA | Deutsche Börse Xetra | market | Europe/Berlin | 2003-01-01 to 2030-12-31 | 2026-12-31 | 238 | 0 | 0 | 25 | exchange_calendars-XETR: ok (allowlisted=21); quantlib-germany-xetra: ok (allowlisted=33) | 11.0.0 |
| FR-EURONEXT-PARIS | Euronext Paris Trading Calendar | market | Europe/Paris | 2010-01-01 to 2030-12-31 | 2026-12-31 | 127 | 29 | 0 | 8 | exchange_calendars-XPAR: ok (allowlisted=2) | 11.0.0 |
| GB-LSE | London Stock Exchange | market | Europe/London | 2000-01-01 to 2030-12-31 | 2028-12-31 | 254 | 62 | 0 | 9 | exchange_calendars-XLON: ok (allowlisted=0); quantlib-uk-exchange: ok (allowlisted=0) | 11.0.0 |
| HK-HKEX | Hong Kong Exchanges and Clearing | market | Asia/Hong_Kong | 2018-01-01 to 2027-12-31 | 2027-12-31 | 141 | 25 | 0 | 9 | exchange_calendars-XHKG: ok (allowlisted=4); quantlib-hong-kong: ok (allowlisted=17) | 11.0.0 |
| JP-JPX | Japan Exchange Group (Tokyo Stock Exchange) | market | Asia/Tokyo | 2010-01-01 to 2030-12-31 | 2027-12-31 | 343 | 0 | 6 | 5 | exchange_calendars-XTKS: ok (allowlisted=11); quantlib-japan: ok (allowlisted=1) | 11.0.0 |
| NL-EURONEXT-AMSTERDAM | Euronext Amsterdam Trading Calendar | market | Europe/Amsterdam | 2010-01-01 to 2030-12-31 | 2026-12-31 | 127 | 29 | 0 | 8 | exchange_calendars-XAMS: ok (allowlisted=2) | 11.0.0 |
| PT-EURONEXT-LISBON | Euronext Lisbon Trading Calendar | market | Europe/Lisbon | 2010-01-01 to 2030-12-31 | 2026-12-31 | 127 | 29 | 0 | 8 | exchange_calendars-XLIS: ok (allowlisted=2) | 11.0.0 |
| SA-TADAWUL | Tadawul (Saudi Exchange) Trading Calendar | market | Asia/Riyadh | 2020-01-01 to 2030-12-31 | 2029-12-31 | 187 | 0 | 16 | 2 | exchange_calendars-XSAU: ok (allowlisted=19) | 11.0.0 |
| US-NYSE | NYSE Trading Calendar | market | America/New_York | 1900-01-01 to 2030-12-31 | 2026-12-31 | 1649 | 126 | 0 | 3 | exchange_calendars-XNYS: ok (allowlisted=9); quantlib-nyse: ok (allowlisted=0) | 11.0.0 |
| EU-EURONEXT | Euronext Cash Markets Base Calendar | base | Europe/Paris | 2010-01-01 to 2030-12-31 | 2026-12-31 | 127 | 0 | 0 | 8 | none | 11.0.0 |
| US-CORP-IN-VISIBILITY | US Corporate Calendar with India Visibility | base | America/New_York | 1900-01-01 to 2030-12-31 | 2026-12-31 | 938 | 0 | 0 | 3 (1 unresolved) | none | 11.0.0 |
| US-MARKET-BASE | US Market Base Calendar | base | America/New_York | 1900-01-01 to 2030-12-31 | 2026-12-31 | 938 | 0 | 0 | 2 (1 unresolved) | none | 11.0.0 |

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for the full walkthrough of adding a market, correcting
data, or contributing code. In short: cite your sources under `sources/<MARKET>/README.md`,
write the calendar and module YAML, run `validate --all --strict`, update goldens, and add the
calendar to `blessed/manifest.json` before opening a PR.

A [`justfile`](justfile) wraps the longer Gradle commands (`just build`, `just test`,
`just validate GB-LSE`, and so on) if you have [`just`](https://github.com/casey/just) installed.
It is optional; every recipe is a thin wrapper around a `./gradlew` command documented above.
