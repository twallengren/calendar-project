# EU-TARGET sources

| id | title | publisher | url / file | retrieved | covers | notes |
|----|-------|-----------|------------|-----------|--------|-------|
| `ecb-t2-opening-hours` | What is T2? | European Central Bank | https://www.ecb.europa.eu/paym/target/t2/html/index.en.html | 2026-09-14 | current euro T2 operating schedule | Defines T2 as the Eurosystem's RTGS system and states that euro settlement is open Monday-Friday except 1 January, Good Friday, Easter Monday, 1 May, 25 December and 26 December. This ordinary operating calendar supports the PROJECTED baseline of no additional date-level early closures or exceptions; it is not an exhaustive record of actual operating incidents. Other TARGET services and currencies can have different availability. |
| `ecb-target-holidays-2026-2027` | ECB public holidays and TARGET closing days | European Central Bank | https://www.ecb.europa.eu/ecb/contacts/working-hours/html/index.en.html | 2026-09-14 | 2026-2027 | The annual tables mark TARGET closing days with an asterisk. They confirm the exact Good Friday and Easter Monday dates and show that ECB staff holidays such as Ascension Day, Christmas Eve and New Year's Eve are not TARGET closing days. |
| `ecb-t2-hours-outcome-2026` | Outcome of the public consultation on the extension of T2 operating hours | European Central Bank | https://www.ecb.europa.eu/press/payments-news/ecb.pubconpm202605.en.pdf | 2026-09-14 | May 2026 operating-hours roadmap | Confirms that T2 RTGS remains closed for payments on weekends and the six TARGET holidays. A short weekend/holiday window for liquidity transfers is planned within two years without changing value dating; broader RTGS expansion remains a future consideration. This calendar therefore represents RTGS payment business dates, not every CLM or TIPS operation. |

## Modelling decisions

This calendar is limited to euro T2 RTGS business dates for customer and interbank payments. It
does not claim that every TARGET service is closed: TIPS is continuously available, and the ECB's
planned limited liquidity-transfer windows do not create a new RTGS payment business date.

The six named TARGET holidays have no substitute-day rule. A holiday that falls on a weekend is
represented by the ordinary weekend row only. ECB staff holidays without the TARGET marker are
operating dates for this calendar.
