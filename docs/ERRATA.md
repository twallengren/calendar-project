# Errata and corrections

A public record of dates this dataset got wrong or could not answer, what the correction was,
and the evidence behind it. Entries are added whenever a release changes a previously published
answer; the per-release detail is in the release changelog and `ci-diff` reports. A correction
to a *past* date is listed even when the previous release refused to answer rather than
answering wrongly, because consumers who fell back to another source need to know.

| Found | Calendar | Dates | Previous releases | Correction | Evidence |
|-------|----------|-------|-------------------|------------|----------|
| 2026-10-13 | HK-HKEX | 2020-10-13, 2021-10-13, 2023-07-17, 2023-09-01, 2023-09-08, 2024-09-06 | ≤ 12.0.0: refused to answer (unscheduled-exception coverage incomplete); scheduled state "open" | Whole-day severe-weather closures (Typhoon Signal No. 8+, Black Rainstorm, Extreme Conditions). exchange_calendars and QuantLib omit 2023-09-01 and 2023-09-08. | HKEX market communications for each day; HKO warnings database; HKEX's own count of eleven closures since 2018 (`sources/HK-HKEX/`) |
| 2026-10-13 | HK-HKEX | 2019-07-31, 2022-11-02 | ≤ 12.0.0: refused; scheduled state "open, regular session" | Trading terminated at 13:55 after Typhoon Signal No. 8 was issued at 13:40: `EARLY_CLOSE` 13:55 | HKEX notices of 31 July 2019 and 2 November 2022 |
| 2026-10-13 | IL-TASE | 2026-03-03 … 2027-10-22 (31 closures), 2026-10-27 | ≤ 12.0.0: refused (schedule not sourced beyond 22 April 2026) | Complete 2026, 2027 and 2028 closure tables, including Knesset Election Day 27 October 2026 and the "Friday before a Sunday holiday" closures | TASE vacation schedule page, content-API payloads preserved (`sources/IL-TASE/`) |
| 2026-10-13 | IL-TASE | every Friday from 2026-04-10; Passover and Sukkot intermediate days | ≤ 12.0.0: refused (only Q1 2026 Fridays emitted) | 13:50 Friday sessions and 14:30 intermediate-day sessions through 2028 | TASE trading-hour tables; ISA guide |
| 2026-10-13 | IL-TASE | 2026-01-04 | ≤ 12.0.0: refused; scheduled state "open" | Not a trading day: the Monday–Friday week applies "as of January 4th, 2026"; the exchange's session record has no session that day | TASE schedule page; TA-35 end-of-day session record |
| 2026-10-13 | SA-TADAWUL | 2020-07-29 … 2020-08-04 | ≤ 12.0.0: refused (2020 scheduled-closure coverage incomplete); scheduled state "open" on the five weekdays | Eid al-Adha 1441 closure, 8–14 Dhu al-Hijjah | The exchange's market-news notice of 30 June 2020, preserved from the Internet Archive (`sources/SA-TADAWUL/`) |
| 2026-10-13 | DE-XETRA, CA-TSX, GB-LSE, Euronext, US-NYSE | 7,670 / 3,287 / 12 / 24 / 8 dates | ≤ 12.0.0: `is_business_day` refused because early-close evidence was incomplete | No data change: an early close is still a business day, so business-day questions now resolve only closure scopes (`spec/SPEC.md`, *Questions and the scopes they need*); `close_time` on those dates still refuses | Model correction (13.0.0) |
| 2026-09-14 | US-NYSE | 1975-12-24 | ≤ 11.0.0: regular session | `EARLY_CLOSE` 14:00 | NYSE holiday history PDF (`nyse-history-2008`) |
| 2026-09-12 | US-NYSE | 2012-10-29/30, 2018-12-05, 2025-01-09 and the Dec 31 / Dec 24 anomalies of earlier releases | ≤ 10.1.0: missing closures; phantom Dec 31 closures; double-dated Dec 24 rows | Corrected from the exchange's own history and press releases | See the v11.0.0 changelog |

Dates that remain unanswerable, by design, until evidence is found:

| Calendar | Dates | Why |
|----------|-------|-----|
| CA-TSX | 2001-09-11, 2001-09-12 | The TSX halted on 11 September 2001 and did not trade on the 12th; the exact arrangement (early termination time, whether the 11th counts as a session) is not yet sourced from TMX |
| IL-TASE | 2025 shortened sessions (close times) | TASE's 2025 schedule names the shortened days but the operative close time was never published as an effective notice; business-day answers are unaffected |

To report a wrong date, open an issue with `.github/ISSUE_TEMPLATE/data-correction.yml`.
