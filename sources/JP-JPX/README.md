# JP-JPX sources

Japan Exchange Group (Tokyo Stock Exchange cash equity market, MIC `XTKS`).

**Trading-day reconciliation and unscheduled exceptions.** JPX's Monthly Statistics Reports
(`jpx-monthly-report-2014-12`, `-2016-01`, `-2019-08`, `-2026-08`) and the annual trading-overview
reference sheets give the exchange's own trading-day count for every year from 2010 (245, 245,
248, 245, 244, 244, 245, 247, 245, 241, 243, 245, 244, 246, 245, 243 for 2010-2025) and every month
to August 2026; all equal the modelled schedule. The exchange counts the 1 October 2020 all-day
suspension as a trading day (its 2020 count of 243 includes it), so the statistics verify the
scheduled closures but cannot by themselves exclude another all-day suspension; that rests on the
exchange's news releases (`jpx-system-failure-2020` is the only such release in coverage; trading
continued after the March 2011 earthquake per `tse-statement-2011-03-14`, and the 9 October 2018
connectivity failure let "trading for all issues according to the normal trading schedule" per
`tse-arrowhead-2018-10-09`) and on contemporaneous press. `UNSCHEDULED_EXCEPTIONS` is
`VERIFIED` through August 2026 and `PROJECTED` after.

