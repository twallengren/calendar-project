# HK-HKEX sources

| id | title | publisher | url / file | retrieved | covers | notes |
|----|-------|-----------|------------|-----------|--------|-------|
| `hkex-calendar` | HKEX Calendar | Hong Kong Exchanges and Clearing Limited | https://www.hkex.com.hk/News/HKEX-Calendar?sc_lang=en | 2026-09-14 | 2025-09 to 2027-09 (live page) | The exchange's own calendar. Each Hong Kong general holiday appears with the description "Hong Kong Market is closed"; each shortened session appears as "Half-Day Trading Day - Afternoon Session is Closed on Christmas's Eve / New Year's Eve / Lunar New Year's Eve". The page embeds its data as a `calendarDataSource` JSON blob covering roughly one year either side of the retrieval date. |
| `hkex-calendar-archived` | HKEX Calendar (archived captures) | Hong Kong Exchanges and Clearing Limited, via the Internet Archive | https://web.archive.org/web/*/https://www.hkex.com.hk/News/HKEX-Calendar?sc_lang=en (captures 2017-07-01, 2017-12-01, 2018-06-01, 2019-01-01, 2019-07-01, 2020-06-01, 2021-01-01, 2022-01-01, 2023-12-01, 2024-10-01, 2025-04-01) | 2026-09-14 | 2017-01 to 2025-12 | Same page, same embedded `calendarDataSource` format, read out of archived captures so that the exchange's own holiday and half-day entries could be checked for every year of coverage rather than only the live window. Every closure it lists for 2018-2027 agrees exactly with the gazetted general holidays list; no closure appears in one and not the other. |
| `hkex-securities-trading-hours` | Trading Hours - Securities Market | Hong Kong Exchanges and Clearing Limited | https://www.hkex.com.hk/Services/Trading-hours-and-Severe-Weather-Arrangements/Trading-Hours/Securities-Market?sc_lang=en | 2026-09-14 | current, page updated 16 Sep 2017 | "Trading is conducted on Monday to Friday (excluding public holidays)". Full day: 09:30-12:00 morning, 12:00-13:00 extended morning, 13:00-16:00 afternoon, closing auction 16:00 to a random close. Half day: morning session only, closing auction "12:00 noon to a random closing between 12:08 p.m. and 12:10 p.m.". Note: "There is no Extended Morning Session and Afternoon Session on the eves of Christmas, New Year and Lunar New Year." This is the source for the recurring half-day rule and for the 12:00 close time. |
| `hkex-derivatives-holiday-schedule` | Trading Calendar and Holiday Schedule (derivatives market) | Hong Kong Exchanges and Clearing Limited | https://www.hkex.com.hk/Services/Trading/Derivatives/Overview/Trading-Calendar-and-Holiday-Schedule?sc_lang=en | 2026-09-14 | 2026 and 2027, page updated 31 Jul 2026 | HKFE's holiday table for 2026 and 2027, listing the same Hong Kong general holidays that close the securities market, plus the notes naming the shortened sessions: "16 February 2026 (Monday) - Eve of Lunar New Year", "24 December 2026 (Thursday) - Eve of Christmas Day", "31 December 2026 (Thursday) - Eve of New Year", "05 February 2027 (Friday) - Eve of Lunar New Year", "24 December 2027 (Friday) - Eve of Christmas Day", "31 December 2027 (Friday) - Eve of New Year". Used to confirm the two 2027 December eves, which fall past the live HKEX Calendar window. |
| `hkex-stock-connect-calendar` | Trading Calendar of Stock Connect (annual PDFs) | Hong Kong Exchanges and Clearing Limited | https://www.hkex.com.hk/-/media/HKEX-Market/Mutual-Market/Stock-Connect/Reference-Materials/Trading-Hour,-Trading-and-Settlement-Calendar/YYYY-Calendar_pdf_e.pdf (2019-2026) | 2026-09-14 | 2019-2026 | Per-year grid marking each Hong Kong trading day "Holiday" or "Half Day". Used as a second HKEX publication corroborating the closures and half days already taken from `hkex-calendar`; it introduced no date of its own. |
| `govhk-general-holidays` | General Holidays (per-year lists) | Hong Kong Special Administrative Region Government | https://www.gov.hk/en/about/abouthk/holiday/ , per-year pages https://www.gov.hk/en/about/abouthk/holiday/2024.htm ... /2027.htm | 2026-09-14 | 2024-2027 | The Government's published list of general holidays for each year, as gazetted under the General Holidays Ordinance (Cap. 149). Each row gives the holiday's statutory name, date and weekday, already carrying any substitution ("the day following ...", "the fourth day of Lunar New Year", "the second weekday after Christmas Day"). Only the current and next few years stay online; 2028 was not yet published at the retrieval date. |
| `govhk-general-holidays-archived` | General Holidays (per-year lists, archived captures) | Hong Kong Special Administrative Region Government, via the Internet Archive | https://web.archive.org/web/YYYY0601/https://www.gov.hk/en/about/abouthk/holiday/YYYY.htm (2018-2023) | 2026-09-14 | 2018-2023 | The same per-year gov.hk pages for years the Government has since taken down, read from mid-year captures (by which point the year's list is final). Table shape and wording are identical to the live pages; the weekday named in each row was checked against the date as a transcription guard. |
| `hko-gregorian-lunar-conversion` | Gregorian-Lunar Calendar Conversion Table | Hong Kong Observatory, Hong Kong Special Administrative Region Government | https://www.hko.gov.hk/en/gts/time/conversion.htm ; annual text originals at https://www.hko.gov.hk/en/gts/time/calendar/text/files/TYYYYe.txt | 2026-09-14 | 1901-2100; all 2016-2029 annual tables and selected boundary/uncertainty tables retained locally | Authority for the published Hong Kong Gregorian/lunar correspondences and Bright & Clear solar-term rows. HKO and the gazetted HK schedule start the 2027 lunar year on 6 February; pinned ICU4J 78.3 starts it on 7 February, so the calendar carries authoritative replacement rows. HKO also warns that new moons close to midnight can differ by one day in 2057, 2089 and 2097. ICU differs from HKO at 2057-09-28 (HKO M09 day 1; ICU M08 day 30 and M09 begins 2057-09-29), while agreeing on the checked 2089 and 2097 rows. Disputed future conversions are not treated as verified exchange schedules. |
| `hkex-severe-weather-notices` | Securities and Derivatives Market Trading Arrangements under Typhoon Signal No. 8 (Market Communications) | Hong Kong Exchanges and Clearing Limited | https://www.hkex.com.hk/News/Market-Communications/2024/240906news?sc_lang=en | 2026-09-14 | 6 September 2024 | Example of a same-day severe-weather announcement: "trading of its securities ... and derivatives markets today (Friday) has been impacted by the issuance of Typhoon Signal No.8 ... If Typhoon Signal No. 8 or above, Black Rainstorm Warning or any announcement of Extreme Conditions, remains issued at 12:00 noon, all trading sessions today will be cancelled." Cited only in `tools/src/test/resources/reference/HK-HKEX/allowlist.csv`, to show that the severe-weather closures a third-party dataset carries are announced on the day rather than scheduled; no date in this calendar comes from it. |
| `hk-1823-ical` | Hong Kong Public Holidays iCal / JSON feed | 1823, Hong Kong Special Administrative Region Government | https://www.1823.gov.hk/common/ical/en.json | 2026-09-14 | 2025-2027 | The Government's machine-readable feed of the same gazetted general holidays, linked from the gov.hk holiday pages. Used as an independent transcription check on 2025-2027; it matched the HTML pages row for row. |
| `hkex-severe-weather-trading` | HKEX to Implement Severe Weather Trading in Securities and Derivatives Markets from 23 September 2024 | Hong Kong Exchanges and Clearing Limited | https://www.hkex.com.hk/News/Market-Communications/2024/240618news?sc_lang=en | 2026-09-14 | from 2024-09-23 | HKEX states that its securities and derivatives markets remain open and operational during severe weather under the arrangements effective 23 September 2024. |

