# Compiler chronology profiles

Native computation belongs to `tools`; `core` exposes only the dependency-free
`NativeDate(chronologyId, year, monthCode, day)` value. Neither the Java query API
nor Python needs ICU at runtime. `tools` pins ICU4J 78.3, verified by Gradle's
committed dependency checksums. The compiler distribution includes its Unicode
license under `META-INF/licenses/icu4j-78.3-LICENSE`.

`ChronologyProvider` describes a calculation profile, inclusive supported ISO
interval, ordered native months and month lengths. Missing profiles, unsupported
coverage and invalid exact dates fail explicitly. The legacy numeric API and
existing YAML rule types retain their meanings; numeric algorithms are explicitly
registered instead of loaded reflectively with silent fallback.

## Hebrew

`HEBREW` is the fixed arithmetic Hebrew profile, with civil midnight date mapping,
using ICU4J 78.3. Conversion support is 1901-01-01 through 2100-12-31 inclusive.
Religious sunset instants are outside this profile. Ordered month identities are:

`TISHRI HESHVAN KISLEV TEVET SHEVAT ADAR_I ADAR_II NISAN IYAR SIVAN TAMUZ AV ELUL`

In a common year `ADAR` replaces both `ADAR_I` and `ADAR_II`. Exact dates never
alias those identities. A recurring rule explicitly selects the required union:

```yaml
type: native_fixed_month_day
chronology: HEBREW
month_codes: [ADAR, ADAR_II]
day: 14
native_years: [{start: 5784, end: 5786}]
```

A valid selected month absent from a particular year is skipped. Unknown month
codes and malformed days fail. Days exceeding a valid month's actual length are
skipped for recurring rules and rejected for exact dates. `native_years` filters
the origin's native year; enclosing `active_years` still filters the nominal ISO
year, and both filters must pass. `duration_days` means elapsed civil days.

The other additive rule types are `native_nth_weekday` (`weekday` and `nth`, with
`-1` meaning last), `native_relative_to_reference` (`day` and elapsed `offset_days`
from the selected native month), and `native_explicit_dates` (`dates` containing
`chronology_id`, `year`, `month_code`, `day`). Exact date lists preserve duplicate
occurrences. Native origins and any span, relative-offset and observation
dependencies must fit the provider's support; generation fails if they do not.

```bash
./gradlew :tools:run --args='convert --from-chronology HEBREW --year 5786 --month-code TISHRI --day 1 --to-chronology ISO --format json'
```

The JSON result identifies both calculation profiles/providers. Legacy numeric
months use `M01` through `M12` in this command. Alternate chronology CSV export
uses this same provider boundary and validates all conversions before opening the
output file; an unsupported conversion never becomes a blank date.

Independent fixtures use the [Bank of Israel 2024/2025 services calendar](https://www.boi.org.il/media/irfl2amk/list-of-holidays-affecting-bank-of-israel-services-in-2024-2025.pdf)
for civil holiday dates, including common/leap Adar distinctions. Those fixtures
are conversion evidence, not assertions about TASE operating days. Every day in
the supported interval additionally undergoes a round-trip regression. ICU's
[civil mapping documentation](https://unicode-org.github.io/icu-docs/apidoc/released/icu4j/com/ibm/icu/util/HebrewCalendar.html)
describes the underlying arithmetic profile. This foundation does not yet publish
TASE data or retain native origin fields in enriched event artifacts.

## Persian correction

`PERSIAN` continues to mean the existing 2820-year arithmetic approximation with
its 682/2816 leap formula. It does not mean Iran's astronomical civil calendar.
The epoch has been corrected from integer JDN 1948320 to 1948321, corresponding to
622-03-19 Julian / 622-03-22 Gregorian. The old value shifted all results one day
early. See the [original algorithmic converter](https://www.fourmilab.ch/documents/calendar/)
and [Calendrical Calculations reference epoch](https://github.com/EdReingold/calendar-code2/blob/master/calendar.l).
No published calendar currently uses `PERSIAN`; its existing leap-year policy is
unchanged, including its disagreement with astronomical dates around 1403/1404.

## Modern Chinese calendar for Hong Kong

`CHINESE_HK` uses ICU4J 78.3's modern Chinese calendar at fixed UTC+08:00 over the
inclusive ISO interval 1929-01-01 through 2100-12-31. The fixed calculation zone is
part of the profile and is independent of a market's timezone. A native year is the
Gregorian year in which its first lunar month begins, so the first supported civil
date is `1928-M11-21` and the final one is `2100-M12-01`.

Month codes are `M01` through `M12`; an `L` suffix denotes the distinct intercalary
month. Thus 2023 orders `M02L` after `M02`, and 2025 orders `M06L` after `M06`.
Exact conversion rejects an absent intercalary month or a day beyond the actual
29/30-day month. Recurring selectors skip a valid month identity when it is absent;
they never alias it to the ordinary month.

The independent fixtures retain selected original [Hong Kong Observatory annual
tables](https://www.hko.gov.hk/en/gts/time/conversion.htm), including both support
boundaries, the 2023 and 2025 leap months, and the 2033/2034 transition. ICU and
HKO also differ at the near-midnight 2027 new moon: HKO starts the lunar year on
February 6 while ICU starts it on February 7. HKEX uses the gazetted HKO date, so
its calendar replaces the affected ICU rows with cited authoritative dates that
carry no false native origin. HKO warns that distant new moons close to midnight
may move by a day in 2057, 2089 and 2097. The pinned ICU profile differs at the
first warned date: HKO starts month nine on 2057-09-28, while ICU reports
`2057-M08-30` and starts month nine on 2057-09-29.
ICU and HKO agree on the checked starts at 2089-09-04 (`M08`) and 2097-08-07
(`M07`). Tests preserve this difference explicitly; it is not silently corrected,
and no disputed future conversion is labeled as a confirmed exchange event.

`QINGMING_HK` is separate from lunar conversion. It is a table of HKO's published
Bright & Clear solar-term dates for 2016–2029, wide enough for the padded generation
of HKEX's evidenced 2018–2027 interval. It throws outside that range rather than
extrapolating an astronomical formula. HKEX lunar and Ching Ming rules likewise end
at the last exchange-confirmed year; the chronology provider alone does not confer
holiday status or a substitution policy.

## Reproducibility

Chronology YAML rejects unknown fields; code generation rejects unsupported
algorithms and validates the entire typed formula expression before writing Java.
`ChronologySafetyTest` regenerates all sources into scratch space and compares
file membership and bytes with the committed directory. Generated files use the
generator's own deterministic formatting; edit YAML or the generator, then run
`generateChronologies` and review the resulting diff.
