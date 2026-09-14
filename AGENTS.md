# Repository Guidance

These instructions apply throughout this repository. Use [spec/SPEC.md](spec/SPEC.md) for calendar semantics and [CONTRIBUTING.md](CONTRIBUTING.md) for contribution conventions.

## Build & Test Commands

Use Java 21 and the checked-in Gradle wrapper. If the default Java installation differs, point `JAVA_HOME` at an installed JDK 21; do not embed a developer's local JDK path in project files. Run the commands below from the repository root; tests and CLI tasks use `workingDir = rootProject.projectDir`.

Three Gradle modules: **`core`** (the dependency-free query API and the `BusinessCalendars` facade,
published as `bdc-calendar-core`), **`data`** (no sources — packs `blessed/` into classpath
resources, published as `bdc-calendar-data`) and **`tools`** (the CLI and everything YAML-driven).

```bash
./gradlew build                     # Build all three modules
./gradlew test                      # Run all tests (core + tools)
./gradlew :tools:build              # Build, test, and check formatting for the toolchain
./gradlew :tools:test               # Run the toolchain's tests
./gradlew :tools:fastTest           # All tests except @Tag("slow")/@Tag("cross-validation") (fast local loop)
./gradlew :tools:test --tests 'com.bdc.diff.CalendarDiffEngineTest'   # Run a single test class
./gradlew :tools:spotlessApply      # Format code (Google Java Format)
./gradlew :tools:spotlessCheck      # Check formatting without rewriting files
./gradlew :tools:installDist        # Build the standalone CLI distribution
./gradlew generateChronologies      # Regenerate Java classes from chronologies/*.yaml
./gradlew installGitHooks           # Install pre-commit hook (runs spotlessApply)
./gradlew :data:generateCalendarData   # Repack blessed/ into the data jar's resources
./gradlew publishToMavenLocal       # Build bdc-calendar-core/-data locally (add -Prelease=true to drop -SNAPSHOT)
```

`installGitHooks` is optional local setup. The hook formats the entire tools module when Java files are staged, so inspect the working-tree diff afterward.

**Running the CLI:**
```bash
./gradlew :tools:run --args="validate --all --strict"     # CI runs this; warnings fail under --strict
./gradlew :tools:run --args="generate US-MARKET-BASE --from 2024-01-01 --to 2024-12-31 --out generated/US-MARKET-BASE"
./gradlew :tools:run --args="resolve US-MARKET-BASE --out build/resolved/US-MARKET-BASE.yaml"
./gradlew :tools:run --args="query US-NYSE --as-of v10.1.0 --is-business-day 2021-12-31"
./gradlew :tools:run --args="changelog --blessed-dir blessed --release-history-dir release-history --out site/"  # build changelog site from release history
./gradlew :tools:run --args="site --api-only --out site"  # write the /v1/ JSON API + .ics files from blessed/ (see spec/SPEC.md#json-api-v1)
# .github/workflows/release-pr.yml prepares reviewed release artifacts after qualifying main CI; release.yml publishes the exact merged release SHA and deploys Pages directly.
./gradlew :tools:run --args="site --out site --base-url https://twallengren.github.io/calendar-project/"  # full static site: /v1/ API, changelog, then HTML pages rendered from that API (com.bdc.site; hand-written styles.css/site.js in tools/src/main/resources/site/)
./gradlew :tools:run --args="site --blessed-dir generated --compare-to blessed --out site-preview"  # contributor preview: build the site from a local `generate --include-specs` output (index synthesised from whichever calendars are present) and add a "Changes vs blessed" banner plus /changes/ against another dir (e.g. blessed/)
./gradlew :tools:run --args="serve --dir site-preview --port 8080"  # zero-dependency static file server (JDK HttpServer) for previewing `site`/`site-preview` output; --open launches a browser, Ctrl-C stops it
# the same run also writes /compare/<A>/<B>/ for every unordered pair of market calendars and /sources/<ID>/ from sources/<ID>/README.md (--sources-dir, default `sources`)
scripts/bless.sh                     # regenerate blessed/ reproducibly (no-op leaves git clean)
./gradlew :tools:run --args="crossvalidate --all --out blessed"   # writes blessed/<ID>/cross_validation.json; ./gradlew :tools:run --args="status --format markdown" renders the Market status table in README.md
```

After `:tools:installDist`, compare all published calendars with:

```bash
tools/build/install/tools/bin/tools ci-diff --blessed-dir blessed --calendars all --output-format json
```

