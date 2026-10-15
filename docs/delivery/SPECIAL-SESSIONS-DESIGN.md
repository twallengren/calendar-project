# Special sessions: design note (October 2026)

Status: implemented in October 2026 (wire schema 2.1); `spec/SPEC.md` ("Special sessions",
*Query API*, *Questions and the scopes they need*) is now the normative description. Written after
the research for the National Stock Exchange of India (`NSE-RESEARCH.md`), the first market whose
calendar the current model could not express. The proposal below is kept as written; the section
"What was built" at the end records where the implementation departs from it and why.

## What the model cannot say today

The event vocabulary is `CLOSED`, `EARLY_CLOSE`, `NOTABLE`, `PERIOD_MARKER` and `WEEKEND`
(`spec/SPEC.md`, "Event Types"). A date is a business day when it carries neither a `CLOSED` nor a
`WEEKEND` row; an `EARLY_CLOSE` shortens an otherwise regular session; a weekend policy period can
make a Saturday a full trading day (the NYSE's pre-1952 Saturdays, the TWSE's make-up Saturdays of
2010-2018). Three things the NSE does fall outside that:

1. **A settled session on a holiday.** Muhurat trading on Diwali (Laxmi Pujan): a one-hour evening
   session every year since at least 2010, on a day the exchange's own list calls a trading holiday
   (and on a Sunday in 2013, 2016, 2019, 2023 and 2026; a Saturday in 2020). "All trades executed in
   this Diwali Muhurat trading session shall result in settlement obligations" (every circular since
   2013); the exchange's Fact Book counts the day as a trading day in most years. The day is not a
   business day in the ordinary sense (no regular session, banks closed), yet it is a trading day
   with a settlement obligation.
2. **A two-part session on a weekend day with settlement.** 2 March and 18 May 2024: 09:15-10:00
   from the primary site, 11:30-12:30 from the disaster-recovery site, closing session 12:40-12:50,
   trades settled. A one-day weekend period plus an `EARLY_CLOSE` at 12:30 would make the day a
   business day with a shortened close, which is a defensible approximation but hides the gap and
   the 5% price bands.
3. **Instrument-limited sessions.** The Sunday 11 November 2012 Dhanteras session (gold ETFs only,
   11:00-15:30, settled separately) and the 2011-2019 Dhanteras extended hours (gold ETFs to 19:00
   or 20:00 after the regular close). The first is a session on a weekend day in which almost no
   security could trade; the second is a late close for a few instruments only.

Full weekend trading days (the budget Saturdays of 28 February 2015, 1 February 2020 and 1 February
2025, Sunday 1 February 2026, and Saturday 20 January 2024 after the planned special session was
withdrawn) need no model change: a one-day weekend period expresses them.

## Proposal

Add one event type, `SPECIAL_SESSION`, that may coexist with `CLOSED` or `WEEKEND` on the same date:

```yaml
- key: nse_muhurat_trading
  name: Muhurat Trading
  default_classification: SPECIAL_SESSION
  open_time: "18:15"
  close_time: "19:15"
  settles: true            # trades create settlement obligations
  scope: ALL               # or a label such as GOLD_ETF for instrument-limited sessions
  rule:
    type: explicit_dates
    dates: [...]
```

Semantics, in the order the Query API needs them:

- `isBusinessDay` is unchanged: a date with a `CLOSED` or `WEEKEND` row is not a business day even
  when a `SPECIAL_SESSION` row is present. Date arithmetic, settlement walks and the finance
  operations therefore ignore special sessions. This is the conservative reading and the one a
  consumer who does not know about special sessions already gets.
- A new question, `isTradingDay`, answers true when the date is a business day or carries a
  `SPECIAL_SESSION` with `settles: true` and `scope: ALL`. The trust model gains a fourth scope,
  `SPECIAL_SESSIONS`, resolved only by this question and by `sessions(date)`.
- `sessions(date)` (Java, Python, TypeScript, CLI, MCP, v2 API) returns the regular session, if
  any, plus the special sessions with their times, settlement flag and scope. `closeTime` keeps
  returning the regular close and is undefined on a day with only a special session.
- The CSV and JSON artifacts carry the new rows with `open_time` and `close_time` columns
  (`open_time` empty for the existing types). This is additive for JSON consumers and a new column
  for CSV consumers, so the wire schema moves to 2.1.0; v1 continues to omit the new type.
- `ci-diff` groups by `(date, key)` as now; a special session is its own identity.
- Validation: a `SPECIAL_SESSION` without `open_time`/`close_time` is an error; one on an ordinary
  business day with `scope: ALL` is an error (that is an extended or shortened session, not a
  special one); `settles` defaults to true.
- Ports: the Python and TypeScript packages read the new column, expose `is_trading_day` /
  `isTradingDay` and `sessions`, and replay the same parity fixtures.
- Site: date pages list special sessions under the day's state; the market page gets a "Special
  sessions" table; ICS feeds emit them as timed events rather than all-day ones.

The two-part 2024 sessions become `SPECIAL_SESSION` rows with the primary-site times and a note,
not a weekend-period business day. The Dhanteras extended hours stay out of scope (session
structure for a few instruments) and are recorded in the source README only.

## Why not now

Both ports, the site, the v1/v2 emitters, the diff engine, the trust model and the spec change
together, and the artifacts of every published calendar gain a column. That belongs in a 14.0.0
candidate prepared from a clean main, after the pending release (`v13.0.0`, PR #23) and the
market additions (PRs #24 and #25) have merged. The NSE calendar itself (holidays, election
closures, outages, budget-weekend trading days) can be built on the current model the moment the
special-session rows can be attached; the research is complete.

## What was built

The event type, `open_time`, `is_trading_day`, `special_sessions` (named for what it returns: the
regular session's hours are not modelled, so a `sessions(date)` that included it would have
nothing to say), validation, CSV/JSON/v1/v2/ICS output, the site, the CLI and the Java, Python and
TypeScript ports. Three departures from the proposal:

1. **No `settles` and no `scope` field.** Every session the NSE research found settles, so a
   `settles: false` default would only ever be overridden by mock-trading sessions, which are not
   trading and are not modelled at all. The one instrument-limited session (the 2012 Dhanteras
   gold-ETF session) joins the Dhanteras extended hours in the source README. Both fields can be
   added later as optional columns without breaking anything; removing them later could not.
2. **No fourth trust scope.** A `SPECIAL_SESSIONS` scope would have made `is_trading_day` UNKNOWN
   on every existing calendar until each declared new intervals backed by evidence nobody has
   gathered. Special sessions are part of the published schedule (the NSE announces Muhurat
   trading in the same circulars as its holidays), so they fall under the scopes that schedule
   evidence already verifies: `is_trading_day` needs the business-day scopes and
   `special_sessions` the session scopes.
3. **v1 carries the rows.** The v1 contract already says an unknown `type` means "not a business
   day", and a special session always shares its date with a `CLOSED` or `WEEKEND` row, so an old
   v1 consumer still gets the right business-day answer. Hiding the rows from v1 would also have
   hidden them from the HTML site, which renders from v1. `open_time` is a new optional field.

`events.csv` gains `open_time` as its last column, so positional readers of the earlier columns
are unaffected. Every artifact was re-blessed for the column with no change to any event (ci-diff
exit 0).
