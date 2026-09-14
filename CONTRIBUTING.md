# Contributing

This project is a version-controlled dataset of business-day calendars. YAML specs are the
source of truth; the Java toolchain compiles them into CSV/JSON artifacts. This guide walks
through the most common contribution: adding a market. Smaller fixes (a wrong date, a missing
source) follow the same tools, described in "Data corrections" below.

## Add a market in an afternoon

This walkthrough adds a new exchange calendar end to end. It assumes a market that closes for
public holidays and has at least one authoritative source you can point to (an exchange
circular, a gazette notice, an official holiday list).

Start with the scaffold command to create the YAML skeleton, a canonical source register, a
generated source README and the reference-export entry:

```bash
./gradlew :tools:run --args="scaffold --market GB-LSE --name \"London Stock Exchange\" --timezone Europe/London --mic XLON"
```

It reuses the Saturday-Sunday weekend module by default. Use `--weekend FRI_SAT` to reuse the
Friday-Saturday policy, `--weekend custom` to create a policy module, `--from` and `--to` to set
the initial range, `--dry-run` to preview, and `--force` only when replacing existing scaffold
files. The scaffold creates a canonical `register.json` and generated README table with a TODO
source entry; it leaves evidence and calendar rules for you to complete, and does not create golden
tests. Structural validation is useful during development; publication additionally requires completed
evidence, reviewed rules and the release checks below.

### 1. Pick your ids

| Thing | Convention | Example |
|-------|------------|---------|
| Calendar file | `calendars/<CC>-<MARKET>.yaml` | `calendars/GB-LSE.yaml` |
| Calendar id | Same as the filename, no extension | `GB-LSE` |
| Module file | `modules/holidays\|policies\|groups/<snake_case_id>.yaml` | `modules/holidays/boxing_day.yaml` |
| Module id | snake_case, matches the filename | `boxing_day` |
| Source register | `sources/<MARKET>/register.json` | `sources/GB-LSE/register.json` |
| Generated source table | `sources/<MARKET>/README.md` | `sources/GB-LSE/README.md` |

`<CC>` is the ISO country code, `<MARKET>` a short exchange mnemonic. Use the same `<MARKET>`
token for the source directory as for the calendar id suffix.

### 2. Cite your sources first

Every holiday needs a citation, and `validate --strict` resolves citation IDs through the
canonical `sources/<MARKET>/register.json`. Add each source there with its original location,
retrieval date, description, preserved local files and SHA-256 checksums. For a `VERIFIED`
coverage claim, also record `support_intervals` for each scope and date range the source supports.
The free-text `covers` field is descriptive and does not establish coverage. Once the register is
complete, generate the table in `sources/<MARKET>/README.md`:

```markdown
# GB-LSE sources

| id | title | publisher | url / file | retrieved | covers | notes |
|----|-------|-----------|------------|-----------|--------|-------|
| `lse-hours` | LSE Holidays & Trading Hours | London Stock Exchange | https://www.londonstockexchange.com/... | 2026-09-13 | current and next year | Published holiday schedule |
```

Add an entry for every distinct document you transcribe from. `retrieved` is the date you pulled
the source; `covers` describes its scope. The README table is generated from `register.json` and
must not be edited as the source of truth. Run `python3 scripts/sources.py` to regenerate it, or
`python3 scripts/sources.py --check` to check it.

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
| `metadata.mic` | The market's ISO 10383 Market Identifier Code (e.g. `XLON`). `validate --strict` warns when a market-kind calendar has none, and errors on a malformed or duplicate one. |
| `metadata.aliases` | Other spellings this calendar should resolve under (legacy exchange_calendars ids, a segment MIC distinct from the primary `mic`, ...). |
| `metadata.coverage.from` / `to` | The date range you maintain this calendar for. |
| `metadata.coverage.quality` | Scope-specific intervals for `SCHEDULED_CLOSURES`, `EARLY_CLOSES`, and `UNSCHEDULED_EXCEPTIONS`, each marked `VERIFIED`, `PROJECTED`, or `INCOMPLETE`. |
| `weekend_shift_policy` | Default shift policy for weekend holidays: `NONE`, `NEAREST_WEEKDAY`, `NEXT_AVAILABLE_WEEKDAY`, or `FORWARD_ONLY`. |
| `uses` | Weekend policy module plus a holiday group module. |

