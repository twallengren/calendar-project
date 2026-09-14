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

## Chinese profile reserved for the next integration gate

`CHINESE_HK` is deliberately not registered yet. Its required design is fixed
UTC+08:00 calculations over ISO 1929–2100, independent of market timezone. Native
year is the Gregorian year in which the lunar year begins; month identities
`M04` and `M04L` distinguish ordinary and intercalary months. A recurring rule's
`month_codes` must explicitly enumerate ordinary, intercalary or both identities.
The adapter, HKO comparison and cited Qingming reference table follow the TASE
integration gate. An unimplemented profile fails instead of using ICU defaults.

## Reproducibility

Chronology YAML rejects unknown fields; code generation rejects unsupported
algorithms and validates the entire typed formula expression before writing Java.
`ChronologySafetyTest` regenerates all sources into scratch space and compares
file membership and bytes with the committed directory. Generated files use the
generator's own deterministic formatting; edit YAML or the generator, then run
`generateChronologies` and review the resulting diff.
