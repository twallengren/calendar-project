# IL-TASE sources

The calendar is intentionally bounded by evidence scope. TASE's own 2025 schedule establishes a
complete set of scheduled closures and shortened-session dates for that calendar year. The board
resolution supplies the 14:30 equity close time for those shortened sessions.

For 2026, the ISA guide establishes the Monday-Friday week from 5 January and the 13:50 close on
Fridays. Only Q1 Friday sessions are emitted because the available TASE quarterly report
corroborates Q1 only; the calendar does not extend that list across unreviewed holiday collisions.
The TASE weekly review supplies two independently named closures on 21-22 April. It does not prove
the rest of the year's vacation schedule. No authoritative 2027 schedule was preserved in this
change.

Accordingly, the evidence state is:

- `SCHEDULED_CLOSURES`: verified 2025-01-01 through 2025-12-31; exact-date support only for
  2026-04-21 through 2026-04-22; incomplete for the rest of 2026 and all of 2027.
- `EARLY_CLOSES`: verified 2025-01-01 through 2025-12-31 and for the explicitly emitted Q1 2026
  Fridays; incomplete after 2026-03-31.
- `UNSCHEDULED_EXCEPTIONS`: incomplete throughout 2025-2027. No emergency or ad-hoc closure is
  inferred from silence in an annual schedule.

This makes the scheduled state usable for the verified intervals above, including the complete
2025 scheduled calendar. It does not establish complete historical business-day truth: assessment
or boolean query APIs that require unscheduled-exception coverage must fail closed throughout this
initial range.

The 2025 Memorial and Independence Day rules show their actual observed Hebrew identities. They
are bounded native-relative rules, not a reusable statutory observation policy. The 2026 versions
are exact Hebrew dates independently confirmed as no-trading days by TASE. All other Jewish dates
are bounded to the native year named by the official schedule; there are no uncited future
projections.

Sunday 4 January 2026 is left under the preceding Sunday-Thursday policy. The ISA guide makes the
new week effective on Monday 5 January and does not identify 4 January as a closure. The Q1 total of
62 trading days is only an aggregate cross-check, so a direct session notice for 4 January remains
desirable evidence.

| id | title | publisher | url / file | retrieved | covers | notes |
|----|-------|-----------|------------|-----------|--------|-------|
| `tase-vacation-schedule-2025` | Vacation Schedule 2025 (Hebrew) | Tel Aviv Stock Exchange | sources/IL-TASE/tase-vacation-schedule-2025-he.pdf | 2026-09-14 | 2025 scheduled closures and shortened-session date ranges | The complete one-page TASE table states Sunday-Thursday trading, lists every 2025 vacation date, and identifies 14-17 April and 8-12 October as shortened trading days. |
| `tase-board-trading-hours-2024` | TASE Board Resolutions of 15 February 2024 | Tel Aviv Stock Exchange | sources/IL-TASE/tase-board-trading-hours-2024-02-15-he.pdf | 2026-09-14 | equity trading hours, including intermediate Passover and Sukkot sessions | Page 17 gives 14:30 as the end of the TAL closing-price phase on Passover and Sukkot intermediate trading days. |
| `isa-trading-week-2026` | Modification of Tel Aviv Stock Exchange Trading Days Guide | Israel Securities Authority | sources/IL-TASE/isa-modification-trading-days-2026-en.pdf | 2026-09-14 | new Monday-Friday trading week effective 5 January 2026 and Friday hours | Pages 7 and 30 set the effective date and Monday-Friday week; pages 17-19 and 30 set every Friday equity session to 10:00-13:50. |
| `tase-weekly-review-2026-04-20` | Overview of the Trading Week on TASE - 20 April 2026 | Tel Aviv Stock Exchange Research Unit | sources/IL-TASE/tase-weekly-review-2026-04-20-en.pdf | 2026-09-14 | 21-22 April 2026 | States that there will be no trading Tuesday and Wednesday due to Memorial Day and Independence Day, and that trading resumes Thursday. |
| `tase-q1-2026-report` | TASE Interim Report for the Period Ended 31 March 2026 | Tel Aviv Stock Exchange | sources/IL-TASE/tase-q1-2026-report-en.pdf | 2026-09-14 | Q1 2026 aggregate trading-day count | Page 3 reports 62 actual trading days in Q1 2026. This corroborates an aggregate only and is not treated as evidence for individual closure dates. |
