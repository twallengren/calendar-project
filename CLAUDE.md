# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Test Commands

All Gradle commands run from the repo root (the `tools/` build uses `workingDir = rootProject.projectDir`).

```bash
./gradlew :tools:build              # Build everything
./gradlew :tools:test               # Run all tests
./gradlew :tools:test --tests "ClassName"  # Run a single test class
./gradlew :tools:spotlessApply      # Format code (Google Java Format)
./gradlew generateChronologies      # Regenerate Java classes from chronologies/*.yaml
./gradlew installGitHooks           # Install pre-commit hook (runs spotlessApply)
```

**Running the CLI:**
```bash
./gradlew :tools:run --args="validate --all --strict"     # CI runs this; warnings fail under --strict
./gradlew :tools:run --args="generate US-MARKET-BASE --from 2024-01-01 --to 2024-12-31 --out generated/US-MARKET-BASE"
./gradlew :tools:run --args="resolve US-MARKET-BASE --out build/resolved/US-MARKET-BASE.yaml"
./gradlew :tools:run --args="query US-NYSE --as-of v10.1.0 --is-business-day 2021-12-31"
scripts/bless.sh                     # regenerate blessed/ reproducibly (no-op leaves git clean)
```

**Golden tests:** Update expected outputs with `./gradlew :tools:test -DupdateGoldens=true`

## Architecture

This is a YAML-driven business-day calendar system. YAML specs are the source of truth; the Java toolchain compiles them into deterministic CSV/JSON artifacts.

### Data flow

```
YAML specs (calendars/, modules/, chronologies/)
  → Loader (Jackson YAML parsing, SpecRegistry)
  → Resolver (inheritance via `extends`, module composition via `uses`, delta application)
  → Generator (rule expansion to concrete dates)
  → Emitter (CSV/JSON output)
```

### Key packages (`tools/src/main/java/com/bdc/`)

| Package | Role |
|---------|------|
| `cli` | PicoCLI commands: validate, resolve, generate, query, diff, ci-diff, history |
| `loader` | YAML parsing into model objects, SpecRegistry for lookups |
| `resolver` | Calendar inheritance resolution and module merging |
| `generator` | Expands event rules into dated events (RuleExpander, ReferenceResolver) |
| `chronology` | Multi-calendar support; generated classes live in `src/main/java-generated/` |
| `emitter` | Output formatters (CSV, JSON, YAML) |
| `diff` | CalendarDiffEngine for comparing calendar outputs |
| `model` | Data records for specs, events, rules |
| `artifact` | Bitemporality: `ReleaseHistoryStore` reads blessed/ and release-history/ for as-of queries; `ArtifactStore` is the older local store |
| `validation` | `SpecValidator` (structural) and `GeneratedOutputValidator` (post-generation) behind `validate` |
| `formula` | Reference date computation (e.g., Easter) |
| `classifier` | Event classification logic (CLOSED, NOTABLE, PERIOD_MARKER) |

### Rule types for event sources

Rules define how holidays are computed. Defined in `spec/SPEC.md`:
- `fixed_month_day` — static date (e.g., Dec 25)
- `nth_weekday_of_month` — e.g., first Monday of September
- `relative_to_reference` — offset from a computed reference like Easter
- `explicit_dates` — hard-coded date list

Rules can specify a `chronology` field (HIJRI, UMM_AL_QURA, JULIAN, PERSIAN) to use non-Gregorian dates, and `end_month`/`end_day` or `duration_days` for multi-day spans. All cross-chronology translation goes through Julian Day Number (JDN).

Event sources also carry `shift_policy` (per-holiday weekend observance, e.g. `FORWARD_ONLY` for NYSE New Year's Day), `only_if_weekday`, `close_time` (EARLY_CLOSE), `status` (CONFIRMED/PROJECTED) and `source` (citation into `sources/<MARKET>/README.md`).

### Calendar composition model

- **Calendars** (`calendars/`) can `extend` parent calendars and `use` modules
- **Modules** (`modules/holidays/`) define individual holidays or policies; can compose other modules via `uses`
- **Groups** (`modules/groups/`) aggregate modules into reusable sets (e.g., `us_nyse_holidays`)
- **Deltas** allow add/remove/reclassify of inherited events
- **Weekend policies** are effective-dated (`{days, from, to}` periods); `nyse_weekends` models Saturday sessions before 1952
- **Weekend shift policies**: NONE, NEAREST_WEEKDAY, NEXT_AVAILABLE_WEEKDAY, FORWARD_ONLY; CLOSED beats EARLY_CLOSE on the same date

### Chronology codegen

Chronology YAML files in `chronologies/` are compiled to Java classes in `tools/src/main/java-generated/`. These generated files are committed to version control. To regenerate after editing a chronology YAML: `./gradlew generateChronologies`.

### Artifact versioning

- `blessed/` — latest published calendar artifacts (committed)
- `release-history/` — historical versions for bitemporality (committed)
- `generated/` — local dev output (gitignored)

## Testing conventions

- **Golden tests**: compare generated output against checked-in expected files in `tools/src/test/resources/golden/`
- **Property-based tests**: JQwik for randomized edge-case testing (chronology conversions, etc.)
- **Test calendars**: `tools/src/test/resources/test-calendars/` contains YAML fixtures
- **Cross-validation**: `ReferenceCrossValidationTest` diffs generated output against `tools/src/test/resources/reference/` (exchange_calendars, QuantLib exports); explained differences live in `allowlist.csv` and stale rows fail

## Data contributions

When adding or modifying calendar data, cite the source with a `source:` field (an id from `sources/<MARKET>/README.md`); `validate --strict` rejects uncited event sources. The NYSE's own holiday history PDF under `sources/US-NYSE/` outranks third-party libraries when they disagree.
