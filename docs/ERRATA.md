# Errata and corrections

A public record of dates this dataset got wrong or could not answer, what the correction was,
and the evidence behind it. It is maintained by hand: a release that changes a previously
published answer (a MAJOR `ci-diff`, see `CONTRIBUTING.md`) should add its rows here before the
release PR is merged; the exhaustive per-row detail is in the release changelog and `ci-diff`
reports. A correction to a *past* date is listed even when the previous release refused to answer
rather than answering wrongly, because consumers who fell back to another source need to know.

| Found | Calendar | Dates | Previous releases | Correction | Evidence |
|-------|----------|-------|-------------------|------------|----------|
| 2026-10-13 | HK-HKEX | 2020-10-13, 2021-10-13, 2023-07-17, 2023-09-01, 2023-09-08, 2024-09-06 | ≤ 12.0.0: refused to answer (unscheduled-exception coverage incomplete); scheduled state "open" | Whole-day severe-weather closures (Typhoon Signal No. 8+, Black Rainstorm, Extreme Conditions). exchange_calendars and QuantLib omit 2023-09-01 and 2023-09-08. | HKEX market communications for each day; HKO warnings database; HKEX's own count of eleven closures since 2018 (`sources/HK-HKEX/`) |
| 2026-10-13 | HK-HKEX | 2019-07-31, 2022-11-02 | ≤ 12.0.0: refused; scheduled state "open, regular session" | Trading terminated at 13:55 after Typhoon Signal No. 8 was issued at 13:40: `EARLY_CLOSE` 13:55 | HKEX notices of 31 July 2019 and 2 November 2022 |
| 2026-10-13 | IL-TASE | 2026-03-03 … 2027-10-22: the 31 weekday closures of 2026–2027 other than 21–22 April 2026 (Purim through Simchat Torah Eve each year, plus Knesset Election Day 27 October 2026) | ≤ 12.0.0: refused; scheduled closures were sourced only for 2025 and for 21–22 April 2026, so every other 2026–2027 date was marked incomplete (including 1 January–20 April 2026) and the scheduled state read "open" | Complete 2026, 2027 and 2028 closure tables, including the "Friday before a Sunday holiday" closures | TASE vacation schedule page, content-API payloads preserved (`sources/IL-TASE/`) |
| 2026-10-13 | IL-TASE | every Friday from 2026-04-03 through 2028; Passover and Sukkot intermediate days 2026–2028 | ≤ 12.0.0: refused (early closes were sourced only for the twelve Fridays 9 January–27 March 2026) | 13:50 Friday sessions and 14:30 intermediate-day sessions through 2028 | TASE trading-hour tables; ISA guide |
| 2026-10-13 | IL-TASE | 2026-01-04 | ≤ 12.0.0: refused; scheduled state "open" | Not a trading day: the Monday–Friday week applies "as of January 4th, 2026"; the exchange's session record has no session that day | TASE schedule page; TA-35 end-of-day session record |
| 2026-10-13 | SA-TADAWUL | 2020-07-29 … 2020-08-04 | ≤ 12.0.0: refused (2020 scheduled-closure coverage incomplete); scheduled state "open" on the five weekdays | Eid al-Adha 1441 closure, 8–14 Dhu al-Hijjah | The exchange's market-news notice of 30 June 2020, preserved from the Internet Archive (`sources/SA-TADAWUL/`) |
| 2026-10-13 | DE-XETRA, CA-TSX, GB-LSE, Euronext (4 venues), US-NYSE | 7,670 / 3,289 / 12 / 24 / 8 dates whose early-close evidence is incomplete (the session-UNKNOWN counts in `docs/delivery/COVERAGE.md`) | ≤ 12.0.0: `is_business_day` refused on all of them | No data change: an early close is still a business day, so business-day questions now resolve only closure scopes (`spec/SPEC.md`, *Questions and the scopes they need*) and answer on every one of these dates except CA-TSX 2001-09-11/12 (below); `close_time` on them still refuses | Model correction (13.0.0) |
| 2026-09-14 | US-NYSE | 1975-12-24 | ≤ 11.0.0: `EARLY_CLOSE` with no close time (the artifact had no `close_time` column), named "Christmas Eve (Early Close)" | `EARLY_CLOSE` at 14:00, a one-off close rather than the 1990-onward Christmas Eve pattern | NYSE holiday history PDF (`nyse-history-2008`); shipped in 12.0.0 |
| 2026-09-12 | US-NYSE | 2012-10-29/30, 2018-12-05, 2025-01-09; 31 December in 18 years; 24 December double rows | ≤ 11.0.0: the three unscheduled closures missing; phantom 31 December closures (rule-generated New Year's Eve rows); double-dated 24 December rows | Corrected from the exchange's own history and press releases | See the v12.0.0 changelog (shipped in 12.0.0; 11.0.0 still carried the old rows) |

Dates that remain unanswerable, by design, until evidence is found:

| Calendar | Dates | Why |
|----------|-------|-----|
| CA-TSX | 2001-09-11, 2001-09-12 | What the Toronto Stock Exchange did on these two days (an early termination on the 11th? a full closure on the 12th?) is not yet sourced from TMX, so the scope is marked incomplete and the API refuses rather than answer either way |
| IL-TASE | 2025 shortened sessions (close times) | TASE's 2025 schedule names the shortened days but the operative close time was never published as an effective notice; business-day answers are unaffected |

To report a wrong date, open an issue with `.github/ISSUE_TEMPLATE/data-correction.yml`.
