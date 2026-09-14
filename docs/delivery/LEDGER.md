# Native calendars and financial operations delivery ledger

The delivery plan supplied on 2026-09-14 is the acceptance contract. A local implementation or a passing test is not evidence of publication. Published history is immutable. The incident source commit is `8d95201`; the corrected source commit must be recorded separately after review.

| Work package | Owner | Dependencies | Implementation / evidence | Review / release |
|---|---|---|---|---|
| Release comparison, PR preparation and exact-commit publication | Sol release agent | Actual published v11 baseline | In progress in isolated worktree | Pending independent review; no publication |
| Cross-validation multiplicity and close times | Astra orchestrator | Existing reference contract | In progress | Pending review |
| Historical publication bounds | Sol history agent | Evidence, never inferred from archive timestamp | In progress | Pending review |
| Coverage audit and safe unknown assessments | Sol history agent | Authoritative sources, additive consumer contracts | In progress | Pending review |
| API links and browser execution | Astra orchestrator | Existing /v1 contract | In progress | Pending review |
| Native chronology providers and Hebrew rules | Astra chronology agent | ICU pin, conversion fixtures, range contract | In progress | Pending review |
| Canonical source register and /v2 | Integration | Coverage / provenance contracts | Pending | Not released |
| TASE integration | Integration | Hebrew provider acceptance | Pending authoritative data and tests | Not released |
| Chinese/HKEX integration | Integration | TASE gate and regional profile | Pending | Not released |
| Financial operations and payment calendars | Integration | Both native-market gates | Pending | Not released |
| Recovery packages and remote installation | Release / verification | Milestone 1 review, CI exact SHA, credentials | Pending | Not published |

## Gates

1. Complete published-to-candidate release report; dangerous answers corrected or explicitly unknown; retry conflicts fail closed.
2. Native rules preserve range consistency; missing evidence and failed conversion cannot become successful answers.
3. TASE native rules, authoritative examples and effective weekends work through consumers before HKEX integration.
4. HKEX and financial conventions/payment calendars pass Java/Python/CLI/JSON/browser/MCP checks.

Independent verification includes full Gradle build, strict validation, cross-validation, scratch artifact counts/diffs, Python package and browser execution. Golden changes require explanations. Account or credential setup is an external blocker, never a completed release.
