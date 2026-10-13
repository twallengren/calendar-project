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
| `hkex-severe-weather-notices` | Securities and Derivatives Market Trading Arrangements under Typhoon Signal No. 8 (Market Communications) | Hong Kong Exchanges and Clearing Limited | https://www.hkex.com.hk/News/Market-Communications/2024/240906news?sc_lang=en | 2026-09-14 | 6 September 2024 | Example of a same-day severe-weather announcement (the 06 September 2024 morning notice): "trading of its securities ... and derivatives markets today (Friday) has been impacted by the issuance of Typhoon Signal No.8 ... If Typhoon Signal No. 8 or above, Black Rainstorm Warning or any announcement of Extreme Conditions, remains issued at 12:00 noon, all trading sessions today will be cancelled." Shows the mechanism: the session is cancelled on the day, by announcement. The dated closures themselves cite the individual hkex-no-trading-<date> notices; see the severe-weather section below. |
| `hk-1823-ical` | Hong Kong Public Holidays iCal / JSON feed | 1823, Hong Kong Special Administrative Region Government | https://www.1823.gov.hk/common/ical/en.json | 2026-09-14 | 2025-2027 | The Government's machine-readable feed of the same gazetted general holidays, linked from the gov.hk holiday pages. Used as an independent transcription check on 2025-2027; it matched the HTML pages row for row. |
| `hkex-severe-weather-trading` | HKEX to Implement Severe Weather Trading in Securities and Derivatives Markets from 23 September 2024 | Hong Kong Exchanges and Clearing Limited | https://www.hkex.com.hk/News/Market-Communications/2024/240618news?sc_lang=en | 2026-09-14 | from 2024-09-23 | HKEX states that its securities and derivatives markets remain open and operational during severe weather under the arrangements effective 23 September 2024. |
| `hko-warnings-database` | Warnings and Signals Database: tropical cyclone warning signals (tc.dat) and rainstorm warning signals (rstorm.dat) | Hong Kong Observatory | https://www.hko.gov.hk/dps/wxinfo/climat/warndb/tc.dat ; https://www.hko.gov.hk/dps/wxinfo/climat/warndb/rstorm.dat (UI: https://www.hko.gov.hk/en/wxinfo/climat/warndb/warndb1.shtml and warndb3.shtml) | 2026-10-13 | 1946 to date; used 2018-01-01 to 2024-09-22 | The Observatory's complete record of every Tropical Cyclone Warning Signal and Rainstorm Warning Signal with issue and cancellation times (HKT). Every HKEX severe-weather closure before 23 September 2024 required a Signal No. 8 or above, a Black Rainstorm Warning or an Extreme Conditions announcement, so enumerating every such record over the period and applying the exchange's published arrangements (hkex-severe-weather-arrangements-2023) bounds the set of trading days that could have been affected. All T8+ and Black records 2018-2024 are reproduced in the prose below; each one maps either to an HKEX notice or to a weekend, holiday, out-of-session or before-07:00 cancellation that left trading as usual. |
| `hkex-severe-weather-arrangements-2023` | Severe Weather Arrangements - Trading (securities market tables (a)-(d), archived) | Hong Kong Exchanges and Clearing Limited | https://web.archive.org/web/20230605002721/https://www.hkex.com.hk/Services/Trading-hours-and-Severe-Weather-Arrangements/Severe-Weather-Arrangements/Trading?sc_lang=en | 2026-10-13 | rules in force until 2024-09-22 | The pre-SWT rules: a signal/warning "discontinued at or before 7:00 am: Trading will take place as usual"; "discontinued after 9:00 am and at or before 11:00 am: The Pre-opening Session and the Morning Session will be cancelled. The Afternoon Session will take place as usual at 1:00 pm"; "discontinued after 12:00 noon: No trading for the day"; a T8 issued during a session: "Trading will terminate 15 minutes after the issuance of the signal"; a Black Rainstorm issued during a session: "trading will continue until the market close as scheduled". The same wording appears verbatim in every HKEX delay notice 2019-2024. |
| `hkex-swt-consultation-2023` | Consultation Paper on Severe Weather Trading of Hong Kong Securities and Derivatives Markets (November 2023) | Hong Kong Exchanges and Clearing Limited | https://www.hkex.com.hk/News/Market-Consultations/2016-to-Present/November-2023-Severe-Weather-Trading/Consultation-Paper?sc_lang=en | 2026-10-13 | 2018-01-01 to 2023-11-30 | Independent count used as a cross-check: "Since 2018, SW conditions have forced market closures on 11 occasions" and the markets were "impacted by SW a total of four times in 2023, including three full-day market suspensions". The eleven dated disruptions modelled here through 9 October 2023 (2019: 1, 2020: 2, 2021: 2, 2022: 2, 2023: 4 of which 3 whole days) match that count exactly. Paragraph 88 also fixes the business-day meaning: only an entire-day closure means SEHK is not "open for the business of dealing in securities"; a half-day closure remains a business day. |
| `hkex-no-trading-2020-10-13` | No Trading Today in Securities and Derivatives Markets (Tropical Storm Nangka) | Hong Kong Exchanges and Clearing Limited | https://www.hkex.com.hk/News/Market-Communications/2020/2010132news?sc_lang=en | 2026-10-13 | 2020-10-13 | HKEX market communication of 2020-10-13: "today's trading sessions in the securities and derivatives markets, including After-Hours Futures Trading ... have been cancelled due to the issuance of Typhoon Signal No. 8. No securities clearing and settlement services will be provided for the day." Whole-day closure; the date is not a business day. |
| `hkex-no-trading-2021-10-13` | No Trading Today in Securities and Derivatives Markets (Severe Tropical Storm Kompasu) | Hong Kong Exchanges and Clearing Limited | https://www.hkex.com.hk/News/Market-Communications/2021/2110132news?sc_lang=en | 2026-10-13 | 2021-10-13 | HKEX market communication of 2021-10-13: "trading sessions in the securities and derivatives markets today (Wednesday), including After-Hours Trading, have been cancelled due to the issuance of Typhoon Signal No. 8. No securities clearing and settlement services will be provided for the day." Whole-day closure; the date is not a business day. |
| `hkex-no-trading-2023-07-17` | No Trading Today in Securities and Derivatives Markets (Severe Tropical Storm Talim) | Hong Kong Exchanges and Clearing Limited | https://www.hkex.com.hk/News/Market-Communications/2023/2307172news?sc_lang=en | 2026-10-13 | 2023-07-17 | HKEX market communication of 2023-07-17: "all trading sessions today (Monday) in the securities and derivatives markets, including After-Hours Trading, have been cancelled ... No securities clearing and settlement services will be provided for the day." Whole-day closure; the date is not a business day. |
| `hkex-no-trading-2023-09-01` | No Trading Today in Securities and Derivatives Markets (Super Typhoon Saola) | Hong Kong Exchanges and Clearing Limited | https://www.hkex.com.hk/News/Market-Communications/2023/2309012news?sc_lang=en | 2026-10-13 | 2023-09-01 | HKEX market communication of 2023-09-01: "all trading sessions today (Friday) in the securities and derivatives markets, including After-Hours Trading session ... have been cancelled ... No securities clearing and settlement services will be provided for the day." Whole-day closure; the date is not a business day. |
| `hkex-no-trading-2023-09-08` | No Trading Today in Securities and Derivatives Markets (Black Rainstorm Warning and Extreme Conditions) | Hong Kong Exchanges and Clearing Limited | https://www.hkex.com.hk/News/Market-Communications/2023/2309083news?sc_lang=en | 2026-10-13 | 2023-09-08 | HKEX market communication of 2023-09-08: "all trading sessions today (Friday) in the securities and derivatives markets, including After-Hours Trading session [cancelled] due to a Black Rainstorm Warning remaining in force [and] the announcement of Extreme Conditions by the Hong Kong Government ... No securities clearing and settlement services will be provided for the day." Whole-day closure; the date is not a business day. |
| `hkex-no-trading-2024-09-06` | No Trading Today in Securities and Derivatives Markets (Super Typhoon Yagi) | Hong Kong Exchanges and Clearing Limited | https://www.hkex.com.hk/News/Market-Communications/2024/2409062news?sc_lang=en | 2026-10-13 | 2024-09-06 | HKEX market communication of 2024-09-06: "all trading sessions today (Friday) in the securities and derivatives markets, including the After-Hours Trading session [cancelled] due to the issuance of Typhoon Signal No. 8 ... No securities clearing and settlement services will be provided for the day." Whole-day closure; the date is not a business day. |
| `hkex-trading-terminated-2019-07-31` | Trading in Securities and Derivatives Markets Terminated Due to Issuance of Typhoon Signal No. 8 (Tropical Storm Wipha) | Hong Kong Exchanges and Clearing Limited | https://www.hkex.com.hk/News/Market-Communications/2019/190731news?sc_lang=en | 2026-10-13 | 2019-07-31 | HKEX market communication of 31 July 2019: "trading in the securities market, including Stock Connect trading, and derivatives markets will be terminated at 1:55pm". Signal No. 8 was issued at 13:40 (hko-warnings-database); the morning session traded in full, the afternoon session closed at 13:55 with no Closing Auction Session. Modelled as an EARLY_CLOSE at 13:55; the date remains a business day. |
| `hkex-trading-suspended-2022-11-02` | Trading in Securities and Derivatives Markets Suspended Due to Issuance of Typhoon Signal No. 8 (Severe Tropical Storm Nalgae) | Hong Kong Exchanges and Clearing Limited | https://www.hkex.com.hk/News/Market-Communications/2022/221102news?sc_lang=en | 2026-10-13 | 2022-11-02 | HKEX market communication of 2 November 2022: "trading in the securities market, including Stock Connect trading, and derivatives markets will be suspended at 13:55" ... "There will be no After-Hours Trading session today." Signal No. 8 was issued at 13:40 (hko-warnings-database); modelled as an EARLY_CLOSE at 13:55; the date remains a business day. |
| `hkex-trading-resumed-2020-08-19` | Trading in Securities and Derivatives Markets will Resume (Severe Tropical Storm Higos) | Hong Kong Exchanges and Clearing Limited | https://www.hkex.com.hk/News/Market-Communications/2020/2008192news?sc_lang=en | 2026-10-13 | 2020-08-19 | HKEX market communication of 2020-08-19: "trading in the securities and derivatives markets will resume at 1:30pm ... there will be no Pre-opening Session". The Pre-opening and Morning Sessions were cancelled and the afternoon session traded to the scheduled close. A late open is not an event in this model (session structure is out of scope), so the date is an ordinary business day with no row; the notice is cited so the absence of a closure is evidenced rather than assumed. |
| `hkex-trading-resumed-2021-06-28` | Trading in Securities and Derivatives Markets will Resume (Black Rainstorm Warning) | Hong Kong Exchanges and Clearing Limited | https://www.hkex.com.hk/News/Market-Communications/2021/2106283news?sc_lang=en | 2026-10-13 | 2021-06-28 | HKEX market communication of 2021-06-28: "trading in its securities and derivatives markets, including Stock Connect trading, will resume at 1:30 pm today (Monday), following the cancellation of the Black Rainstorm Warning". The Pre-opening and Morning Sessions were cancelled and the afternoon session traded to the scheduled close. A late open is not an event in this model (session structure is out of scope), so the date is an ordinary business day with no row; the notice is cited so the absence of a closure is evidenced rather than assumed. |
| `hkex-trading-resumed-2022-08-25` | Trading in Securities and Derivatives Markets will Resume (Severe Tropical Storm Ma-on) | Hong Kong Exchanges and Clearing Limited | https://www.hkex.com.hk/News/Market-Communications/2022/2208252news?sc_lang=en | 2026-10-13 | 2022-08-25 | HKEX market communication of 2022-08-25: "trading in the securities market will resume at 1:00 pm today (Thursday) ... there will be no Pre-opening Session". The Pre-opening and Morning Sessions were cancelled and the afternoon session traded to the scheduled close. A late open is not an event in this model (session structure is out of scope), so the date is an ordinary business day with no row; the notice is cited so the absence of a closure is evidenced rather than assumed. |
| `hkex-trading-resumed-2023-10-09` | Trading in Securities and Derivatives Markets will Resume (Typhoon Koinu and Black Rainstorm Warning) | Hong Kong Exchanges and Clearing Limited | https://www.hkex.com.hk/News/Market-Communications/2023/2310092news?sc_lang=en | 2026-10-13 | 2023-10-09 | HKEX market communication of 2023-10-09: "[trading] will commence at 2:00 pm today (Monday), following the cancellation of the Typhoon Signal No. 8 ... there will be no pre-opening session". The Pre-opening and Morning Sessions were cancelled and the afternoon session traded to the scheduled close. A late open is not an event in this model (session structure is out of scope), so the date is an ordinary business day with no row; the notice is cited so the absence of a closure is evidenced rather than assumed. |

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

