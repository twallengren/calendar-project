# CHINESE_HK / HKEX delivery evidence

The compiler profile is ICU4J 78.3 at fixed UTC+08:00, with inclusive ISO support from
1929-01-01 through 2100-12-31. Native years use the Gregorian year containing Chinese New Year;
months use exact `M01`–`M12` / `MxxL` identities. Every supported ISO date is round-tripped in
`ChineseHkChronologyProviderTest`.

The conversion fixtures retain original annual text tables from the [Hong Kong
Observatory](https://www.hko.gov.hk/en/gts/time/conversion.htm), with file hashes in
`sources/HK-HKEX/register.json`. They cover the two support edges, 2023 leap month two, 2025 leap
month six, the 2033/2034 transition, and HKO's 2057/2089/2097 uncertainty examples. HKO and ICU
differ at 2057-09-28/29 as documented in the profile guide. They also differ at the near-midnight
2027 new moon: [HKO's original 2027 table](https://www.hko.gov.hk/en/gts/time/calendar/text/files/T2027e.txt)
starts month one on 6 February, while ICU starts it on 7 February.

HKEX remains bounded to its already verified 2018–2027 interval. Lunar rules carry native origins
and market substitution policies only inside that interval. The 2027 ICU/HKO difference is handled
with cited remove/add deltas: the generated output has weekend rows on 6–7 February, authoritative
closures on 8–9 February, and no event on 10 February. Replacement rows deliberately carry no
native origin rather than falsely labeling the HKO dates with ICU identities. Government and
exchange evidence are the [2027 general-holidays list](https://www.gov.hk/en/about/abouthk/holiday/2027.htm)
and the [HKEX calendar](https://www.hkex.com.hk/News/HKEX-Calendar?sc_lang=en).

`QINGMING_HK` contains HKO's published Bright & Clear rows for 2016–2029. Those bounds cover the
generator padding around the evidenced HKEX interval; an outside year is an explicit error. It is
a cited lookup table, not an astronomical approximation.

Scratch generation for 2018–2027 produced 1,208 rows, exactly the existing row count and the same
multiset of dates, keys and event types. `ci-diff` reports no additions or removals. It classifies
the migration as MAJOR because historical descriptions become the stable rule name and genuine
Sunday substitutions now populate `observed_from`; these are provenance/lineage changes, not
closure-answer changes. The focused 2022/2027 goldens were updated and rerun without update mode.

Known limits remain explicit: no HKEX date after 2027 is published as confirmed, the disputed
distant ICU dates are not exchange evidence, and unscheduled weather closures before 23 September
2024 remain incomplete. The full build from the pinned `2f5e1aa` base reaches all 791 tests; its 21
failures are the base branch's already known `US-CORP-IN-VISIBILITY` checksum mismatch against
`sources/US-NYSE/README.md`. The isolated HK strict validation and all new focused tests pass.