## Modelling decisions recorded against these sources

- **What this calendar models.** The HKEX *securities* market (SEHK, MIC XHKG). HKEX's derivatives
  market keeps a different schedule (holiday trading for some contracts since 9 May 2022, per
  `hkex-derivatives-holiday-schedule`) and is not modelled here.

- **Closures are exactly the weekday general holidays.** `hkex-calendar` and
  `hkex-calendar-archived` mark every Hong Kong general holiday "Hong Kong Market is closed", and
  a date-by-date comparison over 2018-2027 against `govhk-general-holidays` /
  `govhk-general-holidays-archived` found no closure in one source that is absent from the other.
  The calendar therefore models the gazetted list directly. Where the exchange and the gazette
  could have disagreed they did not, so no precedence rule was needed; had they disagreed, the
  gazette would have won.

- **Weekend observance (`FORWARD_ONLY`).** Under the General Holidays Ordinance a general holiday
  falling on a **Sunday** is observed on the following weekday, and one falling on a **Saturday**
  is **not** substituted. Both halves are visible in the gazetted lists inside coverage: Sunday
  substitutions at 2 January 2023, 2 May 2022, 2 July 2018, 2 October 2023, 27 December 2021 and
  27 December 2027; unsubstituted Saturdays at 1 January 2022, 1 May 2021, 1 May 2027, 1 July 2023
  and 1 October 2022. `FORWARD_ONLY` — shift forward only from the last day of the weekend block —
  is precisely this rule, and is used for every rule-driven fixed-date holiday.

