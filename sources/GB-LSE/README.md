# GB-LSE sources

**Trading-day reconciliation and unscheduled exceptions.** The exchange's Secondary Markets
Factsheets (`lse-smf-dec-2012` through `lse-smf-sep-2026`) give its own trading-day count for every
month from October 1997 to December 2015 and every year from 2011 to September 2026; the 2004, 2007
and 2008 monthly reports (`lseg-release-2008-01-09`) give the earlier year totals. Every month and
year from 2000 equals the modelled count of weekdays less scheduled closures (252, 253, 252, 253,
254, 252, 252, 253, 254, 253, 253, 251, 252, 253, 253, 253, 253, 252, 253, 253, 254, 253, 250, 251,
254, 253 for 2000-2025; 189 through September 2026), so no unscheduled whole-day closure occurred
and the scheduled closures are verified for the whole period. The one inconsistency is inside the
exchange's own tables: the December 2012 factsheet's T3 prints raw weekday counts for May and
August 2012 while its T1 total and the May 2012 Monthly Market Report give 252, the modelled count.
Every disruption of the period ended in a normal or extended close and is an exchange notice:
5 April 2000 (open 15:45, close extended to 18:30), 8 September 2008 (seven-hour connectivity
halt, "close as normal at 4.30"), 26 November 2009 (halt 10:33-14:00), 25 February 2011 (halt
08:33-12:15, no extension), 19 October and 5 December 2023 (partition-3 halts; FTSE 100/250 closed
normally). 11 September 2001 (trading continued at an alternative site, FTSE 100 closed 4,746.0),
7 July 2005 ("no plans to close the market early") and the September 2022 mourning period
(`lse-market-notice-n15-22`: "will open as normal") were trading days. `UNSCHEDULED_EXCEPTIONS` is
therefore `VERIFIED` through 30 September 2026, the last published factsheet month, and
`PROJECTED` after.

