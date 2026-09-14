# bdc-calendars

Trading and business-day calendars for Python, with **zero runtime dependencies**.

The data is generated from the YAML specifications in
[calendar-project](https://github.com/twallengren/calendar-project) and shipped inside the wheel, so
every query is offline, deterministic and reproducible: the same package version always gives the
same answer. Every holiday in the source repository cites an authoritative source, and the Python
answers are tested for parity against the Java reference implementation.

```bash
pip install bdc-calendars
```

```python
import datetime as dt
import bdc_calendars as bdc

nyse = bdc.get_calendar("XNYS")          # or "US-NYSE"

nyse.is_business_day(dt.date(2025, 12, 25))      # False
nyse.next_business_day(dt.date(2025, 12, 24))    # datetime.date(2025, 12, 26)
nyse.add_business_days(dt.date(2025, 11, 26), 2) # T+2 -> datetime.date(2025, 12, 1)
nyse.close_time(dt.date(2025, 7, 3))             # datetime.time(13, 0)  (early close)
nyse.status(dt.date(2028, 6, 1))                 # 'PROJECTED'
```

## Calendars

| Id | Aliases | Kind | Covered |
|----|---------|------|---------|
| `US-NYSE` | `XNYS`, `NYSE` | market | 1900-01-01 .. 2030-12-31 |
| `SA-TADAWUL` | `XSAU`, `TADAWUL` | market | 2020-01-01 .. 2030-12-31 |
| `US-MARKET-BASE` | — | base | 1900-01-01 .. 2030-12-31 |
| `US-CORP-IN-VISIBILITY` | — | base | 1900-01-01 .. 2030-12-31 |

`bdc_calendars.list_calendars()` and `bdc_calendars.list_aliases()` return the live lists for the
installed version. Ids are matched case-insensitively, and `-` and `_` are interchangeable.

## API

`get_calendar(id)` returns a `BusinessCalendar`:

| Member | Meaning |
|--------|---------|
| `calendar_id`, `name`, `kind`, `timezone` | identity; `timezone` is the IANA zone close times are local to |
| `range` | the covered window, unpacking as `(from, to)` |
| `verified_through` | last date checked against sources, or `None` |
| `is_business_day(date)` | not a weekend under the calendar's (effective-dated) weekend policy and no `CLOSED` event. An early close **is** a business day |
| `next_business_day(date)` / `previous_business_day(date)` | the first business day strictly after / before |
| `add_business_days(date, n)` | the `n`-th business day from `date`; `n = 0` returns `date` unchanged, the starting date is never counted, so T+1 from a Friday is the following Monday |
| `business_days_between(a, b)` | count of business days, **both endpoints included** |
| `is_early_close(date)` / `close_time(date)` | shortened-session test and its local close time (`datetime.time` or `None`) |
| `status(date)` | `"CONFIRMED"`, `"PROJECTED"` or `"UNKNOWN"` — never raises |
| `events_on(date)` / `events_in_range(a, b)` / `event_on(date)` | the underlying events |
| `holidays(year)` | every `CLOSED` event in a calendar year |
| `weekend_policy` | the effective-dated weekend definition |

Dates may be `datetime.date`, `datetime.datetime`, an ISO `str` or a `pandas.Timestamp`.

`spec/SPEC.md` in the source repository is the contract; the spellings used there
(`nth_business_day`, `prev_business_day`, `business_days_in_range`) are available as aliases.

### Events

`Event` is a `NamedTuple` of `date`, `type` (`CLOSED`, `EARLY_CLOSE`, `NOTABLE`, `WEEKEND`),
`description`, `key`, `source_module`, `observed_from` (the unshifted date when an observance rule
moved the closure), `close_time` and `status`.

### Joint calendars

```python
joint = bdc.get_joint_calendar("US-NYSE", "SA-TADAWUL")
joint.add_business_days(dt.date(2026, 2, 25), 2)   # T+2 across both markets
joint.closed_members(dt.date(2026, 2, 27))         # [<SingleCalendar SA-TADAWUL ...>]
```

A date is a business day only when **every** member trades on it — the settlement semantic: a trade
settles only on a day both markets are open. "Intersection of trading days" and "union of closures"
are the same predicate, so there is exactly one constructor for it. The joint `range` is the
intersection of the members' ranges, `verified_through` is the earliest declared, and `status` is
the worst member's.

### Coverage, status and the out-of-range contract

Each calendar is maintained for a declared range. **Outside that range the absence of a closure
means "not known", never "open"**, so every operation except `status` raises `OutsideCoverageError`
(a `ValueError`) rather than implying a trading day — including navigation whose bounded search
would step past the edge:

```python
nyse.status(dt.date(2031, 1, 1))          # 'UNKNOWN'  — the safe way to probe first
nyse.is_business_day(dt.date(2031, 1, 1)) # raises OutsideCoverageError
```

Inside the range, `status(date)` tells you how much the answer can be trusted:

* `"CONFIRMED"` — checked against an authoritative source;
* `"PROJECTED"` — after the calendar's `verified_through`, or derived from a rule rather than a
  published schedule (Islamic-calendar closures, for example, whose exact dates depend on
  observation). Treat these as a best estimate, not a published schedule;
* `"UNKNOWN"` — outside the range.

Regular open and close times are **not** modelled: `close_time` answers only for shortened
sessions, and `None` means either a regular session or a closed day — check `is_business_day` to
tell those apart.

## Migrating from exchange_calendars

The common read-only surface is available under the exchange_calendars spellings:

| exchange_calendars | bdc-calendars |
|--------------------|---------------|
| `xcals.get_calendar("XNYS")` | `bdc.get_calendar("XNYS")` |
| `cal.is_session(ts)` | `cal.is_session(date)` / `cal.is_business_day(date)` |
| `cal.next_session(ts)` | `cal.next_session(date)` / `cal.next_business_day(date)` |
| `cal.previous_session(ts)` | `cal.previous_session(date)` / `cal.previous_business_day(date)` |
| `cal.sessions_in_range(a, b)` | `cal.sessions_in_range(a, b)` |
| `cal.schedule` | `cal.schedule` (requires `pip install bdc-calendars[pandas]`) |

Differences worth knowing before you switch:

* **Plain dates, not timestamps.** Arguments are `datetime.date` (ISO strings and
  `pandas.Timestamp` are accepted); results are `datetime.date`, not tz-aware `pd.Timestamp`.
* **No pandas requirement.** pandas is optional and only powers `schedule`.
* **No open times.** exchange_calendars models full sessions; this package publishes closures and
  early-close times only. `schedule` therefore has `close_time` / `is_early_close` columns, and
  regular sessions carry no time.
* **Bounded coverage is explicit.** exchange_calendars extends a calendar indefinitely into the
  future from its rules; here, dates outside the maintained range raise, and dates past
  `verified_through` are reported as `PROJECTED`. This is the main behavioural difference: code
  that queried far-future dates silently will now need to handle `OutsideCoverageError`, or check
  `status()` first.
* **`business_days_between` includes both endpoints**, unlike a naive `len(sessions_in_range)`
  difference. Use `sessions_in_range` if you want the sessions themselves.

## Versioning

`bdc_calendars.__version__` is the Python package/API version. The independently versioned bundled
**data** release is exposed in full:

```python
bdc.__version__       # '0.12.0'
bdc.data_version      # '12.0.0'
bdc.data_git_sha      # the calendar-project commit the data came from
bdc.data_generation_date
```

A major bump in the data version means an answer inside existing coverage changed; a minor bump is
additive. Pin both versions if you need byte-stable answers.

## MCP server

`bdc-calendars` ships an optional [MCP](https://modelcontextprotocol.io) server so an AI agent can
query calendars directly, over stdio:

```bash
pip install "bdc-calendars[mcp]"
```

**Claude Desktop** — add to `claude_desktop_config.json`:

```json
{
  "mcpServers": {
    "bdc-calendars": {
      "command": "bdc-calendars-mcp"
    }
  }
}
```

**Claude Code**:

```bash
claude mcp add bdc-calendars -- bdc-calendars-mcp
```

Every tool result is JSON carrying `calendar_id` and `data_version` (and, where a specific date is
involved, `status` and `verified_through`); errors (`CalendarNotFoundError`, `OutsideCoverageError`,
a malformed date) come back as `{"error": ..., "message": ...}` instead of raising, so the agent can
recover — e.g. calling `list_calendars` for a valid id, or `status` to probe a date first.

| Tool | Example question |
|------|-------------------|
| `list_calendars()` | "What calendars are available, and which ones cover Saudi Arabia?" |
| `calendar_info(calendar)` | "What weekend policy does US-NYSE use, and has it ever changed?" |
| `is_business_day(calendar, date)` | "Does the US-NYSE trade on 2021-12-31?" |
| `next_business_day(calendar, date)` | "What's the next US-NYSE trading day after 2025-12-25?" |
| `previous_business_day(calendar, date)` | "What was the last US-NYSE trading day before 2026-01-01?" |
| `add_business_days(calendar, date, n)` | "If a US-NYSE trade happens on 2025-11-26, what date is T+2 settlement?" |
| `business_days_between(calendar, start, end)` | "How many US-NYSE trading days were there in March 2026?" |
| `holidays_in_range(calendar, start, end, include_early_closes=True)` | "What holidays and early closes does US-NYSE observe in 2026?" |
| `is_early_close(calendar, date)` | "Does the US-NYSE close early on 2027-11-26, and if so, at what time?" |
| `joint_settlement_date(calendars, trade_date, t_plus)` | "A trade between US-NYSE and SA-TADAWUL happens on 2026-02-25; what date does it settle on at T+2, and which market was closed on the days in between?" |
| `status(calendar, date)` | "How reliable is the SA-TADAWUL data for 2028-01-01?" |

The server (`python/bdc_calendars/mcp/server.py`) is a thin wrapper over the same `BusinessCalendar`
API described above; `mcp` is an optional dependency imported lazily, so the base `bdc-calendars`
package stays dependency-free unless you install the `mcp` extra.

## Data provenance and licence

Calendar data is generated from the YAML specs in
[calendar-project](https://github.com/twallengren/calendar-project), where each holiday cites its
source. The code is Apache-2.0; the calendar data is CC0.

## Development

From the repository root:

```bash
python -m venv .venv && .venv/bin/pip install -e "python/[test,mcp]"
.venv/bin/python -m pytest python/ -q

python python/scripts/sync_data.py            # re-copy data from blessed/
python python/scripts/sync_data.py --check    # verify it is in sync (CI)
python/scripts/generate_parity_fixture.sh     # refresh the Java parity fixtures
```

`sync_data.py` drops weekend rows from `blessed/<ID>/events.csv` (they are ~85% of the artifact)
and records the calendar's `weekend_policy` in `data/<ID>/metadata.json` instead; weekends are
reconstructed at query time. The script refuses to write unless that reconstruction reproduces the
published weekend rows exactly.
