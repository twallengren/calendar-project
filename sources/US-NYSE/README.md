# US-NYSE sources

| id | title | publisher | url / file | retrieved | covers | notes |
|----|-------|-----------|------------|-----------|--------|-------|
| `nyse-history-2008` | History of New York Stock Exchange Holidays (revised through November 2008) | NYSE | `nyse-holiday-history-2008.pdf` (text extract: `nyse-holiday-history-2008.txt`) | 2022-01-14 | 1885–2007 | The standard-holiday table (years observed, Rule 51 on Saturday holidays) and the "Special Closings, 1885–date" list. Primary source for everything before 2008. |
| `nyse-legacy-model-2022` | Structured rule model derived from `nyse-history-2008` | project maintainer | `legacy-rule-model-2022.json` | 2022-01-14 | 1885–2022 | Machine-readable transcription of the PDF (holiday rules, Saturday closings, observed-Friday lists, weekend history). Used to generate `us_nyse_special_closings_pre1960`. |
| `nyse-hours` | NYSE Holidays & Trading Hours | NYSE | https://www.nyse.com/markets/hours-calendars | 2026-09-12 | current and next two years | Published holiday and early-close schedule. Also the source for post-2008 one-off closings: Hurricane Sandy (2012-10-29/30), national days of mourning for George H. W. Bush (2018-12-05) and Jimmy Carter (2025-01-09). |

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
- **Early closes.** The regular early closes (Christmas Eve from 1990, day after Thanksgiving
  from 1992, Independence Day eve from 1995) and every one-off "Closed at ..." entry in the
  special closings list are modelled with their local close time. "Opened at ..." late opens
  are not modelled.
- **PDF typo.** The list prints "Dec. 12, 1950 (Sat) Saturday before Christmas Eve"; December 12,
  1950 was a Tuesday and the Saturday before Christmas Eve was December 23, 1950, which is the
  date used.
