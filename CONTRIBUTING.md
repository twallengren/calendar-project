# Contributing

This project is a version-controlled dataset of business-day calendars. YAML specs are the
source of truth; the Java toolchain compiles them into CSV/JSON artifacts. This guide walks
through the most common contribution: adding a market. Smaller fixes (a wrong date, a missing
source) follow the same tools, described in "Data corrections" below.

## Add a market in an afternoon

This walkthrough adds a new exchange calendar end to end. It assumes a market that closes for
public holidays and has at least one authoritative source you can point to (an exchange
circular, a gazette notice, an official holiday list).

A `scaffold` command that generates the YAML skeleton, source file, and test fixtures for a new
market is being added in parallel. Once it lands it will shortcut steps 1-3 below. This guide
does not document its flags yet; check `./gradlew :tools:run --args="scaffold --help"` once it
exists.

### 1. Pick your ids

| Thing | Convention | Example |
|-------|------------|---------|
| Calendar file | `calendars/<CC>-<MARKET>.yaml` | `calendars/GB-LSE.yaml` |
| Calendar id | Same as the filename, no extension | `GB-LSE` |
| Module file | `modules/holidays\|policies\|groups/<snake_case_id>.yaml` | `modules/holidays/boxing_day.yaml` |
| Module id | snake_case, matches the filename | `boxing_day` |
| Source table | `sources/<MARKET>/README.md` | `sources/GB-LSE/README.md` |

`<CC>` is the ISO country code, `<MARKET>` a short exchange mnemonic. Use the same `<MARKET>`
token for the source directory as for the calendar id suffix.

### 2. Cite your sources first

Every holiday needs a citation, and `validate --strict` enforces it. Create
`sources/<MARKET>/README.md` with a table:

```markdown
# GB-LSE sources

| id | title | publisher | url / file | retrieved | covers | notes |
|----|-------|-----------|------------|-----------|--------|-------|
| `lse-hours` | LSE Holidays & Trading Hours | London Stock Exchange | https://www.londonstockexchange.com/... | 2026-09-13 | current and next year | Published holiday schedule |
```

Add a row for every distinct document you transcribe from. `retrieved` is the date you pulled
the source; `covers` is the date range it is good for.

In YAML, reference a row by its `id`:

```yaml
source:
  - id: lse-hours
    ref: "2026 holiday schedule"
```

A `source:` field at the top of a module applies to every event source in that module, so you
only need to repeat it when different holidays in the same module cite different documents. See
`spec/SPEC.md` ("Sources") for the full citation shape, including one-off external references
that skip the table.

### 3. Write the calendar spec

Start with the calendar's own metadata. The fields a market needs:

| Field | Purpose |
|-------|---------|
| `metadata.timezone` | IANA zone id. Required if any event has a `close_time`. |
| `metadata.coverage.from` / `to` | The date range you maintain this calendar for. |
| `metadata.coverage.verified_through` | Dates up to here have been checked against sources. Anything after that should be `status: PROJECTED`. |
| `weekend_shift_policy` | Default shift policy for weekend holidays: `NONE`, `NEAREST_WEEKDAY`, `NEXT_AVAILABLE_WEEKDAY`, or `FORWARD_ONLY`. |
| `uses` | Weekend policy module plus a holiday group module. |

```yaml
kind: calendar
id: GB-LSE

metadata:
  name: London Stock Exchange Trading Calendar
  chronology: ISO
  timezone: Europe/London
  coverage:
    from: 2000-01-01
    to: 2030-12-31
    verified_through: 2026-12-31

weekend_shift_policy: NEXT_AVAILABLE_WEEKDAY

uses:
  - weekend_sat_sun
  - gb_lse_holidays
```

Then write the holiday modules under `modules/holidays/` and a group module under
`modules/groups/` that composes them (see `modules/groups/us_nyse_holidays.yaml` for an
example). Per event source, the fields most markets need beyond `key`, `name`, and `rule`:

| Field | Purpose |
|-------|---------|
| `shift_policy` | Overrides the calendar default for this one holiday. |
| `close_time` | Local wall-clock close, quoted, for `EARLY_CLOSE` events. |
| `status: PROJECTED` | This year's date is a rule-computed guess, not yet announced. Use for unannounced future occurrences of observation-based holidays. |
| `active_years` | Restrict a rule to a range of years (a holiday that was created, retired, or renamed). |

`spec/SPEC.md` documents every rule type (`fixed_month_day`, `nth_weekday_of_month`,
`relative_to_reference`, `explicit_dates`), the weekend policy shapes, and delta operations. Read
it before inventing a new rule type; the existing ones cover almost everything.

### 4. Validate

```bash
./gradlew :tools:run --args="validate GB-LSE"
```

Fix everything it reports. Then run the full suite to make sure you have not broken another
calendar (a shared module, a renamed key):

```bash
./gradlew :tools:run --args="validate --all --strict"
```

`--strict` turns warnings into failures. Warnings include things like a `KEY_OVERRIDE` (two
modules define the same event key) or a missing citation, both worth fixing before a PR.

### 5. Generate and inspect

```bash
./gradlew :tools:run --args="generate GB-LSE --from 2024-01-01 --to 2026-12-31 --out generated/GB-LSE"
```

