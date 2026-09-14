# SA-TADAWUL sources

| id | title | publisher | url / file | retrieved | covers | notes |
|----|-------|-----------|------------|-----------|--------|-------|
| `saudi-exchange-announcements` | Saudi Exchange holiday announcements | Saudi Exchange (Tadawul) | https://www.saudiexchange.sa/ (Market Announcements, "Holiday"); transcript in `saudi-exchange-announcements.md` | 2026-02-15 | 2020 Eid al-Fitr; 2021–2029 | Each available announcement states the last trading day before and the first trading day after an Eid closure, and the observed date of National Day and Founding Day. The live archive lacks the 2020 Eid al-Adha notice. The 2027–2029 Eid dates are published in advance "according to the Umm al-Qura calendar" and are treated as CONFIRMED. |
| `argaam-tadawul-eid-adha-2020` | Tadawul to close 7 days for Eid Al-Adha holiday | Argaam | https://www.argaam.com/en/article/articledetail/id/1387677 | 2026-09-14 | 29 July–4 August 2020 | Contemporary report that attributes the closure and 5 August resumption to Tadawul. It establishes that the current calendar is incomplete, but is not used to confirm exact rows without the exchange's primary notice. |
| `saudi-weekend-change-2013` | Royal Decree changing the weekend to Friday–Saturday | Saudi Press Agency | https://www.spa.gov.sa/ (23 June 2013) | 2026-09-12 | from 2013-06-29 | The Saudi Exchange's first Friday–Saturday weekend was 28–29 June 2013. Before that the weekend was Thursday–Friday. |

## Modelling decisions recorded against these sources

- Eid closures are transcribed as explicit date ranges (the exchange closes for the whole
  announced period, weekend days included). Years beyond the last announcement are
  `status: PROJECTED` from the Umm al-Qura calendar using the spans the exchange has
  consistently announced: 28 Ramadan – 4 Shawwal (Eid al-Fitr) and 5 – 13 Dhu al-Hijjah
  (Eid al-Adha).
- National Day and Founding Day move to the nearest working day when they fall on a weekend,
  as the announcements for 2022, 2023 and 2025 show.
- The live exchange calendar no longer exposes a 2020 Eid al-Adha entry. A contemporary report
  attributes a 29 July–4 August closure and 5 August resumption to Tadawul, but without the primary
  notice (`argaam-tadawul-eid-adha-2020`) the `SCHEDULED_CLOSURES` scope remains `INCOMPLETE` for all of 2020. This prevents an
  apparently open answer while retaining the scheduled rows for inspection.
