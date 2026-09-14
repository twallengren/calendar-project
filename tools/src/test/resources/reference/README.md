# Reference cross-validation data

`ReferenceCrossValidationTest` compares generated calendars against these third-party datasets
for the overlapping date range. Each `<CALENDAR>/<source>.csv` lists `date,type,close_time` for
closures and early closes (weekends omitted). `allowlist.csv` in the same directory lists the
known, explained differences (`source,side,date,type,reason`; `side` is `ours` when we have an
event the reference lacks, `theirs` when the reference has one we lack). A stale allowlist row
fails the test.

Regenerate the CSVs manually (never in CI):

```bash
python3 -m venv .venv
.venv/bin/pip install -r scripts/reference/requirements.txt
.venv/bin/python scripts/reference/export_reference_calendars.py
```

| file | library | notes |
|------|---------|-------|
| `US-NYSE/exchange_calendars-XNYS.csv` | exchange_calendars 4.11 | Models Monday–Friday only; claims 2:00 pm Christmas Eve closes back to 1900 that the NYSE's own history does not list. |
| `US-NYSE/quantlib-nyse.csv` | QuantLib 1.43 `UnitedStates(NYSE)` | Full closures only, from 1980. |
| `SA-TADAWUL/exchange_calendars-XSAU.csv` | exchange_calendars 4.11 | Bounded to 2021–2025. |
| `JP-JPX/exchange_calendars-XTKS.csv` | exchange_calendars 4.11 | Models Monday–Friday only; carries the Vernal/Autumnal Equinox holidays as a table that ends with 2025, so it reports no equinox (and no equinox-derived citizens' holiday) from 2026 on. |
| `JP-JPX/quantlib-japan.csv` | QuantLib 1.43 `Japan()` | Full closures only. Japan's national-holiday calendar rather than the exchange's, but it happens to close December 31 – January 3 as JPX does; it has no notion of an unscheduled market outage. |
