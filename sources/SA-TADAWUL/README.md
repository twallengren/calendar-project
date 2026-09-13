# SA-TADAWUL sources

| id | title | publisher | url / file | retrieved | covers | notes |
|----|-------|-----------|------------|-----------|--------|-------|
| `saudi-exchange-announcements` | Saudi Exchange holiday announcements | Saudi Exchange (Tadawul) | https://www.saudiexchange.sa/ (Market Announcements, "Holiday"); transcript in `saudi-exchange-announcements.md` | 2026-02-15 | 2020–2029 | Each announcement states the last trading day before and the first trading day after an Eid closure, and the observed date of National Day and Founding Day. The 2027–2029 Eid dates are published in advance "according to the Umm al-Qura calendar" and are treated as CONFIRMED. |
| `saudi-weekend-change-2013` | Royal Decree changing the weekend to Friday–Saturday | Saudi Press Agency | https://www.spa.gov.sa/ (23 June 2013) | 2026-09-12 | from 2013-06-29 | The Saudi Exchange's first Friday–Saturday weekend was 28–29 June 2013. Before that the weekend was Thursday–Friday. |

## Modelling decisions recorded against these sources

- Eid closures are transcribed as explicit date ranges (the exchange closes for the whole
  announced period, weekend days included). Years beyond the last announcement are
  `status: PROJECTED` from the Umm al-Qura calendar using the spans the exchange has
  consistently announced: 28 Ramadan – 4 Shawwal (Eid al-Fitr) and 5 – 13 Dhu al-Hijjah
  (Eid al-Adha).
- National Day and Founding Day move to the nearest working day when they fall on a weekend,
  as the announcements for 2022, 2023 and 2025 show.