`ci-diff` exit codes are 0 = no changes, 1 = MINOR, 2 = MAJOR, and 3 = error. Inspect the report when differences are expected.

**Golden tests:** Update expected outputs with `./gradlew :tools:test -DupdateGoldens=true`

**Python package (`python/`):** `bdc-calendars` ships the blessed data inside its wheel and mirrors the Query API (`spec/SPEC.md`) with zero runtime dependencies. An optional `mcp` extra adds a `bdc-calendars-mcp` stdio MCP server (`python/bdc_calendars/mcp/`) exposing that same API to AI agents.
```bash
python -m venv .venv && .venv/bin/pip install -e "python/[test,mcp]"
.venv/bin/python -m pytest python/ -q     # API tests + parity against the Java query API + the MCP server (skipped if `mcp` isn't installed)
python python/scripts/sync_data.py        # re-copy blessed/ into python/bdc_calendars/data/ (also run by the release workflow)
python python/scripts/sync_data.py --check   # CI guard: bundled data still re-derives from blessed/
python/scripts/generate_parity_fixture.sh    # refresh python/tests/fixtures/ from the Java toolchain (jshell)
```

**Onboarding a new market:** `./gradlew :tools:run --args="scaffold --market GB-LSE --name \"London Stock Exchange\" --timezone Europe/London --mic XLON"` generates the calendar YAML, a holiday group and one example holiday module, and a `sources/<MARKET>/README.md` citation table for a new market; it reuses the `weekend_sat_sun`/`weekend_fri_sat` policy modules or writes a new one with `--weekend custom`, appends the calendar's `blessed/manifest.json` and `scripts/reference/export_reference_calendars.py` entries, and prints the golden test stub and next steps (validate, generate, update goldens, cross-validation allowlist) referenced in `CONTRIBUTING.md`. It refuses to overwrite existing files unless `--force`, and `--dry-run` previews the plan without writing anything.

## Architecture

This is a YAML-driven business-day calendar system. YAML specs are the source of truth; the Java toolchain compiles them into deterministic CSV/JSON artifacts.

### Data flow

```
YAML specs (calendars/, modules/, chronologies/)
  → Loader (Jackson YAML parsing, SpecRegistry)
  → Resolver (inheritance via `extends`, module composition via `uses`, delta application)
  → Generator (expand rules over a padded range, filter active years, observe holidays, apply deltas, classify)
  → Emitter (CSV/JSON output)
```

### Key packages (`tools/src/main/java/com/bdc/`, except where marked **core**)

The `core` module holds only classes that import nothing outside `java.*`, so the published library
jar stays dependency-free. Package names are unchanged across the split (`com.bdc.model`,
`com.bdc.stream`, ...), so a class moving between modules needs no import changes.

| Package | Role |
|---------|------|
| `calendar` | **core** — `BusinessCalendars`, the entry point to the bundled data (`of`, `joint`, `available`, `dataVersion`); rebuilds WEEKEND rows from each calendar's weekend policy |
| `stream` | **core** — the Query API: `DateStream`, `CsvDateStream`, `JointDateStream`, `OutsideCoverageException` (`LazyDateStream`, which queries the generator directly, stays in `tools`) |
| `cli` | PicoCLI commands: validate, resolve, generate, query, ci-diff, history, changelog, site, serve, scaffold, manifest, crossvalidate, status |
| `loader` | YAML parsing into model objects, SpecRegistry for lookups |
| `resolver` | Calendar inheritance resolution and module merging |
| `generator` | Expands event rules into dated events over a padded range, then places and shifts them (RuleExpander, EventGenerator) |
| `chronology` | Multi-calendar support; generated classes live in `src/main/java-generated/`; `DateRange` is **core** |
| `emitter` | Output formatters (CSV, JSON, YAML); `EventsCsvReader` (the one reader for event CSVs) is **core** |
| `diff` | CalendarDiffEngine for comparing calendar outputs |
| `model` | Data records for specs, events, rules; `Event`/`EventType`/`EventStatus` are **core** |
| `artifact` | Bitemporality: `ReleaseHistoryStore` reads blessed/ and release-history/ for as-of queries |
| `validation` | `SpecValidator` (structural) and `GeneratedOutputValidator` (post-generation) behind `validate` |
| `formula` | ReferenceResolver and reference formulas (e.g., Easter) |
| `classifier` | Event classification and delta reclassification (CLOSED, NOTABLE, PERIOD_MARKER) |
| `site` | Static site: `ApiEmitter` (/v1/ JSON), changelog, and the HTML renderers — home, market, year, date, `ComparePageRenderer` (pair pages + T+N helper mount), `SourcesPageRenderer`/`SourceRegistry`/`MarkdownRenderer` (the `sources/` register) |

