# Calendar Specification

This document defines the YAML schema for calendar and module specifications.

## Calendar Spec

```yaml
kind: calendar
id: string                    # Unique identifier
metadata:
  name: string                # Human-readable name
  description: string         # Optional description
  kind: market                # market (a tradable venue, the default) or base (a building block)
  chronology: ISO             # Informational: the market's civil calendar (ISO, HIJRI, UMM_AL_QURA, ...)
  timezone: America/New_York  # IANA zone id; required when any event source has a close_time
  mic: XLON                   # Optional ISO 10383 Market Identifier Code; `validate --strict` warns
                               # when a market-kind calendar has none. Must match ^[A-Z0-9]{4}$;
                               # duplicate mics/aliases across calendars are errors.
  aliases: [XJPX]              # Optional other spellings this calendar should resolve under
                               # (legacy exchange_calendars ids, a segment MIC, ...)
  coverage:                   # The range this calendar is maintained for
    from: 1900-01-01
    to: 2030-12-31
    verified_through: 2026-12-31   # Dates up to here have been checked against sources
extends: [calendar-ids]       # Parent calendars to inherit from
uses: [module-ids]            # Modules to include
weekend_shift_policy: NONE    # Default shift policy for shiftable events (see below); omit to inherit
event_sources: [...]          # Event source definitions
classifications:
  event-key: CLOSED | NOTABLE | PERIOD_MARKER
deltas: [...]                 # Modifications
```

Unknown fields are rejected when loading (a misspelled field is an error, not a silent no-op).

## Module Spec

```yaml
kind: module
id: string
uses: [module-ids]            # Other modules to include (for composing groups)
source: {...}                 # Default citation for every event source in this module (see Sources)
references:
  - key: string               # Unique identifier for this reference
    formula: string           # EASTER_WESTERN, THANKSGIVING_US, EQUINOX_VERNAL_JP or
                              # EQUINOX_AUTUMNAL_JP (see "Reference formulas")
policies:
  weekends: [SATURDAY, SUNDAY]          # or effective-dated periods, see Weekend Policy
event_sources: [...]
```

