# Native calendars and financial operations delivery ledger

The delivery plan supplied on 2026-09-14 is the acceptance contract. A local implementation or a passing test is not evidence of publication. Published history is immutable. The incident source commit is `8d95201`; the corrected source commit must be recorded separately after review.

| Work package | Owner | Dependencies | Implementation / evidence | Review / release |
|---|---|---|---|---|
| Release comparison, PR preparation and exact-commit publication | Sol release agent | Actual published v11 baseline | Integrated comparator, release PR preparation, independent streams, immutable descriptor and exact-commit retry checks | Terra reviewed through 35aaa79; 40 release regressions pass; no publication |
| Cross-validation multiplicity and close times | Astra orchestrator | Existing reference contract | Complete multiset comparison; precise close-time allowlists; published scratch CSV unchanged before audit corrections | Terra reviewed; full build passed; not released |
| Historical publication bounds | Sol history agent | Evidence, never inferred from archive timestamp | Integrated publication ledger with evidenced inclusive lower/exclusive upper bounds and authenticated latest-observation cap; legacy snapshots accessible by version | Terra reviewed; focused tests pass |
| Coverage audit and safe unknown assessments | Sol history agent | Authoritative sources, additive consumer contracts | All existing calendars have explicit per-scope intervals; NYSE 1975 early close corrected; dangerous gaps are UNKNOWN | Terra reviewed consumer safety; malformed metadata and ambiguous source IDs fixed; not released |
| API links and browser execution | Astra orchestrator | Existing /v1 contract | Relative JSON/ICS links; actual Chrome root and project-prefix execution; quality-aware browser traversal | Link fixes reviewed; enriched behavior under verification |
| Native chronology providers and Hebrew rules | Astra chronology agent | ICU pin, conversion fixtures, range contract | Integrated ac52157 and 50fd327; ICU78.3 Hebrew, explicit month identity, conversions, bounded expansion, codegen verification and Persian profile correction | Terra independently reviewed and ran focused tests; full build passed |
| Canonical source register and /v2 | Astra integration | Coverage / provenance contracts | Canonical JSON tables and checksums; native event provenance compiled and consumed occurrence-by-occurrence; v2 daily assessments and CLI | Java/Python focused regressions passing; full verification/review pending |
| TASE integration | Sol TASE agent | Hebrew provider acceptance | Native Hebrew rules, 2026 weekend transition and bounded authoritative schedules integrated; unsupported early closes removed | Root reviewed primary PDFs and CLI/Python/MCP representation/v2/HTML parity; actual state explicitly unknown where exception coverage is incomplete; not released |
| Chinese/HKEX integration | Astra design / Sol implementation | Bounded TASE gate and regional profile | Integrated bounded ICU profile, HKO fixtures and Qingming table; native holidays plus authoritative 2027 overrides | Terra gate accepted; actual browser and native consumer checks pass; not released |
| Financial operations and payment calendars | Sol operations / Sol payment / Astra integration | Both native-market gates accepted | Implementation in isolated worktrees; payment publication/schema integration underway | Pending independent review; not released |
| Recovery packages and remote installation | Release / verification | Milestone 1 review, CI exact SHA, credentials | Pending | Not published |

## Gates

1. Complete published-to-candidate release report; dangerous answers corrected or explicitly unknown; retry conflicts fail closed.
2. Native rules preserve range consistency; missing evidence and failed conversion cannot become successful answers.
3. TASE native rules, authoritative examples and effective weekends work through consumers before HKEX integration.
4. HKEX and financial conventions/payment calendars pass Java/Python/CLI/JSON/browser/MCP checks.

Independent verification includes full Gradle build, strict validation, cross-validation, scratch artifact counts/diffs, Python package and browser execution. Golden changes require explanations. Account or credential setup is an external blocker, never a completed release.

## Integrated verification evidence

Before the coverage audit, all 14 published calendars regenerated into `build/delivery-regenerated` with identical complete CSV counters; `ci-diff` reported NONE. The 1975 NYSE early-close correction discovered in the audit will intentionally change that result. Native provenance changes add metadata without changing those CSV fields. Root and `/calendar-project/` browser checks ran against the pre-recovery artifacts. Updated quality-aware browser tests and recovery fixtures must also pass against the candidate. Python integration currently passes 98 tests (one existing optional check skipped), including installed MCP extras; additional joint regression added afterward. This ledger records local evidence, not remote package installation.

The bounded TASE gate covers native-to-civil conversion, evidence-bearing events and explicit unknown assessments. It does not claim that TASE actual trading-day coverage is complete: unscheduled exceptions remain incomplete throughout the initial range. Java/Python bundle and actual MCP server checks will be repeated after candidate data synchronization. The parity exporter now records typed coverage errors, their exact dates and enriched assessments for every bundled market.

After the full scope audit, scratch regeneration in `build/delivery-audit` compared counted complete CSV records for all 14 existing calendars. Only US-NYSE changed: one authoritative EARLY_CLOSE on 1975-12-24 at 14:00. `ci-diff` reported MAJOR with exactly that addition. Strict validation passed all 15 specifications; the full Java build passed and Python passed 107 tests (one optional check skipped) before the publication-ledger follow-up.

## Native gate and recovery checkpoint (2026-09-14)

The integrated Hebrew/Chinese source build passed. All 15 specifications passed strict validation,
and every configured reference comparison had zero unexplained differences. HKEX still has 1,208
rows over 2018–2027: dates, keys and event types are unchanged; 31 descriptions are normalized and
observation lineage is now recorded. The ICU/HKO disagreements in 2027 and 2057 are explicit tested
profile limitations. Cited 2027 exchange overrides close February 8–9, leave February 10 open, and
carry no false ICU native date. Every Qingming lookup row now has a retained, hashed HKO original.

TASE 2025-09-23, HKEX 2025-01-29 and the HKEX 2027-02-09 override passed artifact/Java CLI/Python/
MCP representation/v2/HTML checks. Actual MCP subprocess tests passed all eight cases against the
candidate bundle. The candidate's refreshed browser fixture and JSON/ICS links passed in Chrome
at both `/` and `/calendar-project/`. The independent Terra reviewer accepted the bounded native
market gate. This accepts explicit incomplete coverage, not a claim that TASE's actual state or
pre-policy HKEX weather exceptions are complete.

`scripts/verification/benchmark.py` records representative CLI timings including JVM startup.
The initial local medians were Hebrew generation 1.057s (2025–2027), Chinese generation 1.491s
(2018–2027), NYSE generation 1.170s (1900–2030), one-year artifact count 0.677s, native assessment
1.042s and joint offset 0.788s. These are machine-specific observations for future comparisons,
not CI performance thresholds.

The recovery baseline was authenticated from GitHub release v11.0.0, published
2026-02-16T21:43:23Z, whose dataset contains exactly four calendars. Its source is
3765bcb10d3ebcb7cfc338bcebf94505af25ec1e; the release tag commit is
3a8f40fdf8eb49aad72df3e7369b786d98791ef1. Today's already-modified blessed directory is not used
to reconstruct that baseline. Initial candidate runs exposed and fixed executable-mode, fixture
refresh, API golden, and binary-source comparison issues; complete preparation is being repeated
from a clean corrected source commit.

The source branch `delivery/trustworthy-native-foundation` was pushed successfully. GitHub's
connector rejected draft PR creation with HTTP 403 (`Resource not accessible by integration`).
No PR, merge, release tag, registry publication or remote installation has been completed. Repository
App configuration and publication access remain external requirements; local build evidence does
not satisfy those requirements.
