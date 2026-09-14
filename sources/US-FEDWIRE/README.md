# US-FEDWIRE sources

| id | title | publisher | url / file | retrieved | covers | notes |
|----|-------|-----------|------------|-----------|--------|-------|
| `frfs-fedwire-operating-hours` | Fedwire Funds Service and National Settlement Service Operating Hours | Federal Reserve Financial Services | https://www.frbservices.org/resources/financial-services/wires/operating-hours.html | 2026-09-14 | current Fedwire Funds Service schedule | States that Fedwire Funds observes all Saturdays, all Sundays and the Federal Reserve Banks' Holiday Schedule, and defines every other calendar day as a funds-transfer business day. This ordinary operating calendar supports the PROJECTED baseline of no additional date-level early closures or exceptions; it is not an exhaustive record of actual operating incidents. Daily operating hours, cutoffs and possible extensions are outside this date-only calendar. |
| `frfs-holiday-schedule` | Federal Reserve System Holiday Schedule | Federal Reserve Financial Services | https://www.frbservices.org/about/holiday-schedules | 2026-09-14 | 2026-2030 holiday table; used only through 2027 | Lists every 2026-2030 Federal Reserve holiday. Its footnotes state that a Saturday holiday leaves Federal Reserve Banks and Branches open on the preceding Friday, while a Sunday holiday closes them on the following Monday. The implementation is deliberately bounded before the announced Fedwire operating-day change. |
| `federal-reserve-fedwire-expanded-days-2025` | Federal Reserve Board announces expanded operating days for Fedwire Funds and NSS | Board of Governors of the Federal Reserve System | https://www.federalreserve.gov/newsevents/pressreleases/other20251009a.htm | 2026-09-14 | announced 2028 or 2029 implementation | Announces six operating days per week, Sunday-Friday including weekday holidays, to be implemented in 2028 or 2029. It prevents projecting the current holiday closure policy through the full 2030 general holiday table. |

## Modelling decisions

The operating-hours page explicitly applies the Federal Reserve holiday table to Fedwire Funds.
The table's footnotes are asymmetric: Friday remains open before a Saturday holiday, while Monday
closes after a Sunday holiday. The bounded rules reproduce that behavior.

The calendar ends before 2028 because the announced move to six operating days will begin in 2028
or 2029, and its exact implementation date is not yet fixed. Other Federal Reserve payment and
settlement services are outside scope.