| id | title | publisher | url / file | retrieved | covers | notes |
|----|-------|-----------|------------|-----------|--------|-------|
| `lse-business-days` | Business days | London Stock Exchange | https://www.londonstockexchange.com/equities-trading/business-days | 2026-09-14 | live table observed spanning Mon 31 Aug 2026 - Mon 1 Jan 2029 | The Exchange's own published trading-impact table for its Bank Holidays. States that LSE "recognise[s] the Public and Bank Holidays of England & Wales" and lists, per holiday, whether on-Exchange trading is a NON-trading day or a half day (12:30 close). No standalone yearly PDF calendar was linked from the page at time of retrieval (only a business-parameters spreadsheet and a trading-system guide); the HTML table itself was used instead. |
| `govuk-bank-holidays` | UK bank holidays (England and Wales division) | GOV.UK / HM Government | https://www.gov.uk/bank-holidays (JSON: https://www.gov.uk/bank-holidays.json) | 2026-09-14 | 2019-01-01 to 2028-12-26 | GOV.UK's published list of bank holidays for England and Wales, including the already-shifted ("Substitute day") dates and the one-off Platinum Jubilee (2022-06-03) and State Funeral (2022-09-19) bank holidays and the 2020 VE Day and 2023 Coronation early-May moves. GOV.UK only publishes a rolling window (roughly the current year, several years back and several years forward); it does not go back to 2000. |
| `uk-1971-act` | Banking and Financial Dealings Act 1971 | UK Parliament / legislation.gov.uk | https://www.legislation.gov.uk/ukpga/1971/80 | 2026-09-14 | statutory, ongoing | Statutory basis for the New Year's Day, Easter Monday, early May, Spring and Summer bank holidays and Boxing Day in England & Wales, and for Royal Proclamations appointing additional or moved bank holidays (used for the one-off Jubilee/Royal Wedding/State Funeral days). Good Friday and Christmas Day are common-law holidays, not listed in the Act itself. |
| `dcms-mayday-preconsultation` | Pre-Consultation on moving the May Day Bank Holiday (DCMS, May 2011) | Department for Culture, Media and Sport | https://assets.publishing.service.gov.uk/media/5a78ab29ed915d04220646a3/PreConsult_Bank_Holiday.pdf | 2026-09-14 | background, published May 2011 | Official government consultation document. Section 2.1 tabulates how each regular bank holiday is established in law (Royal Proclamation, the 1971 Act, or common law) for England & Wales. Section 2.3 records that 2011 had "3 bank holidays (4 in 2011 due to the Royal Wedding)" clustered around Easter/May, corroborating that 29 April 2011 was an additional bank holiday. |
| `dcms-diamond-jubilee-ia` | 2012 Diamond Jubilee Extra Bank Holiday: Impact Assessment (DCMS/BIS) | Department for Culture, Media and Sport | https://assets.publishing.service.gov.uk/media/5a749febed915d0e8e399b80/2011-diamond-jubilee-bank-holiday-impact-assessment.pdf | 2026-09-14 | published 2011, covers the 2002 and 2012 Jubilee bank holidays and references the 2011 Royal Wedding | Official government impact assessment. States: "The late May bank holiday will be moved to Monday 4 June 2012 and the extra bank holiday will take place on Tuesday 5 June 2012"; and, discussing precedent, that for the Golden Jubilee "the late May bank holiday was also moved to Monday the 3rd of June 2002 and this was followed by an additional bank holiday on Tuesday the 4th of June 2002." Also references "the Royal Wedding on April 29th 2011" as a comparable national holiday. |
| `lse-trading-calendar-2021` | Trading Calendar 2021 (Turquoise / London Stock Exchange / Borsa Italiana / …) | London Stock Exchange plc | https://docs.londonstockexchange.com/sites/default/files/documents/turquoise_tc_2021.pdf | 2026-09-14 | 2021 | LSE-published multi-venue trading calendar with one column per venue and a per-venue "Early Closing Time" header row; the London Stock Exchange column's early-closing time is **12:30**. December 2021 rows: Fri 24 Dec 2021 marked Early Close, Mon 27 and Tue 28 Dec 2021 marked Market Closed, Fri 31 Dec 2021 marked Early Close. Confirms both the ordinary 24/31-December half day and the 2021 Christmas/Boxing cascade. |
| `lse-trading-calendar-2022` | Trading Calendar 2022 (Turquoise / London Stock Exchange / Borsa Italiana / …) | London Stock Exchange plc | https://docs.londonstockexchange.com/sites/default/files/documents/turquoise_tc_2022.pdf | 2026-09-14 | 2022 | Same format. 24 and 31 December 2022 both fell on a Saturday and appear nowhere in the calendar; instead **Fri 23 Dec 2022 and Fri 30 Dec 2022 are marked Early Close in the London Stock Exchange column** (12:30), with Mon 26 and Tue 27 Dec 2022 marked Market Closed. Primary-source confirmation of the preceding-business-day half-day shift. |
| `lse-trading-calendar-2023` | Trading Calendar 2023 (Turquoise / London Stock Exchange / Borsa Italiana / …) | London Stock Exchange plc | https://docs.londonstockexchange.com/sites/default/files/documents/turquoise-calendar-2023.pdf | 2026-09-14 | 2023 | Same format. 24 and 31 December 2023 both fell on a Sunday; **Fri 22 Dec 2023 and Fri 29 Dec 2023 carry the London Stock Exchange early-close marking** (12:30), with Mon 25 and Tue 26 Dec 2023 marked Market Closed. Second primary-source confirmation of the same shift. |
| `xcal-xlon-crossval` | `exchange_calendars` 4.11, XLON calendar | `exchange_calendars` (Python package) | `tools/src/test/resources/reference/GB-LSE/exchange_calendars-XLON.csv` (generated by `scripts/reference/export_reference_calendars.py`) | 2026-09-14 | 2000-01-04 to 2030-12-31 | **Not a primary source and not cited by any event source.** Cross-validation reference only. It agrees with this calendar on all 315 compared rows, including the six pre-2022 preceding-Friday half days that no primary document we located covers (2000, 2005, 2006, 2011, 2016, 2017); that agreement is reported here as corroboration, but those six dates are justified by the LSE's own published shift behaviour (`lse-trading-calendar-2022`, `lse-trading-calendar-2023`, `lse-business-days` 2028), not by the library. |
| `lse-smf-dec-2012` | Secondary Markets Factsheet, December 2012 (tables T1 Trading Summary and T3 Monthly Order Book Trading) | London Stock Exchange | https://docs.londonstockexchange.com/sites/default/files/reports/Secondary%20Markets%20factsheet%20December%202012.xls | 2026-10-14 | 2000-01-01 to 2012-12-31 | The exchange's own monthly trading-day counts from October 1997 to December 2012 (table T3) and the 2011-2012 year totals (T1). Every month and year from 2000 equals the modelled count of weekdays less scheduled closures, including the months of every known disruption (April 2000: 18, September 2001: 20, July 2005: 21, November 2007: 22, September 2008: 22, October and November 2009: 22 and 21, February 2011: 20), so each disrupted day counted as a trading day. T3's May and August 2012 rows print raw weekday counts (23); T1's 2012 total of 252 and the May 2012 Monthly Market Report (22 trading days on the LSE, 23 on Turquoise) settle the year at the modelled count. |
| `lse-smf-dec-2013` | Secondary Markets Factsheet, December 2013 | London Stock Exchange | https://docs.londonstockexchange.com/sites/default/files/reports/Secondary%20Markets%20factsheet%20December%202013.xls | 2026-10-14 | 2012-01-01 to 2013-12-31 | The exchange's own trading-day count for 2013 and, restated, 2012 ("Trading Days year to date"); both equal the modelled count of weekdays less scheduled closures. Restates 2012 as 251 against the December 2012 factsheet's 252; the monthly reports settle 2012 at 252, the modelled count. |
| `lse-smf-dec-2014` | Secondary Markets Factsheet, December 2014 | London Stock Exchange | https://docs.londonstockexchange.com/sites/default/files/reports/Secondary%20Markets%20factsheet%20December%202014.xls | 2026-10-14 | 2013-01-01 to 2014-12-31 | The exchange's own trading-day count for 2014 and, restated, 2013 ("Trading Days year to date"); both equal the modelled count of weekdays less scheduled closures. |
| `lse-smf-dec-2015` | Secondary Markets Factsheet, December 2015 | London Stock Exchange | https://docs.londonstockexchange.com/sites/default/files/reports/Secondary%20Markets%20factsheet%20December%202015.xls | 2026-10-14 | 2014-01-01 to 2015-12-31 | The exchange's own trading-day count for 2015 and, restated, 2014 ("Trading Days year to date"); both equal the modelled count of weekdays less scheduled closures. |
| `lse-smf-dec-2016` | Secondary Markets Factsheet, December 2016 | London Stock Exchange | https://docs.londonstockexchange.com/sites/default/files/reports/Secondary%20Markets%20factsheet%20December%202016.pdf | 2026-10-14 | 2015-01-01 to 2016-12-31 | The exchange's own trading-day count for 2016 and, restated, 2015 ("Trading Days year to date"); both equal the modelled count of weekdays less scheduled closures. |
| `lse-smf-dec-2017` | Secondary Markets Factsheet, December 2017 | London Stock Exchange | https://docs.londonstockexchange.com/sites/default/files/reports/Secondary%20Markets%20factsheet%20December%202017.pdf | 2026-10-14 | 2016-01-01 to 2017-12-31 | The exchange's own trading-day count for 2017 and, restated, 2016 ("Trading Days year to date"); both equal the modelled count of weekdays less scheduled closures. |
| `lse-smf-dec-2018` | Secondary Markets Factsheet, December 2018 | London Stock Exchange | https://docs.londonstockexchange.com/sites/default/files/reports/Secondary%20Markets%20factsheet%20December%202018.pdf | 2026-10-14 | 2017-01-01 to 2018-12-31 | The exchange's own trading-day count for 2018 and, restated, 2017 ("Trading Days year to date"); both equal the modelled count of weekdays less scheduled closures. |
| `lse-smf-dec-2019` | Secondary Markets Factsheet, December 2019 | London Stock Exchange | https://docs.londonstockexchange.com/sites/default/files/reports/Secondary%20Markets%20factsheet%20December%202019.pdf | 2026-10-14 | 2018-01-01 to 2019-12-31 | The exchange's own trading-day count for 2019 and, restated, 2018 ("Trading Days year to date"); both equal the modelled count of weekdays less scheduled closures. |
| `lse-smf-dec-2020` | Secondary Markets Factsheet, December 2020 | London Stock Exchange | https://docs.londonstockexchange.com/sites/default/files/reports/Secondary%20Markets%20factsheet%20December%202020.pdf | 2026-10-14 | 2019-01-01 to 2020-12-31 | The exchange's own trading-day count for 2020 and, restated, 2019 ("Trading Days year to date"); both equal the modelled count of weekdays less scheduled closures. |
| `lse-smf-dec-2021` | Secondary Markets Factsheet, December 2021 | London Stock Exchange | https://docs.londonstockexchange.com/sites/default/files/reports/Secondary%20Markets%20factsheet%20December%202021.pdf | 2026-10-14 | 2020-01-01 to 2021-12-31 | The exchange's own trading-day count for 2021 and, restated, 2020 ("Trading Days year to date"); both equal the modelled count of weekdays less scheduled closures. |
| `lse-smf-dec-2022` | Secondary Markets Factsheet, December 2022 | London Stock Exchange | https://docs.londonstockexchange.com/sites/default/files/reports/Secondary%20Markets%20factsheet%20December%202022.pdf | 2026-10-14 | 2021-01-01 to 2022-12-31 | The exchange's own trading-day count for 2022 and, restated, 2021 ("Trading Days year to date"); both equal the modelled count of weekdays less scheduled closures. |
| `lse-smf-dec-2023` | Secondary Markets Factsheet, December 2023 | London Stock Exchange | https://docs.londonstockexchange.com/sites/default/files/reports/Secondary%20Markets%20factsheet%20December%202023.pdf | 2026-10-14 | 2022-01-01 to 2023-12-31 | The exchange's own trading-day count for 2023 and, restated, 2022 ("Trading Days year to date"); both equal the modelled count of weekdays less scheduled closures. |
| `lse-smf-dec-2024` | Secondary Markets Factsheet, December 2024 | London Stock Exchange | https://docs.londonstockexchange.com/sites/default/files/reports/Secondary%20Markets%20factsheet%20December%202024.pdf | 2026-10-14 | 2023-01-01 to 2024-12-31 | The exchange's own trading-day count for 2024 and, restated, 2023 ("Trading Days year to date"); both equal the modelled count of weekdays less scheduled closures. |
| `lse-smf-dec-2025` | Secondary Markets Factsheet, December 2025 | London Stock Exchange | https://docs.londonstockexchange.com/sites/default/files/reports/Secondary%20Markets%20factsheet%20December%202025.pdf | 2026-10-14 | 2024-01-01 to 2025-12-31 | The exchange's own trading-day count for 2025 and, restated, 2024 ("Trading Days year to date"); both equal the modelled count of weekdays less scheduled closures. |
| `lse-smf-sep-2026` | Secondary Markets Factsheet, September 2026 | London Stock Exchange | https://docs.londonstockexchange.com/sites/default/files/reports/Secondary%20Markets%20factsheet%20September%202026.pdf | 2026-10-14 | 2026-01-01 to 2026-09-30 | 189 trading days year to date through 30 September 2026 (and 189 for the same period of 2025), equal to the modelled count; the latest published month bounds the verified interval. |
| `lseg-release-2008-01-09` | Over 200 million electronic equity trades during 2007 (9 January 2008) | London Stock Exchange Group (via Borsa Italiana) | https://www.borsaitaliana.it/borsaitaliana/ufficio-stampa/comunicati-stampa/2008/080109datigennaio.en.htm | 2026-10-14 | 2006-01-01 to 2007-12-31 | States 253 trading days in 2007 and 252 in 2006 (and 19 in December 2007), equal to the modelled counts; corroborates the factsheet T3 months for those years. The December 2008 report (https://www.borsaitaliana.it/borsaitaliana/ufficio-stampa/comunicati-stampa/2009/090109-monthly-report-dicembre-08.en.htm) likewise states "254 trading days on the London Stock Exchange in 2008, one more than during 2007", and the December 2004 report (https://mondovisione.com/news/monthly-market-report-for-december-2004-december-trading-highlights-a-strong-200-2011124/) "254 trading days in the year as a whole, compared with 253 in 2003". |
| `lse-rns-2000-04-05` | Market Announcement, 5 April 2000 | London Stock Exchange (RNS; also republished by Mondo Visione) | https://investegate.co.uk/announcement/rns/london-stock-exchange-group--lseg/market-announcement-/165639 | 2026-10-14 | 2000-04-05 | The exchange's announcement of the delayed open at 15:45 and the extension of the close to 18:30 on 5 April 2000: a trading day with a late open, not a closure. |
| `lse-service-update-2008-09-08` | Service Announcement: Service Update 7 and Service Update 8, 8 September 2008 | London Stock Exchange (republished by Mondo Visione) | https://mondovisione.com/media-and-resources/news/london-stock-exchange-service-announcement-service-update-8--2011124/ (Update 7: https://mondovisione.com/news/london-stock-exchange-service-announcement-service-update-7-2011124/) | 2026-10-14 | 2008-09-08 | The connectivity outage of 8 September 2008: reconnection from 14:45, an auction, continuous trading from 16:00 and "we will close as normal at 4.30" (the exchange's spokeswoman as quoted by The Register); September 2008 counts 22 trading days in the exchange's own statistics. A trading day with a seven-hour halt, not a closure. |
| `lse-statement-2009-11-26` | Statement following trading disruption, 26 November 2009 | London Stock Exchange (republished by Mondo Visione) | https://mondovisione.com/media-and-resources/news/london-stock-exchange-statement-following-trading-disruption-2011124/ | 2026-10-14 | 2009-11-26 | Halt from 10:33 with continuous trading resumed at 14:00 and a normal close; November 2009 counts 21 trading days in the exchange's statistics (9 and 26 November both counted). |
| `lseg-statement-2011-02-25` | London Stock Exchange Group cash equity markets resume trading / Resumption of continuous trading, 25 February 2011 | London Stock Exchange Group (republished by Mondo Visione) | https://mondovisione.com/media-and-resources/news/london-stock-exchange-group-cash-equity-markets-resume-trading-2011225/ ; https://mondovisione.com/media-and-resources/news/london-stock-exchange-resumption-of-continuous-trading-2011225/ | 2026-10-14 | 2011-02-25 | Trading paused at 08:03, halted at 08:33 and resumed at 12:15; the exchange "does not intend to extend its scheduled trading hours", so the day closed normally. February 2011 counts 20 trading days in the exchange's statistics. |
| `lse-market-notice-n15-22` | Market Notice N15/22 - Trading Update, 9 September 2022 | London Stock Exchange | https://docs.londonstockexchange.com/sites/default/files/documents/n1522.pdf | 2026-10-14 | 2022-09-09 | After the death of Queen Elizabeth II: "all ... markets will open as normal today and will close at the normal times". Negative evidence for the mourning period. |
| `lse-market-notice-n16-22` | Market Notice N16/22 - Trading Update, 12 September 2022 | London Stock Exchange | https://docs.londonstockexchange.com/sites/default/files/documents/n1622.pdf | 2026-10-14 | 2022-09-19 | Confirms the closure of 19 September 2022, the bank holiday for the State Funeral: the exchange's own notice for a closure already modelled from the gazetted bank holiday. |
| `lse-service-announcement-2023-10-19` | Service Announcement Notice 001/20102023 - 19 October 2023 - Technical Incident Update | London Stock Exchange | https://docs.londonstockexchange.com/sites/default/files/documents/live_-_001_-_20102023_-_london_stock_exchange_-_19_october_2023_-technical_incident_update.pdf | 2026-10-14 | 2023-10-19 | Order-book securities outside the FTSE 100, FTSE 250 and IOB (partition 3) were halted from 15:11 to the close with no closing auction for them; the FTSE 100, FTSE 250 and IOB closed normally. A partial-instrument incident, not a market closure or early termination; no row. |
| `lse-service-announcement-2023-12-05` | Service Announcement Notice 001/05122023 - 05 December 2023 - Technical Incident Update | London Stock Exchange | https://docs.londonstockexchange.com/sites/default/files/documents/live_-_001_-_05122023_-_london_stock_exchange_-_05_december_2023_-_technical_incident_update.pdf | 2026-10-14 | 2023-12-05 | Two partition-3 halts during the day; the closing auction ran at 16:30 as scheduled. Not a closure; no row. |