Look at `generated/GB-LSE/events.csv`. Check that holidays land where you expect, that weekend
shifts are correct, and that `metadata.json` counts look sane. `generated/` is gitignored; it is
scratch space for you, not part of the PR.

### 6. Add golden tests

Golden tests catch accidental regressions to output you have already verified.
`tools/src/test/java/com/bdc/test/GoldenTests.java` lists one test method per calendar-year (or
calendar-range) pair. Add a method for your new calendar, run it once to generate the expected
file, then inspect the generated golden by eye before committing it:

```bash
./gradlew :tools:test --tests "GoldenTests" -DupdateGoldens=true
```

Golden files live under `tools/src/test/resources/golden/`. Re-run the tests without
`-DupdateGoldens` afterward to confirm they now pass.

### 7. Cross-validate against a third-party source (optional)

If `exchange_calendars` or `QuantLib` supports your market, cross-validation catches mistakes a
single source might miss. This step is optional: skip it if neither library has your market.

Regenerate the reference CSVs manually. Never run this in CI; it is not deterministic across
library versions.

```bash
python3 -m venv .venv
.venv/bin/pip install -r scripts/reference/requirements.txt
.venv/bin/python scripts/reference/export_reference_calendars.py
```

This writes `tools/src/test/resources/reference/<CALENDAR>/<source>.csv`. Add your calendar to
the `EXPORTS` list in `scripts/reference/export_reference_calendars.py` first. Run the
cross-validation test; any difference it flags needs either a data fix or a row in
`tools/src/test/resources/reference/<CALENDAR>/allowlist.csv`:

```
source,side,date,type,reason
exchange_calendars-XLSE,theirs,2024-05-06,CLOSED,Not observed per LSE circular; explain here
```

`side` is `ours` when we have a closure the reference lacks, `theirs` when the reference has one
we lack. Every allowlist row needs a real reason: "the reference is wrong because X", not "differs
from reference". A stale allowlist row (one that no longer reflects an actual difference) fails
the test, so remove rows once they stop applying.

### 8. Add the calendar to the blessed manifest

`blessed/manifest.json` lists every calendar that gets published and re-blessed. A calendar
missing from it is invisible to `scripts/bless.sh` and the release workflow. Add an entry:

```json
"GB-LSE": {
  "range_start": "2000-01-01",
  "range_end": "2030-12-31"
}
```

Run `scripts/bless.sh` locally to populate `checksum` and `event_count` and to generate
`blessed/GB-LSE/`. Review the output with `git status --short blessed/`.

### 9. Open your PR

CI runs `validate --all --strict`, the full test suite, and a `ci-diff` comparison between your
branch and the current `blessed/` artifacts. The `ci-diff` comment on your PR reports a
severity:

| Severity | Meaning |
|----------|---------|
| `NONE` | No change to generated output. Merges freely. |
| `MINOR` | Only future-dated events changed. |
| `MAJOR` | Past or near-term events changed (a correction to already-published data). |

Any severity other than `NONE` blocks merge until a maintainer adds the
`calendar-change-approved` label after reviewing the diff comment. Adding a brand-new calendar
is usually `MAJOR` the first time (there is no prior blessed output to diff against for it), so
expect to ask for that label.

Before requesting review, self-check against `.github/PULL_REQUEST_TEMPLATE.md`.

### 10. Release and bless

Merges to `main` that touch `calendars/`, `modules/`, `chronologies/`, or the tool itself trigger
`.github/workflows/release.yml`. It re-runs `ci-diff` to decide a semantic version bump (`MAJOR`
diff -> major version, `MINOR` diff -> minor version, no change -> no release), regenerates
`blessed/`, archives the previous state to `release-history/`, and cuts a GitHub release with the
artifacts attached. You do not run this yourself; `scripts/bless.sh` is for local review only.

## Code contributions

- Java 21, built with Gradle from the repo root.
- Format before committing: `./gradlew :tools:spotlessApply` (Google Java Format). The
  pre-commit hook (`./gradlew installGitHooks`) does this for you.
- Add tests for new logic. Run the full suite with `./gradlew :tools:test`, or a single class
  with `./gradlew :tools:test --tests "ClassName"`.
- New rule types, chronology algorithms, or CLI commands should update `spec/SPEC.md` and
  `CLAUDE.md` alongside the code.

## Data corrections

Found a wrong date in an existing calendar? Same tools, smaller scope:

1. Update the source table row in `sources/<MARKET>/README.md` if the citation itself needs to
   change (new URL, later `retrieved` date), or add a new row for a document you are
   introducing.
2. Fix the YAML and re-run `validate` and the golden tests as above.
3. Expect a `MAJOR` `ci-diff` if the correction touches a past or already-published date; that
   is expected and will need the `calendar-change-approved` label.
4. If the correction resolves a known difference against a reference dataset, remove the
   matching row from that calendar's `allowlist.csv`. A correction PR should shrink the
   allowlist, not grow it: a growing allowlist usually means the fix is papering over a
   remaining disagreement instead of resolving it.

Cite your source. "I think this is wrong" is a good reason to open an issue; it is not a good
commit message.