### Rule types for event sources

Rules define how holidays are computed. Defined in `spec/SPEC.md`:

- `fixed_month_day` — static date (e.g., Dec 25)
- `nth_weekday_of_month` — e.g., first Monday of September
- `relative_to_reference` — offset from a computed reference like Easter
- `explicit_dates` — hard-coded date list

Rules can specify a `chronology` field (HIJRI, UMM_AL_QURA, JULIAN, PERSIAN) to use non-Gregorian dates, and `end_month`/`end_day` or `duration_days` for multi-day spans. All cross-chronology translation goes through Julian Day Number (JDN). Follow existing fixtures when creating rules: each rule has its own `key` and `name`; enclosing event-source fields are not automatically copied into it.

Event sources also carry `shift_policy` (per-holiday weekend observance, e.g. `FORWARD_ONLY` for NYSE New Year's Day), `only_if_weekday`, `close_time` (EARLY_CLOSE), `status` (CONFIRMED/PROJECTED) and `source` (citation into `sources/<MARKET>/README.md`).

### Calendar composition model

- **Calendars** (`calendars/`) can `extend` parent calendars and `use` modules
- **Modules** (`modules/holidays/`) define individual holidays or policies; can compose other modules via `uses`
- **Groups** (`modules/groups/`) aggregate modules into reusable sets (e.g., `us_nyse_holidays`)
- **Deltas** allow add/remove/reclassify of inherited events
- **Weekend policies** are effective-dated (`{days, from, to}` periods); `nyse_weekends` models Saturday sessions before 1952
- **Weekend shift policies**: NONE, NEAREST_WEEKDAY, NEXT_AVAILABLE_WEEKDAY, NEXT_AVAILABLE_FROM_LAST_WEEKEND_DAY, FORWARD_ONLY for CLOSED; DROP (default) and PREVIOUS_AVAILABLE_BUSINESS_DAY for EARLY_CLOSE; CLOSED beats EARLY_CLOSE on the same date, and `displaces:` lets a shifting CLOSED event take a slot and push the holder forward

### Chronology codegen

Chronology YAML files in `chronologies/` are compiled to Java classes in `tools/src/main/java-generated/`. These generated files are committed to version control. Edit chronology definitions or the generator as appropriate, then run `./gradlew generateChronologies` and review the generated diff. Follow with `./gradlew :tools:build` to compile and test the regenerated sources.

### Artifact versioning

- `blessed/` — latest published calendar artifacts (committed)
- `release-history/` — historical versions for bitemporality (committed)
- `generated/` — local dev output (gitignored)
- `build/` — scratch reports and artifact comparisons (gitignored)
- `python/bdc_calendars/data/` — the same blessed data, minus weekend rows (rebuilt from `weekend_policy` at query time), bundled into the `bdc-calendars` wheel; `__version__` is `0.<data major>.<data minor>`
- `data/build/generated-resources/bdc/calendars/` — the same trimmed data again, generated (never committed) by `:data:generateCalendarData` into the `bdc-calendar-data` jar. `core`'s `BusinessCalendarsTest` replays it against `blessed/` to prove the weekend reconstruction is lossless. The Maven artifacts are versioned from `blessed/manifest.json`'s `release_version.semantic`

Use scratch directories for verification. Update `blessed/` and `release-history/` through the reviewed release workflow (`scripts/bless.sh`) when release work is part of the task.

### Python bindings

`python/` holds `bdc-calendars`: a dependency-free port of the Query API (`BusinessCalendar`, `get_calendar`/`get_joint_calendar`, the same out-of-range and status contract, exchange_calendars-style aliases). The Java `DateStream`/`JointDateStream` remain the reference implementation — `python/tests/test_parity.py` replays fixtures dumped from them, so port any semantic change to both. Release publishing to PyPI (Trusted Publishing) is in `release.yml`, guarded by the `PYPI_PUBLISH` repository variable.

## Calendar correctness

- Preserve inclusive ranges and the [range-consistency guarantee](spec/SPEC.md#date-range-consistency): whenever both requests succeed, narrow generation equals wider generation filtered to the narrow range, including complete event lists and provenance. The generator achieves this by expanding rules over a *padded* range — a year either side plus the furthest reach of any `relative_to_reference` rule in the calendar — and filtering back to the request at the end. A rule type that can reach further than the current padding must widen it.
- Keep active-year filtering on the original ISO date. Observation precedes output-range filtering, deltas, classification, and weekend rows.
- Multiple events can share a date, and identical output rows can occur more than once. Compare counted collections of complete records; do not reduce them to one event per date or a set of rows.
- Preserve the [artifact-comparison rules](spec/SPEC.md#artifact-comparison): `ci-diff` groups by `(date, key)`, cancels exact occurrences first, and infers a modification only for a single remaining old/new pair within one identity. Its output must not depend on the order events arrive in.
- Use `EventsCsvReader` (in `core`) for event artifacts rather than splitting physical lines or commas, and do not add a second CSV reader. Descriptions can contain quoted commas and quotes.

## Testing conventions

- **Golden tests**: compare generated output against checked-in expected files in `tools/src/test/resources/golden/`
- **Property-based tests**: JQwik for randomized edge-case testing (chronology conversions, range consistency, diff completeness)
- **Test calendars**: `tools/src/test/resources/test-calendars/` contains YAML fixtures
- **Cross-validation**: `ReferenceCrossValidationTest` diffs generated output against `tools/src/test/resources/reference/` (exchange_calendars, QuantLib exports); explained differences live in `allowlist.csv` and stale rows fail
- **Browser/Java parity**: the T+N settlement helper in `site.js` reimplements `JointDateStream` (contract in `spec/SPEC.md#settlement-in-the-browser`). `SettlementParityTest` checks `tools/src/main/resources/site/settlement-fixture.json` against `JointDateStream` (regenerate with `-DupdateGoldens=true`); the browser half runs at `/compare/settlement-selftest.html`, which needs the site served over HTTP (`python3 -m http.server` in the output directory), not `file://`

For code changes, run focused regressions while developing, then the full build. Test behavior at affected boundaries, including nested ranges and duplicate occurrences where relevant. For documentation-only changes, verify referenced paths and commands and run `git diff --check`; a full Java build is unnecessary.

Update golden expectations only for intentional behavior changes, and review every changed file. Filter to the affected test rather than refreshing all goldens to make a failure disappear. For example:

```bash
./gradlew :tools:test --tests 'com.bdc.generator.GeneratorGoldenTest' -DupdateGoldens=true --rerun-tasks
```

After updates, run tests without golden-update mode. For generation or comparison changes, regenerate the published ranges into a scratch directory, compare complete CSV record counts against `blessed/`, and inspect `ci-diff` output. Explain any artifact differences in the change description.

## Data contributions

When adding or modifying calendar data, cite the source with a `source:` field (an id from canonical `sources/<MARKET>/register.json` (README is generated)); `validate --strict` rejects uncited event sources. The NYSE's own holiday history PDF under `sources/US-NYSE/` outranks third-party libraries when they disagree.

A market-kind calendar's `metadata.mic` (ISO 10383) and `metadata.aliases` are the one source of truth for exchange-code lookups: `validate --strict` warns when a market has no `mic` and errors on a malformed or duplicate mic/alias; `tools manifest` (run by `scripts/bless.sh`) derives `blessed/manifest.json`'s `aliases` map from them, and `python/scripts/sync_data.py`/`data/build.gradle.kts` read that map rather than hand-maintaining their own.

## Native chronologies, trust and financial operations

`docs/native-chronologies.md` describes the compiler-only ICU4J 78.3 Hebrew and Chinese Hong Kong
profiles, bounded conversion intervals, named/leap months and independent evidence. Keep native
identity and provider provenance through generation. `tools convert` shares the provider path;
conversion must fail explicitly outside its profile rather than emit blank dates.

Coverage quality is per scope (scheduled closures, early closes, unscheduled exceptions).
`INCOMPLETE` or a missing explicit scope makes actual state UNKNOWN and business-day operations
raise `UnresolvedDateException` / `UnresolvedDateError`, compatible with coverage errors. Read
`assessment` for scheduled state and evidence; raw event status remains CONFIRMED/PROJECTED.

Financial date conventions, month advancement and last-business-day queries are defined in
`spec/SPEC.md#financial-date-operations`. Keep detailed Java/Python/CLI/MCP results and full-path
confidence in parity. Identity exceptions do not establish an open date. Member close times retain
calendar/timezone identity; comparing wall-clock close times across zones is deprecated.

`EU-TARGET`, `US-FEDWIRE` and `GB-CHAPS` are payment-kind calendars without MICs, included alongside
markets in runtime/site publication. Their 2026–2027 ordinary-day projections exclude sessions,
cutoffs and instrument eligibility; see `docs/payment-calendars.md`.
