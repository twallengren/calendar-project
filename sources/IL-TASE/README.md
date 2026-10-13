# IL-TASE sources

The calendar is bounded by evidence scope. TASE's own 2025 schedule PDF establishes the complete
2025 scheduled closures and identifies that year's shortened-session date ranges. The preserved
February 2024 board resolution proposed a 14:30 equity close, but expressly made the amendments
subject to ISA approval and a later effective-date announcement; without that operative notice this
calendar does not emit 2025 shortened sessions or claim an exact 2025 close time.

For 2026 onward the evidence is TASE's official vacation schedule page
(`tase-trading-vacation-schedule`), which carries the complete 2026, 2027 and 2028 non-trading-day
tables, the Monday-Thursday / Friday / intermediate-day trading-hour tables, the rule that no
trading takes place on a Friday before a Sunday holiday or holiday eve, the 2026 Knesset Election
Day row, and the statement that trading takes place Monday to Friday as of 4 January 2026. The page
is a JavaScript application whose tables are absent from the raw HTML and from Internet Archive
captures, so the rendered Hebrew and English tables are preserved verbatim in
`sources/IL-TASE/tase-trading-vacation-schedule-2026-10-13.txt` (checksummed in the register).
Every 2026-2028 row was re-derived from its Hebrew date with the repository's Hebrew profile and
matches TASE's printed weekday; `TaseCalendarTest` asserts the full three-year closure set and
every shortened session against those tables.

Modelling notes:

- Closures are bounded Hebrew-calendar recurrences over 5786-5789
  (`tase_scheduled_closures_2026_2028`). Memorial/Independence Day are modelled per year because
  the statutory shift differs: unshifted 4/5 Iyar in 5786 and 5787, moved one day later in 5788
  because 5 Iyar is a Monday (TASE: Monday 1 May and Tuesday 2 May 2028).
- "Friday before a Sunday holiday": 7 Tishri 5787 (2026-09-18) and 5788 (2027-10-08) precede a
  Sunday Yom Kippur Eve; 12 Adar 5788 (2028-03-10) precedes Sunday Purim. They are explicit native
  dates citing the rule, not a general formula.
- Every Friday from 9 January 2026 is a 13:50 session (`weekly` rule). A closed Friday (holiday
  eve, Shavuot, the Friday-before-Sunday rule) takes precedence, so no exclusions are needed.
- Intermediate days of Passover (16-19 Nisan) and Sukkot (16-20 Tishri) close at 14:30 on
  Monday-Thursday only: TASE keeps the Friday schedule on an intermediate Friday (3 April 2026,
  23 April 2027, 14 April 2028, 6 October 2028), and 20 Nisan is the Seventh Day of Passover Eve.
- Sunday 4 January 2026 is the first non-trading Sunday: TASE's page says "As of January 4th,
  2026, the trading on TASE takes place Monday to Friday", and its Q1 2026 count of 62 sessions
  reconciles only with that reading (the ISA guide's "effective 5 January" is the first trading
  day of the new week). Friday 2 January 2026 is still a weekend day under the old week.
- Shushan Purim (4 March 2026) trades; 1 January, 25-26 December and Easter Monday are trading
  days on which only foreign ETFs do not trade.

Accordingly, the evidence state is:

- `SCHEDULED_CLOSURES`: verified 2025-01-01 through 2028-12-31.
- `EARLY_CLOSES`: incomplete through 2026-01-03 (no operative 2025 close time); verified from
  2026-01-04 through 2028-12-31.
- `UNSCHEDULED_EXCEPTIONS`: incomplete from 2025-01-01 through 2026-10-13 and projected after
  that. No emergency or ad-hoc closure is inferred from silence in an annual schedule; the past
  stays incomplete until trading-day counts or exchange notices establish it.

Business-day questions therefore answer for dates after 2026-10-13 (with `PROJECTED` confidence)
and fail closed before it; session questions additionally fail closed in 2025.

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
| `tase-trading-vacation-schedule` | TASE Trading and Vacation Schedules / מועדי מסחר ומועדי חופשות בבורסה לניירות ערך בת"א (2025, 2026, 2027 and 2028 tables, trading-hour tables) | Tel Aviv Stock Exchange | https://www.tase.co.il/en/content/knowledge_center/trading_vacation_schedule (Hebrew, authoritative: https://www.tase.co.il/he/content/knowledge_center/trading_vacation_schedule) | 2026-10-13 | 2025-01-01 to 2028-12-31 | The exchange's official vacation schedule page, listing every non-trading and non-settlement day for 2025, 2026, 2027 and 2028, the Monday-Thursday / Friday (end of trading 13:50) / Passover-Sukkot intermediate-day (14:30) trading-hour tables, the rule "On a Friday before a Sunday that is a holiday eve or a public holiday, no trading will take place", the 2026 Knesset Election Day row, and "As of January 4th, 2026, the trading on TASE takes place Monday to Friday". The page is a JavaScript application whose tables are not present in the raw HTML or in Internet Archive captures, so the verbatim rendered Hebrew and English tables were preserved in the local text file. Every 2026-2028 row was re-derived from its Hebrew date with the repository's Hebrew profile and matches the printed weekday. |
| `tase-tradingdays-change` | Gateway to the World: Transition to Monday-Friday Trading (Trading Days Shift) | Tel Aviv Stock Exchange | https://www.tase.co.il/en/content/about/tradingdays_change | 2026-10-13 | from 2026-01-05 | Board approval 18 June 2025, ISA approval 30 July 2025, effective 5 January 2026. Friday cash-market schedule ending 13:50 (derivatives 14:00); "Friday will be a regular trading day, except for the modified schedule". Corroborates the ISA guide and the schedule page's Friday table. |
