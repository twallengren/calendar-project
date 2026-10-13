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
- `UNSCHEDULED_EXCEPTIONS`: verified from 2025-01-01 through 2026-10-13 and projected after
  that. No emergency or ad-hoc closure is inferred from silence in an annual schedule: the past is
  established by the exchange's own TA-35 end-of-day session record (`tase-ta35-eod-sessions`:
  434 sessions over the interval, exactly the scheduled set, no scheduled day missing, including
  every scheduled day of the June 2025 Israel-Iran war, when the Finance Minister and the ISA
  chair announced that trading would take place as usual) and by TASE Ltd's published quarterly
  trading-day counts (`tase-quarterly-trading-days-2025-2026`), which equal the scheduled counts
  in every quarter. An end-of-day record cannot show an unscheduled intraday termination; none
  was found in exchange notices or press coverage for the period.

Business-day questions therefore answer throughout 2025-2028 (`CONFIRMED` through 2026-10-13,
`PROJECTED` after); session questions fail closed in 2025, where no operative close time is sourced.

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
| `tase-trading-vacation-schedule` | TASE Trading and Vacation Schedules / מועדי מסחר ומועדי חופשות בבורסה לניירות ערך בת"א (2025, 2026, 2027 and 2028 tables, trading-hour tables) | Tel Aviv Stock Exchange | https://www.tase.co.il/en/content/knowledge_center/trading_vacation_schedule (Hebrew, authoritative: https://www.tase.co.il/he/content/knowledge_center/trading_vacation_schedule) | 2026-10-13 | 2025-01-01 to 2028-12-31 | The exchange's official vacation schedule page, listing every non-trading and non-settlement day for 2025, 2026, 2027 and 2028, the Monday-Thursday / Friday (end of trading 13:50) / Passover-Sukkot intermediate-day (14:30) trading-hour tables, the rule "On a Friday before a Sunday that is a holiday eve or a public holiday, no trading will take place", the 2026 Knesset Election Day row, and "As of January 4th, 2026, the trading on TASE takes place Monday to Friday". The page is a JavaScript application whose tables are not present in the raw HTML or in Internet Archive captures, so the verbatim rendered Hebrew and English tables were preserved in the local text file. Every 2026-2028 row was re-derived from its Hebrew date with the repository's Hebrew profile and matches the printed weekday. The two JSON files are the byte-exact responses of TASE's content API (https://apicontent.tase.co.il/api/cms?route=%2Fcontent%2Fknowledge_center%2Ftrading_vacation_schedule&culture=he-IL and culture=en-US) that the page renders, with the tables as base64 HTML; the text file is the human-readable extract of the rendered tables. |
| `tase-tradingdays-change` | Gateway to the World: Transition to Monday-Friday Trading (Trading Days Shift) | Tel Aviv Stock Exchange | https://www.tase.co.il/en/content/about/tradingdays_change | 2026-10-13 | from 2026-01-05 | Board approval 18 June 2025, ISA approval 30 July 2025, effective 5 January 2026. Friday cash-market schedule ending 13:50 (derivatives 14:00); "Friday will be a regular trading day, except for the modified schedule". Corroborates the ISA guide and the schedule page's Friday table. |
| `tase-ta35-eod-sessions` | TA-35 index end-of-day history: one record per trading session, 10 December 2024 to 13 October 2026 | Tel Aviv Stock Exchange | https://market.tase.co.il/en/market_data/index/142/historical_data/eod (data: POST https://api.tase.co.il/api/index/historyeod, oId 142, pType 5) | 2026-10-13 | 2025-01-01 to 2026-10-13 | The exchange's own session record: every TradeDate returned for the TA-35 index (one per session). Over 2025-01-01 to 2026-10-13 it holds 434 sessions, exactly the 434 the published vacation schedules imply (Sunday-Thursday through 1 January 2026 minus the 2025 table; no session on Sunday 4 January 2026; Monday-Friday minus the 2026 table from 5 January), with no scheduled day missing, no unscheduled extra session and no weekend-day session, in every month including June 2025 (sessions on every scheduled day 15-26 June during the Israel-Iran war; the Finance Minister and ISA chair announced on 14 June that trading would take place as usual, and the exchange closed higher that Sunday per Globes). An end-of-day record cannot show an unscheduled intraday termination; no exchange notice or press report of one was found for the period. |
| `tase-quarterly-trading-days-2025-2026` | TASE Ltd financial reports and results releases: trading days per quarter, Q1 2025 to Q2 2026 | Tel Aviv Stock Exchange Ltd | https://content.tase.co.il/media/hdteunps/tase-ltd-interim-report-for-the-period-ended-june-30-2025.pdf (H1 2025, Seasonality table p. 52); Q3 2025 release 2025-11-11 (https://mondovisione.com/media-and-resources/news/tel-aviv-stock-exchange-reports-the-results-of-the-financial-statements-for-the-20251111/); FY2025 release 2026-03-05; H1 2026 release 2026-08-04 | 2026-10-13 | 2025-01-01 to 2026-06-30 | Independent quarter-level counts published by the listed company: Q1 2025 64, Q2 2025 60 ("In the second quarter of 2025, there were 60 trading days ... In the first half of 2025, there were 124 trading days"), Q3 2025 62 ("In Q3 2025, there were 62 trading days"), Q4 2025 60 and 246 for the year, Q1 2026 62 (tase-q1-2026-report), Q2 2026 57 ("In the second quarter of 2026, there were 57 trading days ... In the first half of 2026 there were 119 trading days"). Each equals the scheduled count and the TA-35 session record for the same period. |
