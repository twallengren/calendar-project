# Native calendars and financial operations delivery ledger

The delivery plan supplied on 2026-09-14 is the acceptance contract. A local implementation or a passing test is not evidence of publication. Published history is immutable. The incident source commit is `8d95201`; the corrected source commit must be recorded separately after review.

| Work package | Owner | Dependencies | Implementation / evidence | Review / release |
|---|---|---|---|---|
| Release comparison, PR preparation and exact-commit publication | Sol release agent | Actual published v11 baseline | In progress in isolated worktree | Pending independent review; no publication |
| Cross-validation multiplicity and close times | Astra orchestrator | Existing reference contract | Complete multiset comparison; precise close-time allowlists; published scratch CSV unchanged before audit corrections | Terra reviewed; full build passed; not released |
| Historical publication bounds | Sol history agent | Evidence, never inferred from archive timestamp | Integrated b8e2795; legacy snapshots accessible by version, no invented date lower bound | Focused tests pass; independent review pending |
| Coverage audit and safe unknown assessments | Sol history agent | Authoritative sources, additive consumer contracts | All existing calendars have explicit per-scope intervals; NYSE 1975 early close corrected; dangerous gaps are UNKNOWN | Terra reviewed consumer safety; malformed metadata and ambiguous source IDs fixed; not released |
| API links and browser execution | Astra orchestrator | Existing /v1 contract | Relative JSON/ICS links; actual Chrome root and project-prefix execution; quality-aware browser traversal | Link fixes reviewed; enriched behavior under verification |
| Native chronology providers and Hebrew rules | Astra chronology agent | ICU pin, conversion fixtures, range contract | Integrated ac52157 and 50fd327; ICU78.3 Hebrew, explicit month identity, conversions, bounded expansion, codegen verification and Persian profile correction | Terra independently reviewed and ran focused tests; full build passed |
| Canonical source register and /v2 | Astra integration | Coverage / provenance contracts | Canonical JSON tables and checksums; native event provenance compiled and consumed occurrence-by-occurrence; v2 daily assessments and CLI | Java/Python focused regressions passing; full verification/review pending |
| TASE integration | Sol TASE agent | Hebrew provider acceptance | Native Hebrew rules, 2026 weekend transition and bounded authoritative schedules integrated; unsupported early closes removed | Root reviewed primary PDFs and CLI/Python/MCP representation/v2/HTML parity; actual state explicitly unknown where exception coverage is incomplete; not released |
| Chinese/HKEX integration | Astra design / Sol implementation | Bounded TASE gate and regional profile | Design fixed in CHINESE_HK-DESIGN.md; implementation started in isolated worktree | Not released |
| Financial operations and payment calendars | Integration | Both native-market gates | Pending | Not released |
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