- **Christmas on a Sunday is the one case the model cannot express.** When 25 December falls on a
  Sunday the gazette does not substitute Christmas Day onto a weekday; instead it grants both "the
  first weekday after Christmas Day" (Mon 26 Dec) and "the second weekday after Christmas Day"
  (Tue 27 Dec). `FORWARD_ONLY` would put the shifted Christmas Day on Monday 26 December, on top
  of the 26 December holiday, and nothing cascades it to Tuesday. `NEXT_AVAILABLE_WEEKDAY` does
  cascade, but it would also move a **Saturday** 26 December forward to the following Monday,
  which Hong Kong does not do (26 December 2020 and 26 December 2026 are Saturday general holidays
  with no substitute and the following Mondays were ordinary trading days). Neither existing
  policy is exact, so, per the brief, the affected year is listed explicitly: 2022 is excluded
  from the Christmas Day rule via `active_years` and 27 December 2022 is an `explicit_dates`
  entry named exactly as the gazette names it. 2022 is the only year in coverage where
  25 December falls on a Sunday. Checked against the gazette for both of the years the brief
  called out: **2022** (gazetted: Mon 26 Dec + Tue 27 Dec) and **2027** (25 Dec is a Saturday,
  gazetted: Mon 27 Dec only) — the model reproduces both.

- **Holiday-on-holiday cascades.** The Ordinance also pushes a general holiday that coincides with
  another general holiday to the next day. Inside coverage this happens only to Easter Monday,
  when a Sunday Ching Ming is substituted onto it: 2021 (Ching Ming Sun 4 Apr -> Mon 5 Apr,
  Easter Monday -> Tue 6 Apr) and 2026 (Ching Ming Sun 5 Apr -> Mon 6 Apr, Easter Monday ->
  Tue 7 Apr). The model has no holiday-on-holiday cascade, so those two years are excluded from
  the Easter Monday rule and listed as explicit gazetted dates instead.

