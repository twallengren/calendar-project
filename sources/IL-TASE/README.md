# IL-TASE sources

The calendar is intentionally bounded by evidence scope. TASE's own 2025 schedule establishes a
complete set of scheduled closures and identifies shortened-session date ranges for that calendar
year. The preserved February 2024 board resolution proposed a 14:30 equity close, but expressly
made the amendments subject to ISA approval and a later effective-date announcement. Without that
operative notice, this calendar does not emit 2025 shortened sessions or claim an exact close time.

For 2026, the ISA guide establishes the Monday-Friday week from 5 January and the 13:50 close on
Fridays. Only Q1 Friday sessions are emitted because the available TASE quarterly report
corroborates Q1 only; the calendar does not extend that list across unreviewed holiday collisions.
The TASE weekly review supplies two independently named closures on 21-22 April. It does not prove
the rest of the year's vacation schedule. No authoritative 2027 schedule was preserved in this
change.

Accordingly, the evidence state is:

- `SCHEDULED_CLOSURES`: verified 2025-01-01 through 2025-12-31; exact-date support only for
  2026-04-21 through 2026-04-22; incomplete for the rest of 2026 and all of 2027.
- `EARLY_CLOSES`: incomplete throughout 2025; verified only on the twelve explicitly emitted Q1
  2026 Fridays from 2026-01-09 through 2026-03-27, with every intervening date and the remainder
  of 2026-2027 marked incomplete.
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

Sunday 4 January 2026 is left under the preceding Sunday-Thursday policy at the schedule layer. The
ISA guide makes the new week effective on Monday 5 January and does not identify 4 January as a
closure. The Q1 total of 62 trading days is only an aggregate cross-check. Its scheduled-closure
scope therefore remains incomplete, and boolean business-day queries fail closed for that date.

| id | title | publisher | url / file | retrieved | covers | notes |
|----|-------|-----------|------------|-----------|--------|-------|
| `tase-vacation-schedule-2025` | Vacation Schedule 2025 (Hebrew) | Tel Aviv Stock Exchange | [Original PDF](https://content.tase.co.il/media/iexfczjb/file_0010_vacation_schedule_2025_heb.pdf) · `sources/IL-TASE/tase-vacation-schedule-2025-he.pdf` | 2026-09-14 | 2025 scheduled closures and shortened-session date ranges | The complete one-page TASE table states Sunday-Thursday trading, lists every 2025 vacation date, and identifies 14-17 April and 8-12 October as shortened trading days. |
| `tase-board-trading-hours-2024` | TASE Board Resolutions of 15 February 2024 | Tel Aviv Stock Exchange | [Original PDF](https://mayafiles.tase.co.il/reports/1574001-1575000/E1574791.pdf) · `sources/IL-TASE/tase-board-trading-hours-2024-02-15-he.pdf` | 2026-09-14 | proposed equity trading hours for intermediate Passover and Sukkot sessions | Page 17 proposes a 14:30 end to the TAL closing-price phase, but page 1 says the amendments require ISA approval and that their effective date will be announced separately. This record does not establish an operative 2025 close time. |
| `isa-trading-week-2026` | Modification of Tel Aviv Stock Exchange Trading Days Guide | Israel Securities Authority | [Original PDF](https://www.new.isa.gov.il/images/Fittings/isa/asset_library_pic/al_lobby/al_lobby-65d5b849b3af3/Modification_TradingDays.pdf) · `sources/IL-TASE/isa-modification-trading-days-2026-en.pdf` | 2026-09-14 | new Monday-Friday trading week effective 5 January 2026 and Friday hours | Pages 7 and 30 set the effective date and Monday-Friday week; pages 17-19 and 30 set every Friday equity session to 10:00-13:50. |
| `tase-weekly-review-2026-04-20` | Overview of the Trading Week on TASE - 20 April 2026 | Tel Aviv Stock Exchange Research Unit | [Original PDF](https://content.tase.co.il/media/22uhu2p0/stat_111_wrev_eng_20260420.pdf) · `sources/IL-TASE/tase-weekly-review-2026-04-20-en.pdf` | 2026-09-14 | 21-22 April 2026 | States that there will be no trading Tuesday and Wednesday due to Memorial Day and Independence Day, and that trading resumes Thursday. |
| `tase-q1-2026-report` | TASE Interim Report for the Period Ended 31 March 2026 | Tel Aviv Stock Exchange | [Original PDF](https://content.tase.co.il/media/pnbmjog2/tase-ltd-interim-report-for-the-period-ended-march-31-2026.pdf) · `sources/IL-TASE/tase-q1-2026-report-en.pdf` | 2026-09-14 | Q1 2026 aggregate trading-day count | Page 3 reports 62 actual trading days in Q1 2026. This corroborates an aggregate only and is not treated as evidence for individual closure dates. |
