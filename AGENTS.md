# Repository Guidance

These instructions apply throughout this repository. Use [spec/SPEC.md](spec/SPEC.md) for calendar semantics and [CONTRIBUTING.md](CONTRIBUTING.md) for contribution conventions.

## Build & Test Commands

Use Java 21 and the checked-in Gradle wrapper. If the default Java installation differs, point `JAVA_HOME` at an installed JDK 21; do not embed a developer's local JDK path in project files. Run the commands below from the repository root; tests and CLI tasks use `workingDir = rootProject.projectDir`.

```bash
./gradlew :tools:build              # Build, test, and check formatting
./gradlew :tools:test               # Run all tests
./gradlew :tools:test --tests 'com.bdc.diff.CalendarDiffEngineTest'
./gradlew :tools:spotlessApply      # Format code (Google Java Format)
./gradlew :tools:spotlessCheck      # Check formatting without rewriting files
./gradlew :tools:installDist        # Build the standalone CLI distribution
```

**Running the CLI:**
```bash
./gradlew :tools:run --args="validate US-MARKET-BASE"
./gradlew :tools:run --args="generate US-MARKET-BASE --from 2024-01-01 --to 2024-12-31 --out generated/US-MARKET-BASE"
./gradlew :tools:run --args="resolve US-MARKET-BASE --out build/resolved/US-MARKET-BASE.yaml"
./gradlew :tools:run --args="query US-NYSE --is-business-day 2026-07-03"
```

After `:tools:installDist`, compare all published calendars with:

```bash
tools/build/install/tools/bin/tools ci-diff --calendars all --output-format json
```

`ci-diff` exit codes are 0 = no changes, 1 = MINOR, 2 = MAJOR, and 3 = error. Inspect the report when differences are expected.

Optional local setup: `./gradlew :tools:installGitHooks` installs the pre-commit hook. It formats the entire tools module when Java files are staged, so inspect the working-tree diff afterward.

## Architecture

This is a YAML-driven business-day calendar system. YAML specs are the source of truth; the Java toolchain compiles them into deterministic CSV/JSON artifacts.

### Data flow

```
YAML specs (calendars/, modules/, chronologies/)
  → Loader (Jackson YAML parsing, SpecRegistry)
  → Resolver (inheritance via `extends`, module composition via `uses`, merged deltas)
  → Generator (expand dependencies, filter active years, observe holidays, apply deltas, classify)
  → Emitter (CSV/JSON output)
```

### Key packages (`tools/src/main/java/com/bdc/`)

| Package | Role |
|---------|------|
| `cli` | PicoCLI commands: validate, resolve, generate, query, diff, ci-diff, history |
| `loader` | YAML parsing into model objects, SpecRegistry for lookups |
| `resolver` | Calendar inheritance resolution and module merging |
| `generator` | Rule expansion, weekend observation, dependency-aware cascading placement |
| `chronology` | Multi-calendar support; generated classes live in `src/main/java-generated/` |
| `emitter` | Output formatters (CSV, JSON, YAML) |
| `diff` | CalendarDiffEngine for comparing calendar outputs |
| `csv` | Shared reader for complete CSV records, including quoted descriptions and duplicates |
| `model` | Data records for specs, events, rules |
| `artifact` | Bitemporality: versioned artifact storage and retrieval |
| `formula` | ReferenceResolver and reference formulas (e.g., Easter) |
| `classifier` | Event classification and delta reclassification |
| `stream` | LazyDateStream: event queries, business-day navigation, and counts |

### Rule types for event sources

Rules define how holidays are computed. Defined in `spec/SPEC.md`:

- `fixed_month_day` — static date (e.g., Dec 25)
- `nth_weekday_of_month` — e.g., first Monday of September
- `relative_to_reference` — offset from a computed reference like Easter
- `explicit_dates` — hard-coded date list

`fixed_month_day` rules support a `chronology` field: ISO, HIJRI, JULIAN, PERSIAN, or UMM_AL_QURA. Other rule types use ISO dates and references. Non-ISO translation uses the chronology registry and Julian Day Number (JDN). Follow existing fixtures when creating rules: each rule has its own `key` and `name`; enclosing event-source fields are not automatically copied into it.

### Calendar composition model

- **Calendars** (`calendars/`) inherit via `extends` and include modules via `uses`
- **Modules** (`modules/holidays/`, `modules/event_sources/`, `modules/policies/`) define events or policies and can compose other modules via `uses`
- **Groups** (`modules/groups/`) aggregate modules into reusable sets (e.g., `us_nyse_holidays`)
- **Deltas** allow add/remove/reclassify of inherited events
- **Weekend shift policies**: NONE, NEAREST_WEEKDAY, NEXT_AVAILABLE_WEEKDAY

### Chronology codegen

Chronology YAML files in `chronologies/` are compiled to Java classes in `tools/src/main/java-generated/`. These generated files are committed to version control. Edit chronology definitions or the generator as appropriate, then run `./gradlew :tools:generateChronologies` and review the generated diff. Follow with `./gradlew :tools:build` to compile and test the regenerated sources.

### Artifact versioning

- `blessed/` — latest published calendar artifacts (committed)
- `release-history/` — historical versions for bitemporality (committed)
- `generated/` — local dev output (gitignored)
- `build/` — scratch reports and artifact comparisons (gitignored)

Use scratch directories for verification. Update `blessed/` and `release-history/` through the reviewed release workflow when release work is part of the task.

## Calendar correctness

- Preserve inclusive ranges and the [range-consistency guarantee](spec/SPEC.md#date-range-consistency): whenever both requests succeed, narrow generation equals wider generation filtered to the narrow range, including complete event lists and provenance.
- Keep active-year filtering on the original ISO date. Observation precedes output-range filtering, deltas, classification, and weekend rows. Keep dependency caches local to a generation call and preserve the documented cascading limits and explicit dependency errors.
- Multiple events can share a date, and identical output rows can occur more than once. Compare counted collections of complete records; do not reduce them to one event per date or a set of rows.
- Preserve the [artifact-comparison rules](spec/SPEC.md#artifact-comparison): CI compares date/type/description, cancels exact occurrences first, and infers a modification only for a single remaining old/new pair on a date. Artifact diffs retain all CSV fields, including alternate dates.
- Use the shared CSV reader for event artifacts rather than splitting physical lines or commas. Descriptions can contain quoted commas, quotes, and newlines.

## Testing conventions

- **Golden tests**: compare generated output against checked-in expected files in `tools/src/test/resources/golden/`
- **Property-based tests**: JQwik for randomized edge-case testing (chronology conversions, etc.)
- **Test calendars**: `tools/src/test/resources/test-calendars/` contains YAML fixtures

For code changes, run focused regressions while developing, then the full build. Test behavior at affected boundaries, including nested ranges and duplicate occurrences where relevant. For documentation-only changes, verify referenced paths and commands and run `git diff --check`; a full Java build is unnecessary.

Update golden expectations only for intentional behavior changes, and review every changed file. Filter to the affected test rather than refreshing all goldens to make a failure disappear. For example:

```bash
./gradlew :tools:test --tests 'com.bdc.test.GoldenTests.usMarketBase2024' -DupdateGoldens=true --rerun-tasks
```

After updates, run tests without golden-update mode. For generation or comparison changes, regenerate the published ranges into a scratch directory, compare complete CSV record counts against `blessed/`, and inspect `ci-diff` output. Explain any artifact differences in the change description.

## Data contributions

When adding or modifying calendar data, cite authoritative sources for holiday dates and include provenance in comments.