| id | title | publisher | url / file | retrieved | covers | notes |
|----|-------|-----------|------------|-----------|--------|-------|
| `jpx-market-holidays` | Market Holidays | Japan Exchange Group | https://www.jpx.co.jp/english/corporate/about-jpx/calendar/ | 2026-09-14 | 2026–2027 | The exchange's own publication (page "Update : Feb. 06, 2026"). States the standing rule: "JPX markets are closed on Saturdays, Sundays, national holidays, and on the dates indicated below", where the dates indicated are Jan. 2, Jan. 3 and Dec. 31 every year. Also states the observance rule ("National holidays that fall on a Sunday are observed on the closest following day that is not a national holiday") and flags 2026-09-22 as a holiday "in accordance with Rule 3, Paragraph 3 of Act on National Holidays". |
| `jpx-market-holidays-archive` | Market Holidays (archived editions) | Japan Exchange Group, via the Internet Archive | https://web.archive.org/web/20190722020359/https://www.jpx.co.jp/english/corporate/about-jpx/calendar/index.html (2019, 2020), https://web.archive.org/web/20210503003901/https://www.jpx.co.jp/english/corporate/about-jpx/calendar/index.html (2021, 2022), https://web.archive.org/web/20240207130608/https://www.jpx.co.jp/english/corporate/about-jpx/calendar/index.html (2023, 2024), https://web.archive.org/web/20260215170040/https://www.jpx.co.jp/english/corporate/about-jpx/calendar/index.html (2025–2027) | 2026-09-14 | 2019–2027 | Earlier editions of the same JPX page. Jan. 2, Jan. 3 and Dec. 31 are listed as "Market Holiday" in every edition, including years in which they fall on a weekend. Source for the Olympic-year moves as JPX published them and for the 2019 imperial-transition holidays. |
| `cao-shukujitsu` | 昭和30年（1955年）から令和9年（2027年）国民の祝日（csv形式） | 内閣府 (Cabinet Office, Government of Japan) | https://www8.cao.go.jp/chosei/shukujitsu/syukujitsu.csv | 2026-09-14 | 1955–2027 | Machine-readable list of every 国民の祝日 and every 休日 (both substitute holidays under Art. 3(2) and citizens' holidays under Art. 3(3)). The authoritative date list for everything statutory, including the Vernal and Autumnal Equinox dates. |
| `cao-shukujitsu-gaiyou` | 「国民の祝日」について | 内閣府 (Cabinet Office, Government of Japan) | https://www8.cao.go.jp/chosei/shukujitsu/gaiyou.html | 2026-09-14 | current law | Text of 国民の祝日に関する法律 (Act No. 178 of 1948): Art. 2 (the sixteen holidays and their rules, including the "Happy Monday" ones), Art. 3(2) (substitute holiday), Art. 3(3) (citizens' holiday), the note that the two equinox days are fixed annually by the National Astronomical Observatory in the February *kanpō* of the preceding year, and the table of the 2020/2021 Olympic moves of Marine Day, Sports Day and Mountain Day. |
| `jpx-system-failure-2020` | The Failure of Equity Trading System on October 1, 2020 | Tokyo Stock Exchange, Inc. / Japan Exchange Group | https://www.jpx.co.jp/english/corporate/news/news-releases/0060/20201019-01.html | 2026-09-14 | 2020-10-01 | "a failure occurred in the arrowhead cash equity trading system … which made it impossible to start trading and meant TSE was unable to provide opportunities for trading for the entire day". The basis for the single unscheduled full-day closure in coverage. |
| `jpx-monthly-report-2014-12` | 統計月報 2014年12月 1-1 内国株式総括表 (Monthly Statistics Report, Key Statistics for Domestic Stocks) | Japan Exchange Group / Tokyo Stock Exchange | https://www.jpx.co.jp/files/tse/market/data/geppo/b7gje60000056a2a-att/01_sokatu1412.pdf | 2026-10-14 | 2010-01-01 to 2014-12-31 | The exchange's own annual trading-day counts (立会日数): 2010 245, 2011 245, 2012 248, 2013 245, 2014 244, each equal to the modelled schedule month by month. 2011 has no lost session: trading continued through the March 2011 earthquake (tse-statement-2011-03-14). |
| `jpx-monthly-report-2016-01` | Monthly Statistics Report January 2016, Key Statistics for Domestic Stocks | Japan Exchange Group / Tokyo Stock Exchange | https://www.jpx.co.jp/english/markets/statistics-equities/monthly/b5b4pj000000wq1g-att/01_sokatu1601.pdf | 2026-10-14 | 2011-01-01 to 2015-12-31 | Annual trading days 2011-2015 (2015: 244), equal to the modelled schedule. |
| `jpx-monthly-report-2019-08` | 統計月報 2019年8月 (Monthly Statistics Report, August 2019) | Japan Exchange Group / Tokyo Stock Exchange | https://www.jpx.co.jp/markets/statistics-equities/monthly/nlsgeu000004alfg-att/1908.pdf | 2026-10-14 | 2014-01-01 to 2018-12-31 | Annual trading days 2014-2018 (2016 245, 2017 247, 2018 245), equal to the modelled schedule. |
| `jpx-monthly-report-2026-08` | Monthly Statistics Report August 2026, Key Statistics for Domestic Stocks | Japan Exchange Group / Tokyo Stock Exchange | https://www.jpx.co.jp/english/markets/statistics-equities/monthly/vk0khi000002bth0-att/01_sokatu2608.pdf | 2026-10-14 | 2021-01-01 to 2026-08-31 | Annual trading days 2021 245, 2022 244, 2023 246, 2024 245, 2025 243 and monthly counts to August 2026 (19, 18, 21, 21, 18, 22, 22, 20 for January-August 2026), each equal to the modelled schedule. Note that JPX counts the 1 October 2020 all-day suspension as a trading day (its 2020 count of 243 includes it), so these statistics verify the scheduled closures; the absence of other unscheduled suspensions rests on the exchange's news releases (jpx-system-failure-2020 is the only one in coverage) and on contemporaneous press. |
| `jpx-trading-overview-2020` | Trading Overview in Year 2020 & December 2020, Reference (TSE) | Japan Exchange Group / Tokyo Stock Exchange | https://www.jpx.co.jp/english/corporate/news/news-releases/0063/b5b4pj000003tuqd-att/reference(TSE)_2020.pdf | 2026-10-14 | 2019-01-01 to 2020-12-31 | "(No. of Trading Days: 243)" for 2020 and the daily averages divide exactly by 243, the scheduled count: the exchange counts the 1 October 2020 suspension as a trading day. The 2013 and 2015-2025 reference sheets give the other years (2019 241; 2024 245; 2025 243). |
| `tse-statement-2011-03-14` | Tokyo Stock Exchange statement regarding the Tohoku district - off the Pacific Ocean earthquake (14 March 2011) | Tokyo Stock Exchange (republished by Mondo Visione) | https://mondovisione.com/media-and-resources/news/tokyo-stock-exchange-statement-regarding-the-tohoku-district-off-the-pacific-oc-2011314/ | 2026-10-14 | 2011-03-14 | "Today TSE will operate regular trading sessions for stocks, futures, options, and other listed products." Trading continued every scheduled day after the 11 March 2011 earthquake. |
| `tse-arrowhead-2018-10-09` | Partial Connection Failure in the Equity Trading System (arrowhead), 9 October 2018, and the report of 23 October 2018 | Tokyo Stock Exchange | https://www.jpx.co.jp/english/news/1030/20181009-01.html ; https://www.jpx.co.jp/english/corporate/news/news-releases/0060/20181024-02.html | 2026-10-14 | 2018-10-09 | "Trading is conducted as usual"; the report states the exchange "allowed trading for all issues according to the normal trading schedule". A partial connectivity failure, not a closure. |

## Modelling decisions recorded against these sources

- **Coverage starts 2010-01-01, not earlier.** Until the end of the 2000s the first and last
  trading sessions of the year (大発会 / 大納会) were half-day sessions ending at 11:00 JST. TSE
  made 大納会 a full session from 2009-12-30 and 大発会 a full session from 2010-01-04, so
  2010-01-01 is the first date from which JPX has had no shortened sessions at all. No JPX
  publication enumerating the shortened sessions of 2000–2009 is available (the exchange's
  Market Holidays page is only archived back to 2019-01-03, and it never listed session times),
  so rather than publish those years with their close times guessed, coverage begins where the
  data is clean. Extending backwards needs a primary source for the 大発会/大納会 dates and the
  11:00 close.
- **No early closes.** From 2010 onward JPX runs a full session on every trading day; the
  Market Holidays page lists only full closures and no shortened sessions. `JP-JPX` therefore
  emits no `EARLY_CLOSE` events.