- **Lunar and solar-term holidays remain evidence-bounded.** Lunar New Year, the Birthday of the
  Buddha, Tuen Ng, the day following the Chinese Mid-Autumn Festival and Chung Yeung use exact
  `CHINESE_HK` native rules only for 2018-2027. Ching Ming uses the bounded HKO Bright & Clear
  table for that same exchange-evidenced interval. Every resulting date remains `CONFIRMED`
  because it was checked against the gazetted list and `hkex-calendar` /
  `hkex-calendar-archived` (and, for 2019-2026, `hkex-stock-connect-calendar`). The HKO/ICU
  disagreement at the 2027 new moon is replaced by cited calendar deltas, so authoritative
  closure dates carry no false ICU native origin. No formula result outside 2027 is published as
  an exchange holiday.

- **Coverage ends 2027-12-31** (`coverage.to` and `verified_through`). 2027 is the last year the
  Hong Kong Government has gazetted; `https://www.gov.hk/en/about/abouthk/holiday/2028.htm`
  returned 404 at the retrieval date. Because the lunar and solar-term holidays cannot be
  projected, the calendar stops there rather than emitting rule-only years that would be missing
  six of its fourteen holidays.

- **Coverage starts 2018-01-01**, not 2010-01-01 as the onboarding brief suggested. The gazetted
  general holidays are available back to 2010 through archived gov.hk pages, but HKEX's own
  published calendar — the only source consulted that states the **half-day** sessions — reaches
  back only to January 2017 in the Internet Archive, and even the 2017 captures do not carry the
  27 January 2017 Lunar New Year's Eve half day. Before 2018 the half-day dates would have had to
  be inferred from the generic rule alone, and the half-day close time was different for part of
  that period (the securities morning session ended at 12:30 p.m. until the 7 March 2011 trading
  hours change, so a 12:00 close would be wrong for 2010 and the start of 2011). Rather than guess
  those sessions, coverage starts at the first year every emitted event — closures and half days
  alike — is confirmed by an HKEX publication. Extending backwards is a follow-up: it needs either
  HKEX's pre-2017 trading calendars or its trading-hours circulars, neither of which was reachable
  while the Internet Archive was offline during this work.

- **Half-day sessions.** Christmas Eve and New Year's Eve are modelled as a recurring
  `fixed_month_day` rule restricted to Monday-Friday: `hkex-securities-trading-hours` states the
  rule, and every occurrence inside coverage is individually confirmed by `hkex-calendar` /
  `hkex-calendar-archived` (2018-2026) or `hkex-derivatives-holiday-schedule` (2027). When
  24 or 31 December falls on a weekend there is simply no session and nothing is moved — HKEX does
  not shift the half day back to the preceding business day the way the London Stock Exchange
  does, and no source shows it doing so (2022 and 2023, where both dates fall on a weekend, have
  no half-day entry at all in `hkex-calendar`). Lunar New Year's Eve is represented by exact
  `CHINESE_HK` dates from HKEX's own calendar entries, using the actual final day (29 or 30) of
  month twelve; 2023 has none because Lunar New Year's Eve 2023 fell on Saturday 21 January. The
  close time is 12:00, the start of the half-day closing auction per
  `hkex-securities-trading-hours`.

- **Saturday general holidays are not emitted.** Several general holidays fall on Saturdays inside
  coverage (for example Lunar New Year's Day 6 February 2027, Tuen Ng 31 May 2025, the day
  following Mid-Autumn 26 September 2026), and "the day following Good Friday" is *always* a
  Saturday. The market does not trade on Saturdays under any circumstance and none of these days
  carries a substitute, so they add no information and are left out. This is consistent with what
  `FORWARD_ONLY` does to the rule-driven holidays, which also drops their Saturday occurrences.

- **Severe-weather completeness is explicit.** Typhoon and black-rainstorm closures were announced
  on the day and do not appear in published schedules. The actual-state completeness scope is
  therefore `INCOMPLETE` through 22 September 2024; callers can still inspect the scheduled state.
  From 23 September 2024 HKEX's severe-weather-trading policy keeps markets operating in those
  conditions, and that future interval is labelled `PROJECTED` rather than verified.
