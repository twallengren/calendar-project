# Complete event comparisons

The CI diff engine previously retained only the first event on each date. A baseline containing A
and generated output containing A and B on that date could incorrectly report no changes. The
artifact `diff generated` command independently discarded identical duplicate rows by using sets.

Both comparisons now preserve occurrence counts. CI cancels exact matches by date, type, and
description before reporting changes. A single remaining old/new pair on a date is a modification;
ambiguous groups are individual additions and removals. Artifact diffs compare every decoded CSV
field, including alternate dates, and keep their additions/removals display.

A shared reader uses Jackson CSV 2.17.0, matching the existing Jackson dependency versions. It
handles quoted multiline descriptions, column names, BOMs, and duplicate records. Invalid records
now fail with file and logical record context instead of disappearing from comparisons. Markdown
descriptions escape pipes and line breaks so each displayed occurrence remains one table row.
The [specification](../spec/SPEC.md#artifact-comparison) describes matching, parsing, and exit codes.

## Verification

The preceding commit's [CI run](https://github.com/twallengren/calendar-project/actions/runs/34269578421)
completed successfully before implementation.

`JAVA_HOME=/home/torenwallengren/.sdkman/candidates/java/21.0.9-tem ./gradlew :tools:spotlessApply build :tools:installDist --offline`
passed with **542 tests, zero failures, and zero skipped tests**. New regressions cover shared-date
changes, duplicate multiplicity, conservative matching, CSV round trips and malformed input,
command exit codes, and JSON/Markdown counts. A 200-scenario property test checks permutation
invariance, exact multiset equality, and reconstruction from the reported changes.

All four published ranges were regenerated into `build/diff-completeness/` and independently
compared as complete decoded CSV record multisets. Headers matched and no rows were added or
removed:

| Calendar | Inclusive range | Rows |
|---|---|---:|
| US-MARKET-BASE | 1900-01-01 – 2030-12-31 | 14,502 |
| US-NYSE | 1900-01-01 – 2030-12-31 | 15,394 |
| US-CORP-IN-VISIBILITY | 1900-01-01 – 2030-12-31 | 14,507 |
| SA-TADAWUL | 2020-01-01 – 2030-12-31 | 1,255 |

The updated `ci-diff --calendars all --output-format json` returned exit code **0**, severity
**NONE**, and empty change lists for every calendar. It exposed no additional differences in the
current published artifacts. No existing golden expectations, blessed artifacts, or release-history
files changed; no release was published.
