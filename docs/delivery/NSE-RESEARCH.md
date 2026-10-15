# NSE (National Stock Exchange of India, XNSE) research notes

Research carried out on 2026-10-15 by a delegated agent from the exchange's own circulars
(`https://nsearchives.nseindia.com/content/circulars/<DEPT><number>.pdf`), press statements, Fact
Books and the live holiday API; not yet modelled because of the special-session gap described in
`SPECIAL-SESSIONS-DESIGN.md`. Every statement below should be re-read from the cited circular
before it becomes a row; the circular numbers are the citation. `nse-circulars-manifest.csv` lists
the documents retrieved with their checksums so they can be re-fetched and verified.

## Rules the exchange states

- The annual list (issued each December "In pursuance to clause 2 of Chapter IX of the Bye-Laws and
  Regulation 2.3.1 of part A Regulations of the Capital Market Segment") gives the weekday
  holidays and, in a separate table, the holidays falling on Saturday or Sunday; **no weekday is
  ever substituted**. The archived holiday page adds that the exchange "may however close the
  market on days other than the above schedule holidays or may open the market on days originally
  declared as holidays".
- Mid-year additions come "in partial modification" of the annual circular; no listed holiday was
  removed 2010-2026.
- Every annual list marks Diwali-Laxmi Pujan with "Muhurat Trading will be conducted ... Timings
  ... shall be notified subsequently".

## Weekday closures per year (annual circular; `+` added mid-year)

- 2010 (NSE/CMTR/13713): 01-01, 01-26, 02-12, 03-01, 03-24, 04-02, 04-14, 09-10, 11-05 (Muhurat),
  11-17, 12-17.
- 2011 (NSE/CMTR/16348): 01-26, 03-02, 04-12, 04-14, 04-22, 08-15, 08-31, 09-01, 10-06,
  10-26 (Muhurat), 10-27, 11-07, 11-10, 12-06.
- 2012 (NSE/CMTR/19539): 01-26, 02-20, 03-08, 04-05, 04-06, 05-01, 08-15, 08-20, 09-19, 10-02,
  10-24, 11-13 (Muhurat), 11-14, 11-28, 12-25.
- 2013 (NSE/CMTR/22317): 03-27, 03-29, 04-19, 04-24, 05-01, 08-09, 08-15, 09-09, 10-02, 10-16,
  11-04, 11-14, 12-25. Muhurat on Sunday 11-03.
- 2014 (NSE/CMTR/25326): 02-27, 03-17, 04-08, 04-14, 04-18, 05-01, 07-29, 08-15, 08-29, 10-02,
  10-03, 10-06, 10-23 (Muhurat), 10-24, 11-04, 11-06, 12-25; + 04-24 (NSE/CMTR/26360,
  parliamentary elections in Mumbai).
- 2015 (NSE/CMTR/28337): 01-26, 02-17, 03-06, 04-02, 04-03, 04-14, 05-01, 09-17, 09-25, 10-02,
  10-22, 11-11 (Muhurat), 11-12, 11-25, 12-25. Saturday 02-28 a full trading day (budget,
  NSE/CMTR/28939).
- 2016 (NSE/CMTR/31297): 01-26, 03-07, 03-24, 03-25, 04-14, 04-15, 04-19, 07-06, 08-15, 09-05,
  09-13, 10-11, 10-12, 10-31, 11-14. Muhurat on Sunday 10-30.
- 2017 (NSE/CMTR/33746): 01-26, 02-24, 03-13, 04-04, 04-14, 05-01, 06-26, 08-15, 08-25, 10-02,
  10-19 (Muhurat), 10-20, 12-25. 02-21 (municipal poll) was a settlement holiday only; the cash
  segment traded.
- 2018 (NSE/CMTR/36475): 01-26, 02-13, 03-02, 03-29, 03-30, 05-01, 08-15, 08-22, 09-13, 09-20,
  10-02, 10-18, 11-07 (Muhurat), 11-08, 11-23, 12-25.
- 2019 (NSE/CMTR/39612): 03-04, 03-21, 04-17, 04-19, 05-01, 06-05, 08-12, 08-15, 09-02, 09-10,
  10-02, 10-08, 10-28, 11-12, 12-25; + 04-29 (NSE/CMTR/40536, elections) and 10-21
  (NSE/MSD/42319, Maharashtra assembly elections). Muhurat on Sunday 10-27.
- 2020 (NSE/CMTR/42877): 02-21, 03-10, 04-02, 04-06, 04-10, 04-14, 05-01, 05-25, 10-02, 11-16,
  11-30, 12-25. Muhurat on Saturday 11-14. Saturday 02-01 a full trading day (budget,
  NSE/CMTR/43290). No pandemic closure found.
- 2021 (NSE/CMTR/46623): 01-26, 03-11, 03-29, 04-02, 04-14, 04-21, 05-13, 07-21, 08-19, 09-10,
  10-15, 11-04 (Muhurat), 11-05, 11-19.
- 2022 (NSE/CMTR/50560): 01-26, 03-01, 03-18, 04-14, 04-15, 05-03, 08-09, 08-15, 08-31, 10-05,
  10-24 (Muhurat), 10-26, 11-08.
- 2023 (NSE/CMTR/54757): 01-26, 03-07, 03-30, 04-04, 04-07, 04-14, 05-01, 06-28, 08-15, 09-19,
  10-02, 10-24, 11-14, 11-27, 12-25. Muhurat on Sunday 11-12.
- 2024 (NSE/CMTR/59722): 01-26, 03-08, 03-25, 03-29, 04-11, 04-17, 05-01, 06-17, 07-17, 08-15,
  10-02, 11-01 (Muhurat), 11-15, 12-25; + 01-22 (NSE/CMTR/60338, Maharashtra public holiday for
  the Ram temple consecration), 05-20 (NSE/CMTR/61518, elections), 11-20 (NSE/CMTR/64960,
  Maharashtra assembly elections). Saturday 01-20 a full regular day (NSE/MSD/60340, withdrawing
  the planned special session); Saturdays 03-02 and 05-18 two-part special sessions with
  settlement (NSE/MSD/60677, NSE/MSD/61893).
- 2025 (NSE/CMTR/65587): 02-26, 03-14, 03-31, 04-10, 04-14, 04-18, 05-01, 08-15, 08-27, 10-02,
  10-21 (Muhurat, first afternoon session 13:45-14:45), 10-22, 11-05, 12-25. Saturday 02-01 a full
  trading day (NSE/CMTR/65729, NSE/MSD/66381).
- 2026 (NSE/CMTR/71775; live API agrees): 01-26, 03-03, 03-26, 03-31, 04-03, 04-14, 05-01, 05-28,
  06-26, 09-14, 10-02, 10-20, 11-10, 11-24, 12-25; + 01-15 (NSE/CMTR/72260, municipal election).
  Muhurat on Sunday 11-08 (timings not yet issued). Sunday 02-01 a full trading day
  (NSE/CMTR/72349).
- 2027: not yet published.

## Muhurat sessions (normal-market window)

2010-11-05 18:15-19:00 (NSE/CMTR/16062); 2011-10-26 16:45-18:00 (19187); 2012-11-13 15:45-17:00
(22027, revised 22083); 2013-11-03 18:15-19:30 (24773); 2014-10-23 18:30-19:30 (27779);
2015-11-11 17:45-18:45 (31047); 2016-10-30 18:30-19:30 (33424); 2017-10-19 18:30-19:30 (35993);
2018-11-07 17:30-18:30 (39216); 2019-10-27 18:15-19:15 (42483); 2020-11-14 18:15-19:15 (46230);
2021-11-04 18:15-19:15 (50050); 2022-10-24 18:15-19:15 (54023); 2023-11-12 18:15-19:15 (59124);
2024-11-01 18:00-19:00 (64628); 2025-10-21 13:45-14:45 (70319). Settlement: "All trades executed in
this Diwali Muhurat trading session shall result in settlement obligations" (2013 onward).

## Other special sessions

- Sunday 2012-11-11: gold-ETF-only session 11:00-15:30, settled separately (NSE/CMTR/22063).
- Dhanteras extended hours for gold ETFs (and sovereign gold bonds from 2016) after the regular
  close: 2011-10-24 to 20:00; 2014-10-21, 2015-11-09, 2016-10-28, 2017-10-17, 2018-11-05 to 19:00;
  2019-10-25 17:00-19:00. Regular market timing unchanged.
- Saturday mock sessions carry no settlement and are not trading days.

## Outages that changed a session

- 2021-02-24: all segments closed at 11:40 (telecom-link failure affecting the clearing
  corporation's risk system); reopened 15:45 after a 15:30 pre-open and extended to 17:00 (NSE
  statements of 24 and 26 February 2021; SEBI PR 9/2021). A gap, not a shortened day.
- 2017-07-10: trading halted about 09:55, resumed 12:30 after a 12:15 pre-open; normal close.

## Session structure

09:00-15:30 from 2010-01-04 (NSE/CMTR/13705, 13708); pre-open call auction 09:00-09:15 from
2010-10-18 (NSE/CMTR/15981); normal market 09:15-15:30 with closing session 15:40-16:00
(NSE/CMTR/41180 current timetable); closing auction session 15:15-15:35 for derivative-eligible
stocks from 2026-08-03 (NSE/CMTR/74466, 75479).

## Reconciliation data

Fact Book "No. of Trading Days" (fiscal years April-March): 2010-11 255, 2011-12 249, 2012-13 250,
2013-14 251, 2014-15 243, 2015-16 247, 2016-17 248, 2017-18 246, with monthly tables for most of
those years. Muhurat and the weekend special sessions are counted as trading days in most months;
a handful of months differ from weekdays-minus-holidays-plus-specials by one (February and May
2010, April and September 2012, May 2013, March 2014, October 2014), so the Fact Book is a
plus-or-minus-one check, not a day-level record. No counts were found after March 2018.
