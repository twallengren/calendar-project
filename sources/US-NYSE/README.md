# US-NYSE sources

| id | title | publisher | url / file | retrieved | covers | notes |
|----|-------|-----------|------------|-----------|--------|-------|
| `nyse-history-2008` | History of New York Stock Exchange Holidays (revised through November 2008) | NYSE | `nyse-holiday-history-2008.pdf` (text extract: `nyse-holiday-history-2008.txt`) | 2022-01-14 | 1885–2007 | The standard-holiday table (years observed, Rule 51 on Saturday holidays) and the "Special Closings, 1885–date" list. Primary source for everything before 2008. |
| `nyse-legacy-model-2022` | Structured rule model derived from `nyse-history-2008` | project maintainer | `legacy-rule-model-2022.json` | 2022-01-14 | 1885–2022 | Machine-readable transcription of the PDF (holiday rules, Saturday closings, observed-Friday lists, weekend history). Used to generate `us_nyse_special_closings_pre1960`. |
| `nyse-hours` | NYSE Holidays & Trading Hours | NYSE | https://www.nyse.com/markets/hours-calendars | 2026-09-12 | current and next two years | Published holiday and early-close schedule for the current year and the next two. It does not list the one-off closings (checked on the live page and on the 2018-12-04 and 2019-01-07 Internet Archive copies); those cite the exchange's own press releases below. |
| `nyse-euronext-sandy-2012-10-28` | NYSE Euronext Statement on Closure of U.S. Markets on Monday Oct. 29 and Pending Confirmation on Tuesday, Oct. 30, 2012 | NYSE Euronext (Business Wire) | https://www.aol.com/news/2012-10-28-nyse-euronext-statement-on-closure-of-us-markets-o.html (mirror of the 28 October 2012 release; the original nyse.com page is no longer online; also https://www.marktpuls.ch/artikel/permalink/5413) | 2026-10-13 | 2012-10-29 | Dated New York, 28 October 2012: the exchange's own statement that its US markets would be closed on Monday 29 October 2012 because of Hurricane Sandy. The 29 October follow-up (mirror https://www.marktpuls.ch/artikel/permalink/5531) extended the closure to Tuesday 30 October, and the 30 October statement (https://mondovisione.com/news/nyse-euronext-statement-on-opening-of-us-markets-on-wednesday-oct-31-2012-20121030/) announced a normal 9:30 a.m. open on 31 October. NYSE Euronext's October 2012 volume release (https://ir.theice.com/press/news-details/2012/NYSE-Euronext-Announces-Trading-Volumes-for-October-2012/default.aspx) counts 21 US trading days in the month, two fewer than scheduled. |
| `sec-fr-2013-19145` | Self-Regulatory Organizations; NYSE MKT LLC; Notice of Filing of Proposed Rule Change ... Rule 49 (78 FR 48513, 8 August 2013) | U.S. Securities and Exchange Commission / Federal Register | https://www.govinfo.gov/content/pkg/FR-2013-08-08/pdf/2013-19145.pdf | 2026-10-13 | 2012-10-29 to 2012-10-30 | Regulatory record of the Sandy closure: "all U.S. equities and options markets would be closed for those two days" (29 and 30 October 2012). Corroborated by the SEC OCIE risk alert of 27 August 2013 (https://www.sec.gov/about/offices/ocie/business-continuity-plans-risk-alert.pdf). |
| `nyse-pr-bush-2018` | New York Stock Exchange to Honor President George H. W. Bush | New York Stock Exchange (Business Wire, hosted by ICE investor relations) | https://ir.theice.com/press/news-details/2018/New-York-Stock-Exchange-to-Honor-President-George-H-W-Bush/default.aspx | 2026-10-13 | 2018-12-05 | Press release of 1 December 2018: NYSE, NYSE American, NYSE National, NYSE Arca and the Chicago Stock Exchange closed on Wednesday 5 December 2018, the national day of mourning. Corroborated by FINRA Regulatory Notice 18-39 (finra-rn-18-39) and the SEC's EDGAR closure notice (https://www.sec.gov/newsroom/whats-new/edgar-be-closed-wednesday-december-5-2018). |
| `finra-rn-18-39` | FINRA Regulatory Notice 18-39: Unexpected Close of Securities Markets | Financial Industry Regulatory Authority | https://www.finra.org/sites/default/files/Regulatory-Notice-18-39.pdf | 2026-10-13 | 2018-12-05 | December 2018 notice on the unexpected close of the securities markets on 5 December 2018 (superseded by Regulatory Notice 24-18 for later events). |
| `nyse-pr-carter-2024` | The New York Stock Exchange Will Close Markets on January 9 to Honor the Passing of Former President Jimmy Carter on National Day of Mourning | New York Stock Exchange (Business Wire, hosted by ICE investor relations) | https://ir.theice.com/press/news-details/2024/The-New-York-Stock-Exchange-Will-Close-Markets-on-January-9-to-Honor-the-Passing-of-Former-President-Jimmy-Carter-on-National-Day-of-Mourning/default.aspx (also https://www.businesswire.com/news/home/20241230579727/en) | 2026-10-13 | 2025-01-09 | Press release of 30 December 2024: all seven NYSE Group markets closed on Thursday 9 January 2025. Corroborated by FINRA's technical notice of 2 January 2025 (https://www.finra.org/filing-reporting/technical-notices/national-day-of-mourning-20250109) and SIFMA's Unscheduled Close Market Matrix. |
| `fred-nasdaqcom` | NASDAQ Composite Index (NASDAQCOM), daily observations | Federal Reserve Bank of St. Louis (FRED), data from Nasdaq OMX Group | https://fred.stlouisfed.org/graph/fredgraph.csv?id=NASDAQCOM | 2026-10-13 | 2008-01-01 to 2026-10-12 (series from 1971-02-05) | Day-level cross-check of US equity trading days: FRED lists every weekday and leaves the value blank on every day with no session. For every year from 2008 through 12 October 2026 the blank weekdays equal the NYSE's scheduled weekday closures plus exactly the four unscheduled closures (29-30 October 2012, 5 December 2018, 9 January 2025) and no trading day is missing, so no other unscheduled full-day closure occurred. One known artifact: the series prints a value on Good Friday 2019-04-19, when the markets were closed (the S&P 500 series is blank that day). An index series cannot show an early close. |
| `fred-sp500` | S&P 500 (SP500), daily observations | Federal Reserve Bank of St. Louis (FRED), data from S&P Dow Jones Indices | https://fred.stlouisfed.org/graph/fredgraph.csv?id=SP500 | 2026-10-13 | 2016-10-13 to 2026-10-12 | Second, independent index series over FRED's ten-year window; its blank weekdays equal the NYSE's weekday closures exactly, including Good Friday 2019 where the Nasdaq series has its artifact. |
| `nyse-trading-days-2026-2028` | Trading days: 2026 / 2027 / 2028 (estimated) | New York Stock Exchange / ICE | https://www.nyse.com/publicdocs/Trading_Days.pdf | 2026-10-13 | 2026-01-01 to 2028-12-31 | The exchange's own monthly and annual scheduled trading-day counts: 251 US cash-equity trading days in each of 2026, 2027 and 2028, matching the scheduled calendar generated here. |

