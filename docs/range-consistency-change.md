# Range-consistent calendar generation

A single-day NYSE query for July 3, 2026 previously omitted Independence Day's observed
closure because its original date, July 4, was outside the query. A wider export included the
closure, while navigation from July 2 incorrectly selected July 3. The generation engine now
includes observation dependencies: July 3 is closed and the next business day is July 6.

Nearest-weekday observation includes both boundary days. Cascading observation resolves earlier
competitors with stable identities, a per-call cache, and an explicit work stack. Its limits are
366 calendar days of observation delay and 10,000 uncached placements per root calculation.
Reference rules use inverse offset intervals to include contributing reference years. Unsupported
chronology dependencies and date overflow fail explicitly. Provenance breaks ties between
otherwise identical output rows so their ordering also stays consistent across ranges.

Active-year filtering still uses original ISO dates; observation precedes deltas and classification.
The specification documents the [consistency guarantee and limits](../spec/SPEC.md#date-range-consistency).

## Verification

`JAVA_HOME=/home/torenwallengren/.sdkman/candidates/java/21.0.9-tem ./gradlew :tools:spotlessApply build :tools:installDist --offline`
passed with 527 tests, zero failures, and zero skipped tests. The added tests cover boundary
observations, reference offsets in both directions, supported chronologies, delta ordering, stable
duplicates and provenance, date overflow, long cascading dependencies, and both implementation
limits. The property test runs 80 generated scenarios across all three policies and three weekend
conventions, comparing complete event lists for nested ranges and adjacent partitions.

CLI checks confirmed:

```text
2026-07-03 is NOT a business day
  Reason: Independence Day (CLOSED)
Next business day after 2026-07-02: 2026-07-06
```

Published ranges were regenerated into `build/range-consistency-artifacts/` and compared using
complete CSV row multisets, preserving duplicate row counts. Every header and row collection
matched; no golden expectations, blessed artifacts, or release-history files were changed.

| Calendar | Inclusive range | Rows | Added / removed |
|---|---|---:|---:|
| US-MARKET-BASE | 1900-01-01 – 2030-12-31 | 14,502 | 0 / 0 |
| US-NYSE | 1900-01-01 – 2030-12-31 | 15,394 | 0 / 0 |
| US-CORP-IN-VISIBILITY | 1900-01-01 – 2030-12-31 | 14,507 | 0 / 0 |
| SA-TADAWUL | 2020-01-01 – 2030-12-31 | 1,255 | 0 / 0 |