- **Severe-weather disruptions are enumerated, not assumed away.** Before 23 September 2024 a
  Typhoon Signal No. 8 or above, a Black Rainstorm Warning or an Extreme Conditions announcement
  cancelled sessions by same-day notice (`hkex-severe-weather-arrangements-2023`). Such days never
  appear in a published schedule, so the `UNSCHEDULED_EXCEPTIONS` scope was previously
  `INCOMPLETE` for 2018-2024. It is now `VERIFIED` for 2018-01-01 to 2024-09-22 on three legs:
  every Signal No. 8+ and Black Rainstorm record in the Observatory's warnings database
  (`hko-warnings-database`) over the period was enumerated; each record was resolved against the
  exchange's arrangements and its own market communication; and HKEX's independent count of eleven
  closures since 2018 through November 2023 (`hkex-swt-consultation-2023`) matches. The result,
  in `modules/holidays/hk_hkex_severe_weather.yaml`:

  | Date | Event | Securities market | Modelled as |
  |------|-------|-------------------|-------------|
  | 2019-07-31 | Tropical Storm Wipha, T8 13:40 | terminated 13:55, no closing auction | `EARLY_CLOSE` 13:55 |
  | 2020-08-19 | Severe Tropical Storm Higos, T8 to 11:10 | morning cancelled, afternoon from 13:30 | business day (no row) |
  | 2020-10-13 | Tropical Storm Nangka, T8 05:40-19:40 | whole day cancelled | `CLOSED` |
  | 2021-06-28 | Black Rainstorm 08:20-11:05 | morning cancelled, afternoon from 13:30 | business day (no row) |
  | 2021-10-13 | Severe Tropical Storm Kompasu, T8 to 16:40 | whole day cancelled | `CLOSED` |
  | 2022-08-25 | Severe Tropical Storm Ma-on, T8 to 09:20 | morning cancelled, afternoon from 13:00 | business day (no row) |
  | 2022-11-02 | Severe Tropical Storm Nalgae, T8 13:40 | suspended 13:55, no closing auction | `EARLY_CLOSE` 13:55 |
  | 2023-07-17 | Severe Tropical Storm Talim, T8 00:40-16:20 | whole day cancelled | `CLOSED` |
  | 2023-09-01 | Super Typhoon Saola, T8 02:40 then T10 | whole day cancelled | `CLOSED` |
  | 2023-09-08 | Black Rainstorm 23:05 (7th)-15:40, Extreme Conditions | whole day cancelled | `CLOSED` |
  | 2023-10-09 | Typhoon Koinu, T8 to 11:40, Black Rain 04:00-10:30 | morning cancelled, afternoon from 14:00 | business day (no row) |
  | 2024-09-06 | Super Typhoon Yagi, T8 to 12:40 | whole day cancelled | `CLOSED` |

  Every other T8+ or Black Rainstorm record of the period left trading as usual: Mangkhut's T8
  ended 05:20 on Monday 2018-09-17 (before the 07:00 threshold; "Stock and financial markets
  opened as normal", Reuters); Lionrock's Black Rain of 2021-10-08 was issued mid-session (trading
  continues to the scheduled close); Kompasu (2021-10-12), Ma-on (2022-08-24) and Yagi
  (2024-09-05) reached No. 8 after the securities close; Chaba (2022-07-01/02), the 2020-06-06
  and 2020-09-30 Black Rains and Nalgae's lingering T8 (ended 05:20 on 2022-11-03) fell on a
  holiday, a weekend, after hours or before 07:00. A late open after a cancelled morning is not an
  event in this model (session structure is out of scope), so those four days are cited
  (`hkex-trading-resumed-<date>`) but carry no row. From 23 September 2024 HKEX's
  severe-weather-trading policy keeps markets operating in those conditions, and that interval is
  labelled `PROJECTED` rather than verified.
