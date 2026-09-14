# EU-EURONEXT sources

Euronext publishes one "Calendar of business days" / "Holiday Calendar" per year covering all
of its markets. The four cash markets modelled here — Euronext Amsterdam (XAMS), Brussels
(XBRU), Lisbon (XLIS) and Paris (XPAR) — always share the same column values in those
calendars, so these sources back `EU-EURONEXT` and all four venue calendars.

| id | title | publisher | url / file | retrieved | covers | notes |
|----|-------|-----------|------------|-----------|--------|-------|
| `euronext-hours-holidays` | Trading hours & Holidays | Euronext | https://live.euronext.com/en/resources/trading-hours-holidays (also https://www.euronext.com/en/trading/trading-hours-holidays) | 2026-09-14 | 2019–2026 | Per-venue closed / half trading day / full trading day tables for 2022–2026 and bullet lists for 2019–2021, plus the 14:05 CET half-day close time. Primary source for 2019 onwards. |
| `euronext-holiday-calendar-2026` | 2026 Holiday Calendar for Euronext's Cash and Derivatives markets (IF251107CADE) | Euronext | `euronext-holiday-calendar-2026.pdf` (https://connect2.euronext.com/sites/default/files/2025-11/IF251107CADE%202026%20Holiday%20Calendar%20for%20Euronexts%20Cash%20and%20Derivatives%20markets_1.pdf) | 2026-09-14 | 2026 | The last year Euronext has published. Fixes `coverage.verified_through` at 2026-12-31. |
| `euronext-holiday-calendar-2018` | Euronext announces 2018 holiday calendar for its cash and derivatives markets (20 October 2017) | Euronext | `euronext-holiday-calendar-2018.pdf` (https://www.euronext.com/sites/www.euronext.com/files/17.10.20.holiday_calendar_2018.pdf) | 2026-09-14 | 2018 | Press release; states the 14:05 CET half-day close for 24 and 31 December 2018. |
| `euronext-calendars-hours-archive` | Resources > Calendars & Hours: Calendar of business days (archived euronext.com pages) | Euronext | https://web.archive.org/web/20141218183351/https://www.euronext.com/en/holidays-and-hours (2014, 2015), https://web.archive.org/web/20160304211631/https://www.euronext.com/en/calendars-hours (2016), https://web.archive.org/web/20170805211203/https://www.euronext.com/en/calendars-hours (2017), https://web.archive.org/web/20181014221808/https://www.euronext.com/en/calendars-hours (2017, 2018) | 2026-09-14 | 2014–2018 | The euronext.com holiday page as captured by the Internet Archive; the live page no longer carries years before 2019. Source for the 14:00 → 14:05 CET half-day close change (14:05 from the 2014 page onwards) and for the 31 December 2015 full closure. |
| `nyse-euronext-calendar-2013` | NYSE Euronext announces its 2013 Holiday Calendar and early Closing | NYSE Euronext | https://www.euronext.com/en/about/media/euronext-press-releases/nyse-euronext-announces-its-2013-holiday-calendar-and-early | 2026-09-14 | 2013 | Last year with a 14:00 CET half-day close. |
| `nyse-euronext-calendar-2012` | Trading announcement: Calendar of cash business days 2012 (PAR\_20111114\_07899\_EUR, 14 November 2011) | NYSE Euronext | `calendar-of-cash-business-days-2012.pdf` (https://www.euronext.com/sites/www.euronext.com/files/calendar_2012.pdf, via the Internet Archive) | 2026-09-14 | 2012 | Rule Book I notice. Also published as a press release: https://www.euronext.com/en/about/media/euronext-press-releases/nyse-euronext-announces-its-2012-holiday-calendar-and-early |
| `nyse-euronext-calendar-2011` | NYSE Euronext annonce le Calendrier 2011 de ses marchés européens | NYSE Euronext | https://www.euronext.com/en/about/media/euronext-press-releases/nyse-euronext-annonce-le-calendrier-2011-de-ses-marches | 2026-09-14 | 2011 | Lists only Good Friday, Easter Monday and 26 December as closures; 24 and 31 December 2011 were Saturdays and 23 and 30 December closed at the usual 17:35 CET. |
| `nyse-euronext-calendar-2010` | Trading announcement: Calendar of cash business days 2010 (PAR\_20091015\_05039\_EUR, 15 October 2009) | NYSE Euronext | `calendar-of-cash-business-days-2010.pdf` (https://www.euronext.com/sites/www.euronext.com/files/calendar_of_cash_business_days_15_oct_2009.pdf, via the Internet Archive) | 2026-09-14 | 2010 | Rule Book I notice; earliest year in coverage. |

## Modelling decisions recorded against these sources

- **One calendar, four venues.** Every yearly Euronext calendar from 2010 to 2026 gives the
  Amsterdam, Brussels, Lisbon and Paris columns identical values on every row. The shared
  closures therefore live in `EU-EURONEXT` (`metadata.kind: base`) and the four venue
  calendars only `extends` it.
- **Holidays of other Euronext venues are not modelled.** The yearly calendars also list the
  Irish May Bank Holiday and the Dublin substitute closures, Maundy Thursday / Ascension Day /
  Constitution Day / Whit Monday for Oslo Børs, and Ferragosto and the year-end closures for
  Milan. Each of those rows records "Full Trading Day" for Amsterdam, Brussels, Lisbon and
  Paris, so none of them is a closure here. In particular **Whit Monday is not a Euronext
  cash-market holiday** in any year in coverage; it was dropped by Euronext before 2010 and is
  listed only under Oslo Børs.
- **No weekend observance.** `weekend_shift_policy: NONE`, and every closure carries
  `shift_policy: NONE` / `shiftable: false`. The sources are explicit: 1 January 2022 (Sat) is
  followed by "Monday 3 January 2022 (Substitute for New Year's Day) — Full Trading Day"; 25
  December 2022 (Sun) by "Tuesday 27 December 2022 (Substitute for Christmas Day) — Full
  Trading Day"; 26 December 2026 (Sat) by "Monday 28 December 2026 (Substitute for St
  Stephen's Day / Boxing Day) — Full Trading Day". In each case only the Dublin column reads
  "Closed". Labour Day and Christmas Day falling on a weekend (2010, 2011, 2016, 2021, 2022)
  are simply absent from the yearly list.
- **Year-end half days.** 24 and 31 December are half trading days when they fall on a weekday
  and are never moved: for 2016, 2017, 2022 and 2023 the calendars record the neighbouring
  Friday as closing "at the usual times" for these four venues (only Dublin takes a substitute
  half day). Hence `only_if_weekday: [MONDAY..FRIDAY]` and no shifting.
- **Half-day close time.** Euronext quotes one pan-European close time in CET: 14:00 CET
  through 2013 (`nyse-euronext-calendar-2010`, `-2012`, `-2013`) and 14:05 CET from 2014
  (`euronext-calendars-hours-archive`, `euronext-holiday-calendar-2018`,
  `euronext-hours-holidays`). Warrants and certificates close five minutes earlier (13:55 CET)
  and derivatives at various times from 13:55 CET; only the cash-market close is modelled.
  Because `close_time` is a **local** wall-clock time and Euronext Lisbon runs on WET/WEST,
  one hour behind CET all year, Lisbon takes its own half-day modules
  (`euronext_christmas_eve_lisbon`, `euronext_new_years_eve_lisbon`) with 13:00 / 13:05. This
  is the only venue-specific difference in the pack, and it is why the half-day modules sit in
  the venue calendars rather than in the shared `euronext_holidays` group.
- **31 December 2015 was a full closure, not a half day.** The archived "Calendar of business
  days 2015" lists "Thursday 31 December 2015 — New Year's Eve" among the days the markets are
  closed and announces a half trading day for 24 December only. Three separate Internet
  Archive captures of that page (December 2014, March 2015, September 2015) agree. The CAC 40,
  AEX and BEL 20 daily index histories have no session on 2015-12-31 either. Modelled as a
  one-off in `euronext_special_closures`; `exchange_calendars` disagrees and is allowlisted.
- **Coverage starts 2010-01-01.** That is the earliest year for which a Euronext notice was
  located; earlier years would need the pre-2010 Rule Book I announcements, which also cover
  holidays (Whit Monday, Bastille Day, Assumption, All Saints) that Euronext has since
  dropped, so they are deliberately out of coverage rather than rule-derived.
