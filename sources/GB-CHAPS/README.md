# GB-CHAPS sources

| id | title | publisher | url / file | retrieved | covers | notes |
|----|-------|-----------|------------|-----------|--------|-------|
| `boe-chaps-operating-days` | CHAPS | Bank of England | https://www.bankofengland.co.uk/payment-and-settlement/chaps | 2026-09-14 | current CHAPS operating-day policy | The CHAPS operator states that the system is usually open Monday-Friday excluding bank or public holidays in England and Wales. This ordinary operating calendar supports the PROJECTED baseline of no additional date-level early closures or exceptions; it is not an exhaustive record of actual operating incidents. Session hours and participant cutoffs are outside this date-only calendar. |
| `govuk-bank-holidays` | UK bank holidays | Government Digital Service, GOV.UK | https://www.gov.uk/bank-holidays | 2026-09-14 | England and Wales 2026-2027 | Official dated lists for England and Wales. The implementation transcribes all eight published bank holidays in each of 2026 and 2027, including the Christmas and Boxing Day substitute dates. |
| `boe-chaps-hours-next-steps-2026` | Extending RTGS and CHAPS settlement hours – next steps towards near 24x7 settlement | Bank of England | https://www.bankofengland.co.uk/paper/2026/cp/extending-rtgs-and-chaps-settlement-hours-next-steps | 2026-09-14 | May 2026 consultation and operating-hours roadmap | Confirms the September 2027 move to a 01:30 weekday opening, which does not change business dates. Sunday and selected bank-holiday settlement remains a consultation proposal for no earlier than 2029; a later 22x6 phase is proposed for no earlier than 2031. |

## Modelling decisions

The Bank of England supplies the CHAPS operating-day rule; GOV.UK supplies every dated England and
Wales bank holiday in the bounded interval. Exact dates are retained so the Christmas and Boxing
Day substitutes follow the official list without inferring a general substitution algorithm.

The September 2027 earlier opening is intraday and leaves this date-only calendar unchanged. The
May 2026 weekend and bank-holiday proposal has no effect before the calendar ends and no effect at
all unless adopted.