When two modules (or a module and the calendar) declare an event source with the same `key`,
the later declaration in resolution order (parents, then modules in `uses` order, then the
calendar's own sources) replaces the earlier one. `validate` reports such overrides as a
`KEY_OVERRIDE` warning so accidental collisions are visible.

Modules can compose other modules using `uses` to create holiday groups:

```yaml
kind: module
id: us_nyse_holidays
uses:
  - us_market_base
  - mlk_day
  - presidents_day
  - good_friday
```

## Event Source Structure

Each event source has the following fields:

```yaml
event_sources:
  - key: string                  # Unique identifier
    name: string                 # Display name
    default_classification: CLOSED  # Event type (default: CLOSED)
    shiftable: true              # Whether to shift on weekends (see below)
    shift_policy: FORWARD_ONLY   # Optional per-event override of the calendar's policy
    displaces: [other_key]       # Optional: CLOSED keys this event outranks when it shifts
    only_if_weekday: [MONDAY, TUESDAY, THURSDAY]  # Optional: drop occurrences on other weekdays
    close_time: "13:00"          # Local close time for EARLY_CLOSE events (quote it in YAML)
    status: CONFIRMED            # CONFIRMED (default) or PROJECTED
    active_years: [...]          # Optional: list of active year ranges (see below)
    source: {...}                # Citation (see Sources); inherits the module's source if omitted
    rule: {...}                  # Rule definition (see types below)
```

The rule's own `key`/`name` are optional and default to the event source's; if both are given
they must agree.

### Shiftable and shift_policy

`shiftable` controls whether this event follows the calendar's `weekend_shift_policy` when it
falls on a weekend. Defaults to `true` for `fixed_month_day` rules, `false` for others.
`shift_policy` sets the policy for this event regardless of the calendar default (NYSE New
Year's Day is `FORWARD_ONLY` while Christmas is `NEAREST_WEEKDAY`). `EARLY_CLOSE` sources ignore
both `shiftable` and the calendar default: without an explicit `shift_policy` they are `DROP`.

### only_if_weekday

Keeps only occurrences that fall on the listed weekdays (evaluated on the nominal date, before
shifting). Used for rules such as "the NYSE closes early on July 3 when it is a Monday, Tuesday
or Thursday".

### close_time and status

`close_time` is the local wall-clock close for `EARLY_CLOSE` events and is emitted in the
output; the calendar's `metadata.timezone` says which wall clock. `status` marks whether the
dates come from an authoritative announcement (`CONFIRMED`) or are computed from a rule and may
change once announced (`PROJECTED`, typical for observation-based lunar calendars).

### Active Years

Use `active_years` to specify which years the event is active. Supports:

- Single years: `1972`
- Open-ended ranges: `[null, 1968]` (from inception through 1968) or `[2022, null]` (from 2022 onwards)
- Closed ranges: `[1990, 2000]` (1990 through 2000 inclusive)

If `active_years` is omitted, the event is active for all years.

```yaml
# Juneteenth became a federal holiday in 2021
- key: juneteenth
  name: Juneteenth National Independence Day
  active_years:
    - [2022, null]   # Active from 2022 onwards
  rule:
    type: fixed_month_day
    month: 6
    day: 19
```

```yaml
# Election Day: every year through 1968, then only presidential years
- key: election_day
  name: Election Day
  active_years:
    - [null, 1968]   # Annual through 1968
    - 1972           # Presidential election
    - 1976           # Presidential election
    - 1980           # Presidential election
  rule:
    type: relative_to_reference
    reference_month: 11
    reference_day: 1
    offset_weekday:
      weekday: TUESDAY
      nth: 1
      direction: AFTER
```

## Event Source Rule Types

### explicit_dates

List specific dates, optionally with comments:

```yaml
rule:
  type: explicit_dates
  key: good_friday
  name: Good Friday
  dates:
    - 2024-03-29
    - 2025-04-18
```

With comments (for historical context):

```yaml
rule:
  type: explicit_dates
  key: national_mourning
  name: National Day of Mourning
  dates:
    - date: 1994-04-27
      comment: Richard Nixon
    - date: 2004-06-11
      comment: Ronald Reagan
```

The comment is appended to the event name in output: "National Day of Mourning (Richard Nixon)"

### fixed_month_day

```yaml
rule:
  type: fixed_month_day
  month: 12
  day: 25
  chronology: ISO    # or HIJRI, UMM_AL_QURA, JULIAN, PERSIAN
```

A rule may span several days, emitting one occurrence per day under the same key:

```yaml
rule:
  type: fixed_month_day
  chronology: UMM_AL_QURA
  month: 9        # 28 Ramadan ...
  day: 28
  end_month: 10   # ... through 4 Shawwal (inclusive; wraps to the next year if before the start)
  end_day: 4
```

`duration_days: N` is the alternative form (N consecutive days from the start date) and is
also accepted by `nth_weekday_of_month` and `relative_to_reference`. Dates that do not exist in
a given year (Feb 29, the 30th of a 29-day lunar month) are skipped. For table-based
chronologies the generation range is clamped to the table's coverage; `validate` reports a
calendar whose `coverage` exceeds the table.

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

Calculate dates relative to a reference point. Supports two reference types and two offset types.

#### With Named Reference (e.g., Easter)

```yaml
rule:
  type: relative_to_reference
  key: good_friday
  name: Good Friday
  reference: easter      # Key of a reference defined in this module
  offset_days: -2        # Days to add (negative = before, positive = after)
```

#### With Fixed Month/Day Reference

```yaml
rule:
  type: relative_to_reference
  key: week_after_nov1
  name: Week After November 1st
  reference_month: 11
  reference_day: 1
  offset_days: 7
```

#### With Weekday Offset

Find the nth weekday before or after a reference date. Useful for rules like "first Tuesday after November 1st" (Election Day).

```yaml
rule:
  type: relative_to_reference
  key: election_day
  name: Election Day
  reference_month: 11
  reference_day: 1
  offset_weekday:
    weekday: TUESDAY     # MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY, SUNDAY
    nth: 1               # 1 = first, 2 = second, etc.
    direction: AFTER     # AFTER or BEFORE (strictly after/before, not including reference date)
```

Note: The weekday offset finds occurrences strictly before or after the reference date. If November 1st is a Tuesday and you're looking for the first Tuesday after November 1st, you'll get November 8th (not November 1st).

## Event Types

- `CLOSED` - Market/business closed
- `EARLY_CLOSE` - Early closure (partial day)
- `NOTABLE` - Notable event (not closure)
- `PERIOD_MARKER` - Period boundary marker
- `WEEKEND` - Weekend day (generated automatically based on weekend policy)

## Weekend Policy

`policies.weekends` in a module is either a flat list of weekdays (one open-ended period) or a
list of effective-dated periods. For a given date the last period covering it decides the
weekend days; a date covered by no period has no weekend.

```yaml
policies:
  weekends:
    - {days: [SUNDAY], to: 1952-05-30}                         # NYSE traded Saturdays until 1952
    - {days: [SATURDAY, SUNDAY], from: 1945-07-07, to: 1945-09-01}   # summer Saturday closings
    - {days: [SATURDAY, SUNDAY], from: 1952-05-31}
```

Periods from different modules that overlap with different days are a resolution error;
identical periods reached twice (diamond dependencies) are deduplicated.

## Weekend Shift Policy

Controls how an event whose nominal date is unavailable is observed. The calendar's
`weekend_shift_policy` is the default for shiftable CLOSED events; `shift_policy` on an event
source overrides it. The first five values below answer "a CLOSED holiday fell on a weekend day,
where is it observed?"; the last two answer the EARLY_CLOSE question and are described after them.

- `NONE` - No shifting; the holiday stays on the weekend day (emitted as CLOSED)
- `NEAREST_WEEKDAY` - US-style: first day of a two-day weekend shifts back, last day shifts
  forward (Saturday to Friday, Sunday to Monday); ties on longer weekends go forward. Does not
  cascade.
- `NEXT_AVAILABLE_WEEKDAY` - UK-style: shifts to the next weekday that is not already a
  closure (cascading: Christmas Saturday to Monday, Boxing Day Sunday to Tuesday)
- `FORWARD_ONLY` - shifts forward only when the holiday is on the last day of the weekend
  block (Sunday to Monday); on any other weekend day it is not observed. NYSE New Year's Day.
- `NEXT_AVAILABLE_FROM_LAST_WEEKEND_DAY` - Japanese-style substitute holiday (振替休日): shifts
  only when the holiday falls on the last day of the weekend block, to the next weekday that is
  not already a closure (cascading). A Saturday holiday is not observed at all. This is
  `FORWARD_ONLY`'s "last weekend day only" test with `NEXT_AVAILABLE_WEEKDAY`'s cascade: Japan
  observes a Sunday holiday on "the closest following day that is not a national holiday", so
  Sunday May 3 2026 is observed on Wednesday May 6 past the May 4 and May 5 holidays, while
  Saturday February 11 2023 is not made up on either side.

The next two values apply to `EARLY_CLOSE` events instead. An early close is "blocked" when its
nominal date is not a session: the date is a weekend day, or a CLOSED event occupies it after
CLOSED placement (see *Same-date precedence*).

- `DROP` - the blocked occurrence is simply not observed. **This is the default for every
  `EARLY_CLOSE` event source**, regardless of `shiftable` and regardless of the calendar's
  `weekend_shift_policy`, which governs full closures only. `DROP` on a CLOSED source is a
  validation error (a CLOSED event either shifts or stays on the weekend day: use `NONE`).
- `PREVIOUS_AVAILABLE_BUSINESS_DAY` - the blocked occurrence moves back to the nearest earlier
  date that is neither a weekend day nor carries a CLOSED event, searching at most **7 days**
  back; if no such date is found the occurrence is dropped. The moved row carries `observed_from`.
  Same-date precedence still applies to the landing date by construction, since a date holding a
  CLOSED event is never chosen. The London Stock Exchange 12:30 half day on 24/31 December uses
  this: Sunday 24 December 2028 is observed on Friday 22 December 2028.

Shifting a CLOSED event only ever happens when its nominal date is a weekend day; an early close
under `PREVIOUS_AVAILABLE_BUSINESS_DAY` also moves when a full closure has taken its nominal
date. Observed events carry `observed_from` (the nominal date) in the output.

### Observance priority (`displaces`)

Cascading policies resolve collisions by "first come, first served": an event whose own nominal
date is an ordinary weekday is placed before any weekend event's shift is resolved, so the
shifting event cascades past it. That is correct for the UK (Boxing Day keeps Monday 26 December
and a Sunday Christmas cascades to Tuesday 27 December) and wrong for Canada, where TMX observes a
Sunday Christmas on the Monday "in lieu of Christmas Day" and pushes Boxing Day to the Tuesday.

`displaces` on an event source names the keys this event outranks:

```yaml
- key: ca_christmas
  name: Christmas Day
  displaces: [ca_boxing_day]
  rule: {type: fixed_month_day, month: 12, day: 25}
```

Semantics, precisely:

1. `displaces` affects **CLOSED** events only, and only while a shift is being resolved. It is
   ignored (with a validation warning) on other classifications. Default: empty - no displacement,
   which is the "first come, first served" behaviour above.
2. While a policy searches for a slot, a candidate date counts as **available** when it is not a
   weekend day **and** it is either empty or occupied *exclusively* by occurrences whose keys are
   all listed in the shifting event's `displaces`. A date holding even one occurrence the event
   may not displace is skipped as before.
3. When the chosen slot was occupied, the occupants are evicted, the shifting event is placed
   there with its own `observed_from` (its nominal date), and each evicted occurrence
   **re-cascades**.
4. **Re-cascade rule.** A displaced occurrence moves to the first date strictly after the slot it
   lost that is neither a weekend day nor already carrying a CLOSED event - that is,
   `NEXT_AVAILABLE_WEEKDAY`'s cascade applied from the taken slot, *whatever the displaced event's
   own `shift_policy` is*. Displacement only ever pushes an event later, so `NEAREST_WEEKDAY`'s
   backward branch and the "not observed at all" branches of `FORWARD_ONLY` and
   `NEXT_AVAILABLE_FROM_LAST_WEEKEND_DAY` never apply to a displaced event. The displaced
   occurrence keeps its original nominal date in `observed_from` (or gains its pre-displacement
   date as `observed_from` if it had not shifted before).
5. The re-cascade honours the displaced event's *own* `displaces` list, so priorities chain. An
   implementation must bound the chain (this one stops after 16 displacements) so that a cycle
   terminates.
6. `validate` **errors** on a `displaces` key that matches no event source in the calendar, and on
   an event that lists its own key; it **warns** on a cycle in the `displaces` graph, whose
   outcome depends on declaration order and is therefore not a well-defined model.

Worked example (`CA-TSX`, weekend Sat-Sun, `weekend_shift_policy: NEXT_AVAILABLE_WEEKDAY`,
`ca_christmas` declaring `displaces: [ca_boxing_day]`):

| Year | Dec 25 | Placement |
|------|--------|-----------|
| 2022 | Sunday | Boxing Day is placed on its own Monday 26th. Christmas shifts: Monday 26th is held only by `ca_boxing_day`, so it is available. Christmas takes Monday 26th (`observed_from` 2022-12-25); Boxing Day re-cascades to Tuesday 27th (`observed_from` 2022-12-26). |
| 2021 | Saturday | Both are weekend events. Christmas resolves first (earlier nominal date) to Monday 27th; Boxing Day then cascades to Tuesday 28th. No displacement occurs. |
| 2026 | Friday | Christmas stays on Friday 25th. Boxing Day (Saturday 26th) cascades to Monday 28th. No displacement occurs. |

Without `displaces` the same two sources produce the UK ordering, which is what `GB-LSE` relies
on: Boxing Day keeps Monday and Christmas cascades to Tuesday.

**Ports.** `shift_policy` (including `DROP` and `PREVIOUS_AVAILABLE_BUSINESS_DAY`) and `displaces`
are *generation-time* rules: they decide which dates end up in `events.csv`/`events.json`. Any
implementation that compiles these YAML specs must implement them to produce the same artifacts.
Consumers that read the generated artifacts - the Python package in `python/` among them - are
unaffected: they see only the resulting dates and the `observed_from` column, both of which already
exist in the output contract.

### Same-date precedence

A date can carry at most one of CLOSED or EARLY_CLOSE: a full closure suppresses an early close
on the same date (Christmas observed on Friday December 24 removes the Christmas Eve early
close). An EARLY_CLOSE on a weekend day is likewise not a session. In both cases the early
close's own `shift_policy` decides what happens: `DROP` (the default) discards it, and
`PREVIOUS_AVAILABLE_BUSINESS_DAY` moves it back to the nearest earlier session (see *Weekend
Shift Policy*). NOTABLE and PERIOD_MARKER events are
informational and are always kept; a weekend day with only informational events still gets its
WEEKEND row. `validate` reports two CLOSED events on one date as a warning.

### Date range consistency

Generation uses inclusive output ranges, and the result does not depend on the range asked for.
Whenever both requests succeed, generating a narrower range returns exactly the events of a wider
generation filtered to that narrower range - same types, descriptions, `observed_from` values,
duplicate rows and provenance - and two adjacent ranges concatenate to the range that spans them.
Exports, single-day queries, business-day counts and date navigation all use the same engine, so
they agree with each other by construction.

This holds because rules are expanded over a *padded* range rather than the requested one, and the
result filtered back to the request at the end. The padding is a year either side - enough for the
chronology-year rounding rule expansion does, for the at most one week a shift moves an event
backwards, and for the at most 60 days a `NEXT_AVAILABLE_WEEKDAY` cascade moves one forwards -
plus the furthest reach of any `relative_to_reference` rule in the calendar, since an offset of
800 days or a 60th weekday after a fixed date reaches past a year. A fixed one-year window is not
enough: with one, whether a long-offset occurrence appeared at all depended on the requested
range.

Active years are evaluated on each occurrence's original, unshifted ISO date. Observation happens
before deltas: removing an event with a delta does not free the date it was observed on for
another event, and a delta addition does not block a cascade. Invalid recurring month days, such
as February 29 in a non-leap year or day 30 of a 29-day lunar month, remain absent rather than
being moved.

Example:

```yaml
kind: calendar
id: US-NYSE
weekend_shift_policy: NEAREST_WEEKDAY
uses:
  - weekend_sat_sun
  - us_nyse_holidays
```

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
2. Resolve `uses` (modules) in order (depth-first)
3. Merge local content (event sources by key, later wins; an explicit `weekend_shift_policy`,
   including `NONE`, overrides the inherited one)
4. Expand rules over a padded range, filter `active_years` (on the nominal year) and
   `only_if_weekday`, place CLOSED events with weekend shifting, apply precedence
5. Apply deltas against observed dates
6. Emit WEEKEND rows, sort

## Reference formulas

`references[].formula` names a computation the generator knows. `validate` rejects any other
value.

| Formula | Result |
|---------|--------|
| `EASTER_WESTERN` | Western (Gregorian) Easter Sunday |
| `THANKSGIVING_US` | Fourth Thursday of November |
| `EQUINOX_VERNAL_JP` | Japan's Vernal Equinox Day (春分の日) |
| `EQUINOX_AUTUMNAL_JP` | Japan's Autumnal Equinox Day (秋分の日) |

The two Japanese equinox holidays are set each February by the Cabinet Office from the National
Astronomical Observatory's almanac, so they cannot be computed exactly in advance. Both formulas
use the standard approximation, valid 1900-2099 and an error outside that range:

```
vernal   day-of-March     = floor(20.8431 + 0.242194 * (year - 1980) - floor((year - 1980) / 4))
autumnal day-of-September = floor(23.2488 + 0.242194 * (year - 1980) - floor((year - 1980) / 4))
```

`EquinoxCalculatorTest` checks both against every year of the Cabinet Office's published list.
Occurrences past the end of that list are projections and should carry `status: PROJECTED`.

## Sources

Every event source should cite where its dates come from. A citation is an object
`{id, title, publisher, url, file, retrieved, ref, note}` or a bare id string; `id` refers to a
row in `sources/<MARKET>/README.md`. A module-level `source:` applies to all of its event
sources. `validate --strict` fails on an event source without a resolvable citation.

## Output

`events.csv` columns: `date[,<chronology>_date],type,description,key,source_module,observed_from,close_time,status`.
`events.json` carries the same rows with the calendar id, timezone, range and coverage.
`metadata.json` adds `timezone`, `coverage` and `counts_by_status`.

## Validation

`validate [<id> | --all] [--strict] [--format text|json]` checks, per calendar: unknown or
duplicate ids, unknown modules/references/formulas/chronologies, rule sanity (`nth`, month/day
ranges, empty or duplicate explicit dates), rule/event-source identity mismatches, weekend
period conflicts, missing `source`, `coverage`, `timezone` or `close_time`, redundant `uses`,
classifications and deltas that match nothing, `shift_policy: DROP` on a CLOSED source,
unknown or self-referencing `displaces` keys (error) and `displaces` cycles (warning),
lookup-table chronology coverage, and, after
generating over the coverage range, same-date CLOSED/EARLY_CLOSE conflicts, rules that produce
nothing, and PROJECTED events before `verified_through`. Exit code 1 on errors, 2 on warnings
under `--strict`.

## Status and cross-validation artifacts

`crossvalidate [<ID>|--all] [--reference-dir <dir>] [--format text|json] [--out <dir>]` runs the
comparison in `CrossValidator` (also used by `ReferenceCrossValidationTest`) against every
`<reference-dir>/<ID>/*.csv` file for the selected calendar(s) (default reference dir:
`tools/src/test/resources/reference`). With `--out <dir>` it writes `<dir>/<ID>/cross_validation.json`
for every cross-validated calendar:

```json
{
  "calendar_id": "US-NYSE",
  "status": "ok",          // "ok" | "discrepancies" | "no-reference"
  "results": [
    {
      "source": "exchange_calendars-XNYS",
      "library_version": "exchange_calendars 4.11, python 3.13.12, 2026-09-12",
      "from": "1971-01-01",
      "to": "2030-12-31",
      "compared_types": ["CLOSED", "EARLY_CLOSE"],
      "counts": { "matched": 620, "allowlisted": 9, "unexplained": 0 },
      "unexplained_rows": [],
      "stale_allowlist_rows": [],
      "status": "ok"        // "ok" | "discrepancies"
    }
  ]
}
```

Exit code 1 when any result has unexplained rows or a stale allowlist entry. `scripts/bless.sh`
runs `crossvalidate --all --out blessed` after generating every calendar; the release workflow
does the same before committing.

`status [--format markdown|json] [--blessed-dir blessed] [--sources-dir sources]
[--calendars-dir calendars] [--modules-dir modules]` builds a scorecard, one row per calendar in
`blessed/manifest.json` (markets first, then base, alphabetically within each group), from
`blessed/<ID>/metadata.json`, cited sources and `blessed/<ID>/cross_validation.json` (`"none"` when
absent).

When `--calendars-dir` exists (the default `calendars/`), sources are computed by resolving the
calendar with `SpecResolver` (`sources_basis: "resolved"`): the distinct citation ids (plus
titles/urls/files for citations without an id) attached to every resolved event source, unioned
with every id documented in `sources/<CAL>/README.md` for the calendar itself and for every
calendar in its `extends` chain (so a calendar that only `extends` a base — the four Euronext
venues extending `EU-EURONEXT`, for instance — reports the base's citations instead of zero). The
union means a README can also carry background sources no single event cites directly without
being dropped; a citation id that isn't documented anywhere in the chain is reported under
`unresolved` (and, in `--format markdown`, as a warning line on stderr). When `calendars/` is
absent (a blessed-only environment), `status` falls back to the previous directory-only count —
just the ids listed in `sources/<ID>/README.md` — and reports `sources_basis: "directory"`.

JSON shape, one entry per calendar:

```json
{
  "id": "FR-EURONEXT-PARIS",
  "name": "Euronext Paris Trading Calendar",
  "kind": "market",
  "timezone": "Europe/Paris",
  "coverage": { "from": "2010-01-01", "to": "2030-12-31", "verified_through": "2026-12-31" },
  "counts": { "closures": 127, "early_closes": 29, "projected": 0 },
  "sources": {
    "ids": ["euronext-hours-holidays", "euronext-holiday-calendar-2026", "..."],
    "count": 8,
    "readmes": {
      "euronext-hours-holidays": "sources/EU-EURONEXT/README.md",
      "euronext-holiday-calendar-2026": "sources/EU-EURONEXT/README.md"
    },
    "unresolved": []
  },
  "sources_basis": "resolved",
  "cross_validation": {
    "exchange_calendars-XPAR": { "status": "ok", "allowlisted": 2 }
  },
  "release_version": "11.0.0"
}
```

`--format markdown` renders the same data as a table (see the "Market status" section of
`README.md`), one column per field above; the Sources cell shows the count, with
` (n unresolved)` appended when `unresolved` is non-empty.

## Published artifacts and as-of queries

`blessed/` holds the current release and `release-history/<CAL>/<timestamp>_<sha>_v<version>/`
the previous ones. `query <CAL> --as-of <blessed|vX.Y.Z|date>` answers from a published
artifact instead of the current YAML; `history releases <CAL>` lists them. The release workflow
retains only the most recent 30 versions per calendar in `release-history/`, so pinned release
files (and `--as-of` lookups by version or date) are only available for retained versions; older
releases remain accessible via git history.

## Query API

Every distribution channel — the `query` CLI, the published JSON artifacts, language bindings, the
MCP server — answers from the same API, described here precisely enough to be reimplemented in
another language without reading the Java.

A **stream** is one calendar made queryable. Two implementations exist and behave identically:
a lazy stream over the current YAML (events generated on demand) and a materialized stream over a
published artifact's `events.csv` (used by `--as-of`). A stream exposes:

| Property | Meaning |
|----------|---------|
| `calendar_id` | the calendar this stream answers for |
| `range` | the window the stream can answer in: `metadata.coverage` (`from`..`to`) for a YAML-backed stream, `range_start`..`range_end` for an artifact-backed one. A calendar with no `coverage` block is unbounded |
| `verified_through` | `coverage.verified_through`, when declared |

### Definitions

- A date is a **business day** when the market trades on it: it is not a weekend under the
  calendar's (effective-dated) weekend policy and carries no `CLOSED` event. An `EARLY_CLOSE` day
  **is** a business day. In an artifact-backed stream the same test reads as: the date carries
  neither a `WEEKEND` nor a `CLOSED` row.
- An **early close** is a date carrying an `EARLY_CLOSE` event with a `close_time`. `CLOSED` beats
  `EARLY_CLOSE` on the same date (see *Same-date precedence*), so a date is never both.
- All dates are ISO (Gregorian) civil dates in the market's own timezone; `close_time` is a local
  time in `metadata.timezone`.

### Operations

| Operation | Result |
|-----------|--------|
| `events_in_range(from, to)` | every event with `from <= date <= to`, ordered by date. Errors if `from > to` |
| `events_on(date)` | every event on that date, possibly empty |
| `event_on(date)` | the first of `events_on(date)`, or none |
| `is_business_day(date)` | as defined above |
| `next_business_day(from)` | the first business day **strictly after** `from` |
| `prev_business_day(from)` | the last business day **strictly before** `from` |
| `nth_business_day(from, n)` | walk day by day from `from`, counting business days, `n` forward (`n > 0`) or backward (`n < 0`); `n = 0` returns `from` unchanged whether or not it is a business day. The starting date is never counted, so T+1 from a Friday is the following Monday |
| `business_days_in_range(from, to)` | count of business days with **both endpoints included** |
| `event_count_in_range(from, to)` | count of events in the range, endpoints included, weekend rows included where the stream carries them |
| `is_early_close(date)` | whether `close_time(date)` has a value |
| `close_time(date)` | the local close time of a shortened session; where several apply, the earliest |
| `status(date)` | `CONFIRMED`, `PROJECTED` or `UNKNOWN` (see below) |

Searches are bounded so a mis-specified calendar cannot loop forever: `next_business_day` and
`prev_business_day` scan at most **366** days and then fail; `nth_business_day` walks at most
`366 * |n| + 366` days.

### Status

`status(date)` says how much the answer can be trusted. It is evaluated in this order and **never
raises**:

1. `UNKNOWN` — the date lies outside `range`.
2. `PROJECTED` — the date is after `verified_through`. This wins over whatever the rows say: a
   `CONFIRMED` row past the verified horizon has not been checked against a source, so it is
   reported as projected.
3. `PROJECTED` — any event on the date carries `status: PROJECTED` (rule-derived dates in
   observation-based calendars, for example).
4. `CONFIRMED` — otherwise.

### Out-of-range contract

Outside a stream's `range` the absence of a closure row means "not known", never "open". Therefore:

- `status(date)` returns `UNKNOWN`; it is the safe way to probe an unfamiliar date first.
- **Every other operation raises** an out-of-range error (`OutsideCoverageException` in Java; ports
  should raise their own equivalent, an invalid-argument error) carrying the calendar id, the
  offending date and the range. This includes navigation whose bounded search would step past the
  edge of the range: `next_business_day(2030-12-31)` on a calendar covered through 2030-12-31
  fails rather than guessing.
- A YAML-backed stream enforces this only when the calendar declares `coverage`; without one it is
  unbounded and never raises for being out of range. Artifact-backed streams are always bounded by
  the generated range.
- The CLI turns the error into a one-line message on stderr and exit code 1.

### Joint calendars (several calendars at once)

Several calendars can be queried as one **joint** stream: a date is a business day only when
**every** member trades on it. The two ways of naming this — "intersection of trading days" and
"union of closures" / "closed in any" — are the same predicate (`open(A) and open(B)` is the
negation of `closed(A) or closed(B)`), so the API offers exactly **one** constructor for it rather
than two names for one behaviour. A different predicate (for example "open in any member") would be
a different constructor; none is defined. The joint of a single calendar is that calendar itself.

Composition rules:

| Property | Joint value |
|----------|-------------|
| `calendar_id` | member ids joined with `+`, in the order given (`US-NYSE+SA-TADAWUL`) |
| `is_business_day` | true only when true for every member |
| `events_on` / `events_in_range` | every member's events concatenated in member order (range queries then sorted by date, stably), each with its `source_module` prefixed by the member id: `US-NYSE/module:christmas`. A member event with no source module gets the bare member id |
| `range` | the intersection of the member ranges; members whose ranges do not overlap are an error |
| `verified_through` | the earliest value among the members that declare one; members that declare none do not constrain it; absent when no member declares one |
| `status` | `UNKNOWN` if any member is `UNKNOWN`, else `PROJECTED` if any member is `PROJECTED`, else `CONFIRMED` — the joint answer is only as good as its worst member |
| `close_time` | the earliest early close declared by any member that day. A joint date can carry an early close and still not be a business day (another member is closed), so check `is_business_day` first |

Every derived operation (navigation, counting, settlement) follows from the joint `is_business_day`,
which is what makes T+N settlement across markets correct: a trade settles only on a day both
markets are open.

### Settlement in the browser

The compare pages on the published site carry a T+N settlement form that answers from the JSON API
in the reader's browser. It is a *reimplementation* of the joint stream — no Java runs — so this
section is the contract it implements, and the reference for any other client that walks the
published files directly rather than calling a library. The implementation lives in
`tools/src/main/resources/site/site.js`.

Inputs, per calendar, both fetched from `/v1/`:

| Input | File | Use |
|-------|------|-----|
| `weekend_policy` | `calendars/<ID>/manifest.json` | which weekdays are non-trading, effective-dated |
| `CLOSED` rows | `calendars/<ID>/<year>.json` | full-day closures, by date |

Fetch the year file for the trade date's year **and the following year**: a T+10 walk from late
December crosses the boundary, and a year the API does not publish is outside coverage.

The algorithm:

1. **Weekend.** `weekend_policy` is a list of `{days, from?, to?}` periods. For a date, the **last**
   period whose `from`/`to` (either may be absent, meaning unbounded) contains it decides; its
   `days` are the weekend days. A date no period covers has **no** weekend days. A policy with no
   `periods` is one unbounded period taking the policy's `days`.
2. **Business day.** A date is a business day for a calendar when it carries no `CLOSED` row **and**
   is not a weekend day under step 1. `EARLY_CLOSE` days are business days. Do not test `WEEKEND`
   rows instead of the policy: the policy is the normative statement, the rows are its expansion.
3. **Joint business day.** A date is a joint business day when it is a business day for *every*
   calendar in the group — the same predicate as the joint stream above.
4. **Walk.** `N = 0` returns the trade date unchanged, business day or not. Otherwise walk forward
   one day at a time from the trade date, never counting the trade date itself, decrementing the
   remaining count on each joint business day; the date where the count reaches zero is the
   settlement date. Report every day walked past, and for each, which calendars were closed on it.
   Bound the walk at `366 * N + 366` days and fail rather than loop.
5. **Out of range.** If the walk reaches a year the API does not publish for one of the calendars,
   raise rather than treat the missing rows as "open" — the out-of-range contract above applies
   unchanged to a browser client.

This must agree with `JointDateStream` date for date. `/compare/settlement-selftest.html` ships a
fixture of fifty (pair, trade date, N) cases whose answers were computed in Java, runs the browser
algorithm against the published API on page load, and reports pass/fail in the page; the fixture
itself is regenerated and re-checked against `JointDateStream` by `SettlementParityTest`.

### CLI surface

```
query <CAL[,CAL...]> [options]
  --is-business-day <date>       business-day test, with the reason when closed
  --events-on <date>             events on a date
  --next-business-day <date>     first business day after
  --prev-business-day <date>     last business day before
  --nth-business-day <n> --from <date>
  --business-days-from <date> --business-days-to <date>
  --settlement T+N --from <trade date>
                                 settlement date on the joint calendar, listing which member
                                 calendars are closed on each intervening day
  --open-in <cals> --closed-in <cals> --from <date> --to <date>
                                 dates open in one calendar (or joint group) and closed in another
  --is-early-close <date>        shortened session test
  --close-time <date>            early close time, "regular session" or "closed"
  --status <date>                CONFIRMED | PROJECTED | UNKNOWN
  --verified-through             covered range and verified-through date
  --as-of <blessed|vX.Y.Z|date>  answer from a published artifact instead of the current YAML
```

The positional argument takes a comma-separated list of calendar ids, which is queried jointly
(`query US-NYSE,SA-TADAWUL --settlement T+2 --from 2026-02-25`). With `--as-of`, every member is
resolved independently through the release history and the joint stream is built from the resulting
artifacts, so a joint as-of query is answered entirely from published data.

## Chronology Support

The calendar system supports multiple chronologies (calendar systems) through a YAML-based ontology. All chronologies use Julian Day Number (JDN) as a universal pivot for cross-calendar translation.

### Built-in Chronologies

| ID | Name | Description |
|----|------|-------------|
| `ISO` | Gregorian Calendar | Default. Standard ISO dates (YYYY-MM-DD) |
| `HIJRI` | Islamic Calendar | Tabular arithmetic approximation (30-year cycle) |
| `UMM_AL_QURA` | Umm al-Qura Calendar | Saudi civil calendar, lookup table AH 1356-1500 (~1937-2076) |
| `JULIAN` | Julian Calendar | Historical Julian calendar (every 4th year is leap) |
| `PERSIAN` | Solar Hijri Calendar | Iranian calendar (2820-year cycle) |

### Using Chronologies in Rules

Specify a chronology in `fixed_month_day` rules:

```yaml
rule:
  type: fixed_month_day
  key: eid_al_fitr
  name: Eid al-Fitr
  month: 10        # Shawwal
  day: 1
  chronology: HIJRI
```

The date is automatically converted to ISO dates for each relevant year in the generation range.

### Year Ranges

When working with non-ISO chronologies, year ranges are automatically translated:

```java
DateRange range = new DateRange(LocalDate.of(2024, 1, 1), LocalDate.of(2025, 12, 31));
int[] hijriYears = range.yearRange("HIJRI");  // Returns [1445, 1447] approximately
int[] julianYears = range.yearRange("JULIAN"); // Returns [2023, 2025]
```

## Chronology Ontology

New chronologies can be added without code changes using YAML definitions.

### Chronology Spec Schema

```yaml
kind: chronology
id: string                    # Unique identifier (e.g., JULIAN, PERSIAN)

metadata:
  name: string                # Human-readable name
  description: string         # Description of the calendar

structure:
  epoch_jdn: number           # Julian Day Number of the epoch (year 1, month 1, day 1)
  week:
    days_per_week: 7
    first_day: MONDAY         # First day of the week
  months:
    - name: string            # Month name
      days: number            # Days in common year
      leap_days: number       # Days in leap year (optional, if different)

algorithms:
  type: FORMULA | LOOKUP_TABLE | METONIC_CYCLE
  leap_year: string           # Leap year formula (for FORMULA type)
  table: string               # Table ID (for LOOKUP_TABLE type)
  fallback: string            # Fallback algorithm ID (for LOOKUP_TABLE type)
```

### Algorithm Types

**FORMULA** - Simple algorithmic calendars with a leap year formula:

```yaml
kind: chronology
id: JULIAN
metadata:
  name: Julian Calendar
  description: Calendar introduced by Julius Caesar

structure:
  epoch_jdn: 1721424
  months:
    - {name: January, days: 31}
    - {name: February, days: 28, leap_days: 29}
    - {name: March, days: 31}
    # ... etc

algorithms:
  type: FORMULA
  leap_year: "year % 4 == 0"
```

**Supported leap year formula expressions:**
- Variables: `year`
- Arithmetic: `+`, `-`, `*`, `%` (modulo)
- Comparison: `==`, `!=`, `<`, `>`, `<=`, `>=`
- Logical: `&&`, `||`
- Grouping: `()`
- Literals: integers, `true`, `false`

Examples:
- Julian: `"year % 4 == 0"`
- Gregorian: `"(year % 4 == 0 && year % 100 != 0) || (year % 400 == 0)"`
- Tabular Hijri: `"((11 * year + 14) % 30) < 11"` (30-year cycle with 11 leap years)
- Persian: `"((((year - 474) % 2820) + 474 + 38) * 682 % 2816) < 682"` (2820-year cycle)

**LOOKUP_TABLE** - Observation-based calendars requiring precomputed data:

For calendars that cannot be computed algorithmically (e.g., Umm al-Qura Islamic
calendar based on moon sighting), lookup tables provide month boundaries:

```yaml
kind: chronology
id: UMM_AL_QURA
metadata:
  name: Islamic Calendar (Umm al-Qura)
  description: |
    Observation-based Islamic calendar used in Saudi Arabia.
    Requires lookup table data as it cannot be computed algorithmically.

algorithms:
  type: LOOKUP_TABLE
  months:
    - {year: 1400, month: 1, jdn: 2444240, length: 30}
    - {year: 1400, month: 2, jdn: 2444270, length: 29}
    # ... complete table of month boundaries
```

Note: The built-in `HIJRI` chronology uses the tabular (arithmetic) approximation,
which may differ from observation-based calendars by 1-2 days.

**METONIC_CYCLE** (planned, not yet implemented) - Calendars with 19-year cycles (e.g., Hebrew):

```yaml
kind: chronology
id: HEBREW
metadata:
  name: Hebrew Calendar

algorithms:
  type: METONIC_CYCLE
  cycle_length: 19
  leap_years: [3, 6, 8, 11, 14, 17, 19]
```

### Chronology Table Schema

For lookup-based calendars:

```yaml
kind: chronology_table
id: string                    # Unique identifier

metadata:
  name: string
  description: string
  source: string              # Data source or authority
  valid_from: string          # Earliest valid date
  valid_to: string            # Latest valid date

entries:
  - year: number              # Year in the chronology
    month: number             # Month (1-based, optional for year tables)
    jdn: number               # Julian Day Number of first day
    length: number            # Number of days (for month tables)
```

### Code Generation

Chronologies are compiled from YAML to Java source code at build time:

```bash
./gradlew generateChronologies
```

This reads YAML files from `chronologies/` and generates Java classes in `tools/src/main/java-generated/`. The generated classes are self-contained with all conversion logic and data embedded - no runtime YAML parsing or external dependencies.

**Example generated class structure:**
- Formula calendars: Contains leap year logic and month definitions
- Lookup table calendars: Contains embedded JDN/length arrays for all months

### Adding New Calendars

New calendars are defined in YAML and compiled to self-contained Java classes:

```bash
# Generate Java classes from YAML chronology definitions
./gradlew generateChronologies
```

For observation-based calendars (e.g., Umm al-Qura), the YAML spec would include
embedded lookup table data with JDN values for each month boundary.

### Directory Structure

```
chronologies/
  iso.yaml          # Gregorian calendar
  julian.yaml       # Julian calendar
  persian.yaml      # Solar Hijri (Iranian) calendar
  hijri.yaml        # Tabular Islamic calendar
  umm_al_qura.yaml  # Umm al-Qura lookup table (Saudi Arabia)
```

## Julian Day Number

Julian Day Number (JDN) is a continuous count of days since the beginning of the Julian Period (January 1, 4713 BCE). It serves as a universal pivot for converting between calendar systems.

### Key Epochs

| Calendar | Epoch Date | JDN |
|----------|------------|-----|
| Gregorian | January 1, 1 CE | 1721426 |
| Julian | January 1, 1 CE | 1721424 |
| Hijri | July 16, 622 CE (Julian) | 1948440 |

### Cross-Calendar Conversion

All conversions go through JDN:

```
Source Calendar → JDN → Target Calendar
```

Example: Convert Hijri 1446-06-15 to ISO date:

```java
ChronologyRegistry registry = ChronologyRegistry.getInstance();

// Hijri to JDN
long jdn = registry.getAlgorithm("HIJRI").toJdn(1446, 6, 15);

// JDN to ISO
ChronologyDate isoDate = registry.fromJdn(jdn, "ISO");
LocalDate localDate = isoDate.toIsoDate();
```

Or using the facade:

```java
LocalDate isoDate = ChronologyTranslator.toIsoDate(1446, 6, 15, "HIJRI");
```

### ChronologyDate Record

A chronology-agnostic date representation:

```java
// Create a date
ChronologyDate date = new ChronologyDate("HIJRI", 1446, 6, 15);

// Convert to JDN
long jdn = date.toJdn();

// Convert to ISO LocalDate
LocalDate isoDate = date.toIsoDate();

// Convert to another chronology
ChronologyDate julianDate = date.toChronology("JULIAN");

// Factory methods
ChronologyDate isoDate = ChronologyDate.iso(2025, 6, 15);
ChronologyDate fromIso = ChronologyDate.fromIsoDate(LocalDate.now(), "HIJRI");
```

## JSON API v1

`tools site --api-only --blessed-dir blessed --release-history-dir release-history --out site/
[--include-base]` reads `blessed/` and `release-history/` and writes a static `/v1/` tree of
minified JSON and RFC 5545 `.ics` files, suitable for serving as-is (e.g. from GitHub Pages).
`--include-base` also emits calendars whose `kind` is not `market` (see below); by default only
`market`-kind calendars are published.

### URL layout

```
v1/index.json                                       one entry per published calendar, plus release info
v1/calendars/<ID>/manifest.json                      metadata.json content + weekend_policy + years + links
v1/calendars/<ID>/<year>.json                        all rows for that year, one file per year in the calendar's full coverage range
v1/calendars/<ID>/holidays.json                      non-WEEKEND rows, full coverage range
v1/calendars/<ID>/all.json                           all rows, full coverage range
v1/calendars/<ID>/holidays.ics                       one VEVENT per CLOSED/EARLY_CLOSE row, all years
v1/calendars/<ID>/holidays-recent.ics                same, 2020 onward only
v1/releases/<semver>/calendars/<ID>/<year>.json      pinned copy of <year>.json as published in that release, 2020 onward only (see below)
```

`index.json` lists each calendar's `id`, `name`, `timezone`, `coverage` (`from`/`to`/
`verified_through`), `counts_by_type`, `counts_by_status`, `checksum` (from `blessed/manifest.json`),
`years` (`[first, last]` of the calendar's full coverage range — every one of those years has a
`<year>.json` file) and `href` (its manifest), plus a top-level `release`
(`semantic`/`git_sha`/`generation_date`, from `blessed/manifest.json`'s `release_version`),
`generated_at`, `schema_version` (`"1.0"`) and `api_version` (`"v1"`).

Every per-calendar JSON document (`<year>.json`, `holidays.json`, `all.json`, and the pinned
release files) carries `calendar_id`, `version` (`{semantic, git_sha}` of the release that
produced it), `range` (`{from, to}` covered by that document), `coverage` (the calendar's
published coverage, from `metadata.json` — the same for every document of a calendar, including
pinned historical ones, since older `metadata.json` formats predate the `coverage` field), and
`event_count`. Rows use the same fields as `JsonEventsEmitter`: `date`, `type`, `description`,
`key`, `source_module`, `observed_from`, `close_time`, `status`; `observed_from` and `close_time`
are omitted (rather than written as `null`) when not applicable, to keep the minified files a
reasonable size — a missing key means the same thing as an explicit `null`.

A calendar's `kind` (`metadata.json`'s `kind` field, falling back to `manifest.json`'s per-calendar
entry) controls whether it is published; `kind` is optional and defaults to `market`. Only
`market` calendars are published by default; `--include-base` publishes `base`-kind calendars too.
A calendar that is not published has **no** entry under `v1/calendars/<ID>/` or
`v1/releases/<semver>/calendars/<ID>/` at all: `index.json` is the definitive list of what exists
under `v1/calendars/`.

`v1/calendars/<ID>/manifest.json` is `blessed/<ID>/metadata.json`'s content plus `weekend_policy`
(the `days`/`periods` block from `blessed/<ID>/resolved.yaml`), `years` (every year in the
calendar's coverage range, each with a `<year>.json` file) and `links` (`year_template`,
`holidays`, `all`, `ics`, `ics_recent`).

### Pinned release files cover 2020 onward

`<year>.json`, `holidays.json` and `all.json` under `v1/calendars/<ID>/` cover the calendar's
*full* blessed coverage range (e.g. US-NYSE 1900-2030) — the HTML site generator (a sibling
package) renders one page per year in coverage, so every year needs a file, and the row data for a
single calendar minified is a manageable size (single digits of MB).

Pinning that same full range for *every retained release* is what does not fit: nine retained
versions across the four current market calendars measured out to roughly 56 MB. Pinned copies
under `v1/releases/<semver>/calendars/<ID>/<year>.json` are therefore limited to **2020-01-01
onward** — the same cutoff as `holidays-recent.ics` — for every retained version, including
blessed. A consumer who needs a pinned copy of a year before 2020 should read the historical
`events.csv`/`events.json` directly from the matching `release-history/<CAL>/` snapshot (or
`blessed/<CAL>/` for the current release); those pre-2020 years essentially never change between
releases in practice, since holiday data that far back is already settled.

### Compatibility contract

- Fields are never removed or retyped within `v1`; new optional fields may be added.
- An unknown `type` means "not a business day"; an unknown `status` means `PROJECTED`.
- A date after `coverage.verified_through` is projected regardless of the row's own `status`.
- `/v1/releases/<semver>/` files are immutable but exist only for versions still retained in
  `release-history/`.
- Consumers should poll `index.json` and compare `checksum` to detect changes rather than
  re-fetching every file on a schedule.

## Artifact comparison

`ci-diff` compares the events generated from the specs against the blessed baseline for the same
range. Every occurrence takes part: events are grouped by identity - `(date, key)` when both sides
carry event keys, by date alone for a legacy baseline written before the key column existed - and
within one identity the occurrences are compared as a multiset of the complete published value:
`(type, description, source_module, observed_from, close_time, status)`. Only the reader's synthetic
in-memory provenance label is excluded. A source-module, observation, close-time or status change
is therefore visible even when the date and description stay the same. Missing legacy columns use
the CSV reader's documented defaults; newly supplied enrichment is a publishable change.

Exact matches cancel one occurrence at a time. If exactly one occurrence then remains on each
side, the pair is reported as a modification; otherwise every leftover is reported individually as
a removal or an addition, and similar names or types are never used to guess a pairing. An event
that moves to another date is a removal and an addition. So, for occurrences sharing one identity:

| Blessed | Generated | Report |
|---|---|---|
| A | A, B | add B |
| A, A, A | A | remove A twice |
| A, A | A, B | modify one A to B |
| A, A | B, B | remove A twice, add B twice |

No event can therefore hide another on the same date, and repeated changes keep their counts.
The report is sorted by date, key, old and new type (in event-type enum order) and old and new
description and published enrichment fields, so it never depends on the order the events arrive in.

Removals and modifications are MAJOR. Additions inside the compared blessed range are MAJOR;
additions only outside it (a backfill or an extension) are MINOR. A calendar with no blessed
`events.csv` at all is MINOR: nothing consumes it yet. `ci-diff` exits 0 for NONE, 1 for MINOR,
2 for MAJOR and 3 for an error.

The JSON report carries every change. The Markdown report's summary counts likewise count every
occurrence even when the detail tables are truncated, and descriptions are escaped for table cells
(`|` escaped, `&`/`<`/`>` as entities, newlines as `<br>`).

Blessed and reference CSVs are read by `EventsCsvReader`, which addresses columns by header name
(so column order and extra columns do not matter), accepts quoted fields with embedded commas and
doubled quotes, ignores blank lines, `#` comment lines and a leading UTF-8 byte order mark, and
fails with the file and line number on a malformed row, an invalid ISO date or an unknown event
type rather than skipping it.


### Reference cross-validation

Reference exporters expose `date,type,close_time`; comparisons count each occurrence of this tuple,
exclude effective weekend days on both sides, and honour the declared comparison types and range.
An empty reference is an error, not a clean validation. Missing close time differs from a supplied
time. An exact match cancels once, and each unexplained remaining occurrence is reported separately.

Legacy allowlists use `source,side,date,type,reason`; each entry excuses one occurrence. New precise
allowlists can use `source,side,date,type,close_time,reason`, including an empty close time for a
full closure. A stale occurrence fails validation. Close-time discrepancies must cite evidence and
must not be hidden by a broad date-only exception.

API link values are resolved against the URL of the JSON document containing them. For example,
`v1/index.json` links to `calendars/US-NYSE/manifest.json`, and that manifest links to `2026.json`.
This keeps the same output usable at `/` and `/calendar-project/`. CI executes the shipped browser
algorithm over HTTP using `python3 scripts/verification/browser.py --site build/browser-site`.
The historical `--settlement T+N` spelling computes a business-date offset, not instrument-specific
settlement eligibility, sessions or cutoffs.