## Modelling decisions recorded against these sources

- **Saturday sessions.** The Exchange traded on Saturdays until 1952. Per the special closings
  list it closed Saturdays from May 31 through September 27, 1952 and never reopened on a
  Saturday, so `nyse_weekends` makes Saturday a weekend day from 1952-05-31. Summer Saturday
  closings in 1933 and 1944–1951 are modelled as weekend periods.
- **Saturday holidays.** Before Rule 51 (adopted July 3, 1959) a holiday falling on a Saturday
  closed the Saturday session; in some years the preceding Friday was also closed, and those
  Fridays are listed as special closings ("... (observed)" in
  `us_nyse_special_closings_pre1960`). From 1959 the Friday is observed except when it ends an
  accounting period, which is why New Year's Day uses `shift_policy: FORWARD_ONLY`.
- **2008 onward is reconciled day by day.** The NYSE's own holiday history ends in 2007. From
  2008 through 12 October 2026 the scheduled closures and the unscheduled ones are checked against
  two independent daily index series hosted by the Federal Reserve Bank of St. Louis
  (`fred-nasdaqcom` from 2008, `fred-sp500` from October 2016), which are blank on every day
  without a session: the blank weekdays equal the modelled weekday closures plus exactly the four
  unscheduled closures of the period (Hurricane Sandy, 29-30 October 2012; the days of mourning for
  George H. W. Bush, 5 December 2018, and Jimmy Carter, 9 January 2025), each cited to the
  exchange's own press release and a regulatory record, and no trading day is missing. The only
  discrepancy is a known artifact: the Nasdaq series prints a value on Good Friday 2019, when the
  S&P series is blank and the markets were closed. Intraday disruptions that ended with a normal
  close (the 8 July 2015 NYSE suspension, the March-May 2020 all-electronic floor closure, the
  March 2020 circuit-breaker halts) are not closures and carry no row. Index series cannot show
  an early close, so `EARLY_CLOSES` stays `PROJECTED` from 2008; both `SCHEDULED_CLOSURES` and
  `UNSCHEDULED_EXCEPTIONS` are `VERIFIED` through 12 October 2026 and `PROJECTED` after.
- **Early closes.** The regular early closes (Christmas Eve from 1990, day after Thanksgiving
  from 1992, Independence Day eve from 1995) and every one-off "Closed at ..." entry in the
  special closings list are modelled with their local close time. "Opened at ..." late opens
  are not modelled.
- **Disputed Christmas Eves.** The history PDF explicitly lists a 14:00 close on 24 December 1975,
  which is modelled as a one-off alongside 1974. The remaining pre-1990 Christmas Eve dates carried
  by exchange_calendars but absent from the PDF are marked `INCOMPLETE` for the `EARLY_CLOSES`
  scope on those exact dates; surrounding dates retain the PDF-backed quality interval.
- **PDF typo.** The list prints "Dec. 12, 1950 (Sat) Saturday before Christmas Eve"; December 12,
  1950 was a Tuesday and the Saturday before Christmas Eve was December 23, 1950, which is the
  date used.
