# Special sessions: design note (October 2026)

Status: proposal, not implemented. Written after the research for the National Stock Exchange of
India (`NSE-RESEARCH.md`), the first market whose calendar the current model cannot express.

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