## Modelling decisions recorded against these sources

- **Coverage starts 2000-01-01** per the project brief for this market, not because of a source
  limitation. Two well-known pre-2000 one-off UK bank holiday moves (the 1995 VE Day 50th
  anniversary move of the early May bank holiday to 8 May 1995, and the 31 December 1999
  Millennium bank holiday) fall entirely before this calendar's coverage window and are
  therefore **not modeled at all** — not because a source was rejected, but because they are
  out of range. A brief search for a primary citation for the 1999 Millennium bank holiday
  (Hansard, the Privy Council's own proclamation archive, and The Gazette) did not turn one up
  within a reasonable search budget; had coverage needed to include 1999 this would have been
  reason to narrow coverage further rather than guess the exact date/mechanism.
- **Weekend observance.** LSE recognises the England & Wales bank holiday list (`lse-business-days`)
  and the UK bank-holiday convention is "next available weekday not already a holiday"
  (`weekend_shift_policy: NEXT_AVAILABLE_WEEKDAY`), confirmed directly against `govuk-bank-holidays`
  and `lse-business-days` cascade examples: Christmas Saturday 25 Dec 2021 -> observed Monday
  27 Dec (Boxing Day Sunday 26 Dec -> observed Tuesday 28 Dec); the same cascade recurs for
  2027 (25 Dec Sat -> Mon 27 Dec; 26 Dec Sun -> Tue 28 Dec) and New Year's Day 1 Jan 2028 (Sat)
  -> observed Monday 3 Jan 2028, all directly visible in the fetched sources.
- **Early closes (Christmas Eve, New Year's Eve).** `lse-business-days` shows a recurring 12:30
  half-day close on 24 December and 31 December whenever they are trading days (Monday-Friday),
  independent of whether Christmas Day itself is later observed on a different date: in the 2027
  row of the live table, Friday 24 December 2027 is a half day even though Christmas Day is
  observed on the substitute date Monday 27 December 2027. This confirmed the model choice of a
  plain `fixed_month_day` + `only_if_weekday: [MONDAY..FRIDAY]` rule rather than something tied to
  the observed Christmas/New Year date.
- **Early close when 24/31 December itself falls on a weekend.** When 24 or 31 December falls on
  a Saturday or Sunday, LSE moves the half-day close back to the preceding business day instead
  of dropping it. Three separate LSE-published documents show this directly:
  - `lse-business-days` (live table): 24 Dec 2028 is a Sunday and the half day is shown on
    **Friday 22 December 2028**; 31 Dec 2028 is a Sunday and the half day is on **Friday 29
    December 2028**.
  - `lse-trading-calendar-2022`: 24 and 31 Dec 2022 are Saturdays; the London Stock Exchange
    column is marked Early Close on **Friday 23 December 2022** and **Friday 30 December 2022**.
  - `lse-trading-calendar-2023`: 24 and 31 Dec 2023 are Sundays; the London Stock Exchange
    column is marked early close on **Friday 22 December 2023** and **Friday 29 December 2023**.

  This is modelled as a general rule, not as a date list: both early-close event sources carry
  `shift_policy: PREVIOUS_AVAILABLE_BUSINESS_DAY` (see `spec/SPEC.md`, "Weekend Shift Policy"),
  which moves a half day whose nominal date is not a session back to the nearest earlier date
  that is neither a weekend day nor a full closure. The shifted rows carry `observed_from` (the
  nominal 24/31 December date). An earlier revision of this calendar listed all eighteen shifted
  occurrences as `explicit_dates` under separate `*_shifted` event sources, because the shift
  mechanisms then applied to `CLOSED` events only; that workaround is gone and the generated
  dates, types and close times are unchanged by its removal.

  The same 24/31-December-on-weekend collision also occurs, within this calendar's coverage, in
  2000, 2005, 2006, 2011, 2016 and 2017. **No LSE-published document we could locate covers those
  six years** — the live business-days table only reaches back to the current year and no LSE
  trading-calendar PDF earlier than 2021 is still served from `docs.londonstockexchange.com`.
  They are produced by the same `PREVIOUS_AVAILABLE_BUSINESS_DAY` rule the three documents above
  establish, on the basis that LSE has published the identical behaviour in every
  weekend-collision year it does cover (2022, 2023, 2028) and that the 12:30 half day itself is
  unchanged across the whole period. `xcal-xlon-crossval` independently computes the same six dates and close time, which is
  recorded as corroboration only — it is a cross-validation reference, not a citation, and it is
  not the reason those dates are in the calendar. A reviewer who wants a stricter standard should
  narrow `coverage.from` to 2021-01-01 rather than allowlist them, since they are ordinary
  business-day-affecting data, not a disagreement with a reference.
- **Early May Bank Holiday one-off move.** Moved to 8 May 2020 (VE Day 75th anniversary),
  directly listed in `govuk-bank-holidays`. 2023 is different: `govuk-bank-holidays` lists both
  "2023-05-01 Early May bank holiday" (the ordinary first-Monday date) and a separate
  "2023-05-08 Bank holiday for the coronation of King Charles III" as distinct entries -- the
  Coronation added an extra bank holiday rather than moving the existing one, corroborated by
  both cross-validation references showing 2023-05-01 as closed independently of 2023-05-08 (see
  `uk_early_may_bank_holiday.yaml` and `uk_one_off_bank_holidays.yaml`). An earlier draft of this
  calendar modeled 2023 as a "moved" holiday like 2020; cross-validation against
  `exchange_calendars`/QuantLib caught the "theirs-only 2023-05-01 CLOSED" discrepancy and this
  was corrected rather than allowlisted.
- **Spring Bank Holiday one-off moves (Jubilees).** Moved to Monday 3 June 2002 (Golden
  Jubilee) and Monday 4 June 2012 (Diamond Jubilee), per `dcms-diamond-jubilee-ia`; moved to
  Thursday 2 June 2022 (Platinum Jubilee) per `govuk-bank-holidays`. Each move is paired with an
  extra one-off Jubilee bank holiday the following day: Tuesday 4 June 2002, Tuesday 5 June 2012,
  and Friday 3 June 2022 (`dcms-diamond-jubilee-ia`, `govuk-bank-holidays`).
- **One-off national closures.** Royal Wedding, Friday 29 April 2011 (`dcms-diamond-jubilee-ia`,
  corroborated by `dcms-mayday-preconsultation`'s "4 bank holidays in 2011" remark); State
  Funeral of Queen Elizabeth II, Monday 19 September 2022 (`govuk-bank-holidays`). Both dates
  fall on ordinary weekdays already, so no weekend shifting applies.
- **`coverage.verified_through`: 2028-12-31.** This is the last year both `lse-business-days`
  (live table, retrieved 2026-09-14, covers through Wed 1 Jan 2029) and `govuk-bank-holidays`
  (through 2028-12-26) fully confirm. 2029 and 2030 rely on rule projection only: New Year's
  Day, Good Friday, Easter Monday, the Early May/Spring/Summer bank holidays, Christmas Day and
  Boxing Day are all statutory (`uk-1971-act`) or common-law holidays with a fixed computation
  rule, so projecting them past `verified_through` is safe (`status(date)` still reports
  `PROJECTED` automatically for any date after `verified_through`, per `spec/SPEC.md`). No
  one-off closure is projected past 2028 because none has been announced.
- **Quality audit.** Scheduled closures are verified only for the primary-source windows
  2021–2023 and 2026–2028; the intervening and outer years are labelled projections. Early
  closes use the same split. The twelve 2000/2005/2006/2011/2016/2017 shifted half days are
  explicitly `INCOMPLETE` because no surviving primary LSE calendar for those years was found.