```yaml
kind: calendar
id: GB-LSE

metadata:
  name: London Stock Exchange Trading Calendar
  chronology: ISO
  timezone: Europe/London
  mic: XLON
  coverage:
    from: 2000-01-01
    to: 2030-12-31
    quality:
      - {scope: SCHEDULED_CLOSURES, from: 2000-01-01, to: 2030-12-31, quality: INCOMPLETE, evidence_ids: []}
      - {scope: EARLY_CLOSES, from: 2000-01-01, to: 2030-12-31, quality: INCOMPLETE, evidence_ids: []}
      - {scope: UNSCHEDULED_EXCEPTIONS, from: 2000-01-01, to: 2030-12-31, quality: INCOMPLETE, evidence_ids: []}

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

For holidays dated in a native calendar, the compiler supports exact native dates and converts
them to ISO dates. For example, convert Hebrew 1 Tishri 5785 with the `HEBREW` chronology
provider:

```bash
./gradlew :tools:run --args="convert --from-chronology HEBREW --year 5785 --month-code TISHRI --day 1"
# 2024-10-03
```

The Hebrew provider uses a fixed arithmetic civil-date mapping; it does not model sunset instants.
Use month codes such as `TISHRI`, `NISAN`, or `ADAR_II` (the available code depends on whether the
Hebrew year is intercalary). Check the supported range and exact-date semantics before relying on
a conversion for source evidence. The bounded `CHINESE_HK` profile uses fixed UTC+08:00 civil-date
mapping; month codes are `M01`–`M12`, with an `L` suffix for an intercalary month. For example:

```bash
./gradlew :tools:run --args="convert --from-chronology CHINESE_HK --year 2025 --month-code M06L --day 1"
```

A successful native-date conversion establishes a chronology date, not an exchange opening day.
For TASE, actual-day assessments remain `UNKNOWN` wherever required scheduled-closure or
unscheduled-exception coverage is incomplete. Follow the calendar's scope-specific quality intervals
and exception-maintenance policy; do not mark a broader span verified from a conversion example.

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

### 8. Submit the reviewed calendar for release preparation

The release preparation workflow discovers calendar specifications, regenerates their declared
ranges and updates `blessed/manifest.json` with complete artifacts, counts, checksums and aliases.
Scaffolding leaves that published manifest unchanged. Submit the YAML, sources and reviewed test
expectations in the implementation PR; the subsequent release PR contains generated data and its
published-to-candidate impact report. Use the preview below to inspect local output.

### Preview your change

Before opening a PR, render the browsable site from what you just generated to see exactly what a
reviewer's `ci-diff` comment (and the CI-uploaded `site-preview` artifact) will show, styled as the
published pages:

```bash
./gradlew :tools:installDist
T=tools/build/install/tools/bin/tools
$T generate GB-LSE --from 2000-01-01 --to 2030-12-31 --out generated/GB-LSE --include-specs
$T site --blessed-dir generated --compare-to blessed --out site-preview
$T serve --dir site-preview --port 8080 --open
```

`generated/` only needs the calendar(s) you touched — the site synthesises its index from
whichever directories are present, so a partial local output is fine. `--compare-to blessed`
diffs each locally generated calendar against `blessed/` over the range you generated and adds a
"Changes vs blessed" banner to every affected year and date page, plus a `/changes/` page listing
every added, removed and modified date with its severity (a `MAJOR` change inside the calendar's
already-published range needs the `calendar-change-approved` label, same as `ci-diff`).
`tools serve` is a zero-dependency static file server for previewing the output — Ctrl-C stops it.

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
`calendar-change-approved` label after reviewing the diff comment. The release-impact comparator
classifies a new calendar as MINOR and examines the union of published and candidate IDs, including
calendars absent from the older manifest.

Before requesting review, self-check against `.github/PULL_REQUEST_TEMPLATE.md`.

### 10. Release and bless

After CI succeeds for a qualifying merge to `main`, `.github/workflows/release-pr.yml` prepares
one reviewable release PR. It compares the candidate with an authenticated immutable published
release asset. This release-impact comparison is separate from the development `ci-diff` above:
removals, coverage contractions, changed answers inside existing coverage and incompatible identity
changes are MAJOR; new calendars, additive aliases and coverage extensions are MINOR; descriptive
metadata or citation-only changes are PATCH. Exact severity is in `release/impact.json`.

The PR contains regenerated artifacts, immutable history, synchronized Python data and parity/API
fixtures, and `release/release.json`. The descriptor fixes the baseline, source SHA, versions,
generation timestamp and hashes. `scripts/bless.sh` performs strict validation and cross-validation;
a failed check aborts preparation. Review the complete report and intentional golden changes before
merging the release PR. Never reconstruct an old published baseline from today's modified data.

`.github/workflows/release.yml` publishes only after CI succeeds on the exact release merge commit.
It binds tags to that commit, builds once, verifies artifact hashes at every downstream step, and
resumes matching completed steps while rejecting conflicts. Publication runs are serialized without
cancelling active work. Dataset, Java core, Python software and wire-schema versions are independent;
`release/versions.json` records the selected versions. Manual dispatch supports recovery and retries.

Release PR creation requires the repository-scoped GitHub App configured with `RELEASE_APP_ID` and
`RELEASE_APP_PRIVATE_KEY`; its PRs run normal checks and do not bypass branch protection. Registry
credentials and repository settings must be configured for the intended publication channels.
The publication workflow deploys Pages directly. Configure Pages to use **GitHub Actions**;
there is no separate bot-push-triggered `pages.yml`. A local build is not evidence of remote
publication: package installation from each registry must succeed before that channel is complete.

## Code contributions

- Java 21, built with Gradle from the repo root.
- Format before committing: `./gradlew :tools:spotlessApply` (Google Java Format). The
  pre-commit hook (`./gradlew installGitHooks`) does this for you.
- Add tests for new logic. Run the full suite with `./gradlew :tools:test`, or a single class
  with `./gradlew :tools:test --tests "ClassName"`.
- New rule types, chronology algorithms, or CLI commands should update `spec/SPEC.md` and
  `AGENTS.md` alongside the code.

## Data corrections

Found a wrong date in an existing calendar? Same tools, smaller scope:

1. Update the source entry in `sources/<MARKET>/register.json` if the citation itself needs to
   change (new location, later `retrieved` date), or add a new entry for a document you are
   introducing. Regenerate `README.md` with `python3 scripts/sources.py`.
2. Fix the YAML and re-run `validate` and the golden tests as above.
3. Expect a `MAJOR` `ci-diff` if the correction touches a past or already-published date; that
   is expected and will need the `calendar-change-approved` label.
4. If the correction resolves a known difference against a reference dataset, remove the
   matching row from that calendar's `allowlist.csv`. A correction PR should shrink the
   allowlist, not grow it: a growing allowlist usually means the fix is papering over a
   remaining disagreement instead of resolving it.

Cite your source. "I think this is wrong" is a good reason to open an issue; it is not a good
commit message.

### Evidence and completeness for new calendars

Add `sources/<ID>/register.json` as the canonical source register, with the original URL, a retrieval date, and SHA-256 for every preserved local evidence file. Generate its README table with `python3 scripts/sources.py`; `--check` verifies consistency. Citation IDs must resolve for rules, deltas, and coverage claims. A retrieval timestamp records acquisition, not an announcement or publication date.

Declare scope-specific `coverage.quality` intervals for scheduled closures, early closes and unscheduled exceptions. `VERIFIED` requires evidence IDs whose canonical `support_intervals` cover the entire claimed interval for that scope. A gap in support is a validation error. Use `PROJECTED` only when the schedule model is explicitly documented; use `INCOMPLETE` where absent events cannot safely mean open. A date is `UNKNOWN` for business-day decisions if any required scope is incomplete or absent, even when the scheduled closure list is complete. Boolean, navigation and count operations fail with an unresolved-date error for such dates. Do not copy a calendar's desired coverage into a source's support intervals without reviewing the source. A source can establish scheduled holidays without establishing emergency closures.

A submission also needs independently sourced conversion/closure examples, an explanation of weekend and observation rules, and an exception-maintenance policy: which authority is reviewed, who maintains the calendar, and when evidence should be refreshed. The scheduled source-review workflow reports ageing citations and approaching coverage boundaries; it does not invent coverage extensions. Preserve source rights and third-party licence notices when adding evidence or generated datasets.
