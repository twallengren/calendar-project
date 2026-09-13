## Summary

<!-- What does this PR change and why? Link any related issue. -->

## Checklist

- [ ] Every holiday date I added or changed cites a source with an `id` from
      `sources/<MARKET>/README.md` (see `CONTRIBUTING.md`).
- [ ] `./gradlew :tools:run --args="validate --all --strict"` runs clean.
- [ ] Golden tests are updated if generated output changed
      (`./gradlew :tools:test -DupdateGoldens=true`, then reviewed by eye).
- [ ] Any `allowlist.csv` rows I added or removed are explained in this PR description, with a
      reason, not just "differs from reference".
- [ ] I understand the `ci-diff` severity this PR produces (`NONE`, `MINOR`, `MAJOR`) and, if it
      is not `NONE`, I have asked a maintainer to review the diff and add the
      `calendar-change-approved` label.

## Data changes (skip if this PR does not touch calendar data)

- Market(s) affected:
- Source(s) cited:
- Expected `ci-diff` severity and why:
