# US-MARKET-BASE sources

This base calendar composes the shared US market holiday modules, which cite the NYSE sources in
`sources/US-NYSE/README.md`. The only citation it introduces itself is the weekend convention.

| id | title | publisher | url / file | retrieved | covers | notes |
|----|-------|-----------|------------|-----------|--------|-------|
| `convention` | Saturday-Sunday weekend | n/a | n/a | n/a | all years | Standard weekend definition; no external source. |
| `nyse-history-2008` | History of New York Stock Exchange Holidays | NYSE | see `sources/US-NYSE/README.md` | 2022-01-14 | 1885-2007 | Cited by the shared US holiday modules this calendar composes. |

This is a reusable rule base rather than a venue calendar. All three completeness scopes are
therefore labelled `PROJECTED`; its legacy `verified_through` value is not treated as evidence
that a particular market's closures, half days, or exceptions are complete.
