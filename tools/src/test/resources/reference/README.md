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
| `GB-LSE/exchange_calendars-XLON.csv` | exchange_calendars 4.11 | Bounded to 2000-2030. |
| `GB-LSE/quantlib-uk-exchange.csv` | QuantLib 1.43 `UnitedKingdom(Exchange)` | Full closures only. |
| `DE-XETRA/exchange_calendars-XETR.csv` | exchange_calendars 4.11 | Asserts a 14:00 last-trading-day early close no Deutsche Börse calendar confirms. |
| `DE-XETRA/quantlib-germany-xetra.csv` | QuantLib 1.43 `Germany(Xetra)` | No New Year's Eve, Whit Monday, Unity Day or Reformation Day rules. |
| `CA-TSX/exchange_calendars-XTSE.csv` | exchange_calendars 4.11 | See allowlist for 2001-09-11/12 and Christmas Eve years. |
| `CA-TSX/quantlib-tsx.csv` | QuantLib 1.43 `Canada(TSX)` | Full closures only; exact match 2000-2030. |
| `FR-EURONEXT-PARIS/exchange_calendars-XPAR.csv` | exchange_calendars 4.11 | Same shape for XAMS, XBRU, XLIS under their venue directories. |
| `HK-HKEX/exchange_calendars-XHKG.csv` | exchange_calendars 4.11 | Includes unscheduled severe-weather closures. |
| `HK-HKEX/quantlib-hong-kong.csv` | QuantLib 1.43 `HongKong(HKEx)` | Hard-coded per-year lists, ends after 2025. |
| `AU-ASX/exchange_calendars-XASX.csv` | exchange_calendars 4.11 | Bounded to 2010-2030 here. Carries the 2010 and 2011 ANZAC Day additional days; still moves weekend Christmas Eve / New Year's Eve early closes to the Friday after the exchange stopped doing so in 2017 (see allowlist). |
| `AU-ASX/quantlib-australia-asx.csv` | QuantLib 1.43 `Australia(ASX)` | Full closures only; no ANZAC Day additional days (2010, 2011). |
| `SG-SGX/exchange_calendars-XSES.csv` | exchange_calendars 4.11 | Recorded only to 2025; every scheduled closure 2010-2025 matches; models no half-day sessions (see allowlist). QuantLib `Singapore(SGX)` is not used: it omits most lunar and religious holidays in several years. |
| `KR-KRX/exchange_calendars-XKRX.csv` | exchange_calendars 4.11 | Matches the exchange's lists 2010-2025; misses the 2026 Buddha's Birthday substitute, the 2026 election day and the 2026-2027 Labour Day / Constitution Day holidays (see allowlist). |
| `KR-KRX/quantlib-south-korea-krx.csv` | QuantLib 1.43 `SouthKorea(KRX)` | Hard-coded per-year lists; matches 2010-2024; misses the 2025 and 2026 election days and the 2026-2027 Labour Day / Constitution Day holidays. |
| `ZA-JSE/exchange_calendars-XJSE.csv` | exchange_calendars 4.11 | Statutory holidays with the Sunday rule plus some proclaimed days (to 2019); no early closes, no 2021-2026 proclamations (see allowlist). |
| `ZA-JSE/quantlib-south-africa.csv` | QuantLib 1.43 `SouthAfrica()` | National-holiday calendar with hard-coded one-off days; missing the 2011, 2014 and 2019 election days and the 2011/2016 27 December holidays. |
| `TW-TWSE/exchange_calendars-XTAI.csv` | exchange_calendars 4.11 | Close to the exchange's tables, typhoon closures included through 2024; see allowlist for the 2011 Labour Day substitute, settlement/bridge days, 2024-2026 typhoons, the 2025 Act's holidays and a spurious 2 January 2026. QuantLib `Taiwan()` is not used (82 disagreements). |
| `BR-B3/exchange_calendars-BVMF.csv` | exchange_calendars 4.11 | Exact match 2010-2027, including the Sao Paulo holidays through 2021 and the 2014 World Cup closure. |
| `BR-B3/quantlib-brazil-exchange.csv` | QuantLib 1.43 `Brazil(Exchange)` | Full closures only; lacks the 2014 World Cup closure and closes on the two 2020 Sao Paulo holidays the exchange traded (see allowlist). |