- **Exchange closures beyond the national holidays.** `jpx-market-holidays` states the standing
  rule that the markets are additionally closed on January 2, January 3 and December 31. Those
  three dates are modelled as their own event sources (`jp_jpx_*_market_holiday`) restricted to
  Monday–Friday, so that a year-end date falling on a weekend produces the calendar's ordinary
  `WEEKEND` row rather than a second kind of closure row. JPX's own page does list them on
  weekend days (e.g. "Jan. 2 (Sat.) Market Holiday" for 2027); the set of non-trading days is
  identical either way.
- **New Year's Day is not shifted.** Art. 3(2) does move a Sunday January 1 to January 2 — JPX
  publishes "Jan. 2 (Mon.) New Year's Day (Jan. 1) observed" for 2023 — but January 2 is already
  a market holiday, so the substitute never changes which days the exchange is open. Giving
  `jp_new_years_day` `shift_policy: NONE` keeps it that way; with the calendar's cascading policy
  the substitute would instead skip past the January 2 and January 3 market holidays and wrongly
  close January 4, the year's first trading day.
- **Substitute holidays (振替休日).** Art. 3(2): a 国民の祝日 that falls on a Sunday is observed on
  the nearest following day that is not a national holiday. This is the calendar's
  `weekend_shift_policy: NEXT_AVAILABLE_FROM_LAST_WEEKEND_DAY` — shift only from the last day of
  the weekend block, then cascade past closures. A Saturday holiday is not observed at all in
  Japan, which is why neither `NEAREST_WEEKDAY` nor `NEXT_AVAILABLE_WEEKDAY` (both of which move
  Saturday holidays) fits, and why `FORWARD_ONLY` (which does not cascade past May 4 and May 5 to
  give the May 6 observance of a Sunday May 3) does not either.
- **Citizens' holidays (国民の休日).** Art. 3(3) makes a non-holiday weekday sandwiched between two
  national holidays a holiday. It depends on two independently moving holidays lining up and is
  not expressible as a rule in this model, so the affected dates are taken from `cao-shukujitsu`
  as `explicit_dates` in `jp_citizens_holidays`. Within coverage they are 2015-09-22, 2019-04-30,
  2019-05-02 and 2026-09-22. There are none in 2028–2030: Respect for the Aged Day would have to
  land on September 21 with the Autumnal Equinox on September 23, which next happens in 2032 —
  outside coverage, and past the Cabinet Office list in any case.
- **Equinox days.** `EQUINOX_VERNAL_JP` and `EQUINOX_AUTUMNAL_JP` reproduce every Vernal and
  Autumnal Equinox Day in `cao-shukujitsu` from 2000 through 2027 (asserted in
  `EquinoxCalculatorTest`). The statutory date is whatever the National Astronomical Observatory
  publishes each February for the following year, so the 2028–2030 occurrences are marked
  `status: PROJECTED` through an `active_years` split.
- **Emperor's Birthday.** December 23 while Emperor Akihito reigned (in coverage: 2010–2018) and
  February 23 from 2020 under Emperor Naruhito. 2019 has no Emperor's Birthday at all, which
  `cao-shukujitsu` confirms.
- **2019 imperial transition.** 2019-05-01 (Accession Day) and 2019-10-22 (Enthronement Ceremony
  Day) were one-off holidays; 2019-04-30 and 2019-05-02 then fell to Art. 3(3) as citizens'
  holidays. `cao-shukujitsu` labels the first pair 休日（祝日扱い） and the second pair 休日.
- **2020 and 2021 Olympic moves.** Marine Day, Sports Day and Mountain Day were moved by special
  measures law for those two years only (`cao-shukujitsu-gaiyou`), so each of the three modules
  carries the ordinary rule with 2020 and 2021 excluded plus an `explicit_dates` source for the
  moved dates. Mountain Day 2021 fell on Sunday August 8 and was observed on August 9, which the
  ordinary substitute policy produces.
- **Sports Day's name.** 体育の日 ("Health and Sports Day" in JPX's English listings) through 2019,
  スポーツの日 ("Sports Day") from 2020. Modelled as two event sources so the emitted description is
  right for the year.
- **2020-10-01.** The arrowhead system failure closed the TSE cash equity market for the whole
  day (`jpx-system-failure-2020`). It is the only unscheduled full-day closure in coverage and is
  modelled as an explicit date, like the NYSE's weather and mourning closures.
- **Quality audit.** JPX's archived yearly pages and the Cabinet Office date list verify the
  scheduled-closure scope for 2019–2027. The 2010–2018 interval lacks a complete archived JPX
  market calendar, and 2028–2030 extend past the official lists, so both are labelled projected.
  Early closes and unscheduled-exception completeness remain projected rather than claiming a
  complete historical publication; the confirmed 2020 outage remains an explicit event.
- **Typo in an archived JPX edition.** The 2019-01-01 edition of the Market Holidays page lists
  "May 5 (Tue.) Accession Day" for 2020; May 5 is Children's Day (こどもの日) and Accession Day was
  a 2019-only holiday. `cao-shukujitsu` is followed.
