# Governance

## Project model

This project is maintainer-led overall, with per-market ownership for data. Maintainers own the
toolchain, the spec, and final decisions. Market owners own the accuracy of the calendars they
adopt.

## Maintainers

Maintainers are responsible for:

- Reviewing and merging pull requests that touch the tool, the spec, or shared modules.
- Arbitrating when a market has no owner, when owners disagree, or when a change crosses market
  boundaries (a shared module, a spec change, a new chronology).
- Adding the `calendar-change-approved` label after reviewing a `ci-diff` report of `MINOR` or
  `MAJOR` severity.
- Triaging issues and making release decisions.
- Ensuring CI, validation, and cross-validation stay meaningful as the dataset grows.

## Adopt a market

Each calendar can have one or more named owners, listed in `.github/CODEOWNERS`. An owner is a
domain expert for that market: someone who can judge whether a holiday date is right, knows
where to find the authoritative source, and is willing to review data PRs for it.

- Owners are automatically requested for review on any pull request that touches their market's
  calendar, module, or source files (as mapped in `.github/CODEOWNERS`).
- Owners review for accuracy and sourcing. Maintainers still review for spec conformance and
  merge the PR.
- To adopt a market, open an issue (see the "new-market" or a plain issue template) or comment on
  an existing one saying which calendar you want to own. A maintainer adds you to
  `.github/CODEOWNERS`.
- An unowned market is reviewed by maintainers directly; contributions to it are welcome and are
  not blocked on finding an owner first.
- Ownership is about review authority, not veto power. Maintainers can merge over an owner's
  objection if the owner is unresponsive, but should not do so routinely.

## Decision making

- Day-to-day data decisions (is this date right, is this source good enough) are made by the
  market's owner, or by maintainers when there is no owner.
- Decisions that affect the spec, the toolchain, or more than one market are made by maintainers,
  based on technical merit, and are discussed in an issue before implementation when the change
  is not obviously small.
- Pull requests require maintainer approval to merge. Market owner approval is expected for data
  changes to an owned market but does not substitute for maintainer sign-off.

## Contribution acceptance

Contributions are accepted when they:

1. Follow the conventions in `CONTRIBUTING.md` and the schema in `spec/SPEC.md`.
2. Cite an authoritative source for any calendar data.
3. Pass `validate --all --strict` and the test suite.
4. Include golden test updates when generated output changes.
5. Have a `ci-diff` severity a maintainer has reviewed and approved (see `CONTRIBUTING.md`).

## Code of conduct

See `CODE_OF_CONDUCT.md`. Maintainers enforce it and may ask a market owner to step back from
review for conduct reasons, independent of the data quality of their contributions.
