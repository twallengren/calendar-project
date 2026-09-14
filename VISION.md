# Vision

This document sketches the direction we see for this project — where it is today, where it could go, and the principles guiding that evolution.

## Where we are

A YAML-driven business-day calendar system that compiles declarative specs into deterministic CSV/JSON artifacts. The authenticated GitHub release asset for v11.0.0 contains four calendars; no v12 release has been published. The local source candidate has expanded beyond those published artifacts, so the README market table is a prepared local v12 candidate snapshot. Current candidate work includes:

- **Expanded calendar coverage**: Hebrew-based TASE rules, bounded to 2025–2027, plus EU-TARGET, GB-CHAPS and US-FEDWIRE date calendars for 2026–2027. TASE unscheduled-exception coverage remains incomplete throughout, so actual-day answers are UNKNOWN wherever a required scope is incomplete. Payment calendars mark scheduled closures VERIFIED and early closes and unscheduled exceptions PROJECTED.
- **Seven chronology profiles**: ISO/Gregorian, tabular Hijri, Umm al-Qura, Julian, Persian, fixed-arithmetic civil Hebrew, and a modern Chinese profile at fixed UTC+08:00. Hebrew dates map at civil midnight and do not model sunset; CHINESE_HK is bounded to 1929–2100 and is not a general historical profile. See [native chronology profiles](docs/native-chronologies.md).
- **A composable module system** for holidays, effective-dated weekend policies, per-holiday observance rules, multi-day spans, and early-close times. `shift_policy` governs `EARLY_CLOSE` events, and `displaces:` lets one CLOSED event claim priority over another when both want the same shifted slot.
- **Scope-specific provenance and confidence**: coverage quality is declared separately for scheduled closures, early closes and unscheduled exceptions. A missing required scope produces UNKNOWN actual-day assessments rather than an inferred open day. Event status can still distinguish confirmed from projected dates.
- **Financial date operations** in Java and Python: adjustment conventions, business-day offsets, month advancement and last-business-day lookup. Detailed results report confidence over the whole examined path, including unsuccessful initial searches for modified conventions and month-end checks. These are date-only operations, not instrument-specific settlement, cutoff or session rules.
- **A CLI toolchain**, including validation, resolution, generation, `query`, diffing, cross-validation, scaffolding, site generation and history. The generated v1 and v2 APIs carry event-oriented and explicit daily-assessment contracts; publication and deployed content must be verified independently.
- **Independent version streams**: Java software, bundled data, Python software and API wire-schema versions are tracked separately. The configured candidate versions do not imply publication; the current remote data release remains v11.0.0. See the README for local build guidance and remote-availability limits.

The architecture remains spec-driven, with inheritance and a clean separation between data and tooling. Candidate coverage and capabilities do not establish remote availability.

### Known limitations

Real gaps, not modesty:

- **Holiday-on-holiday cascades aren't fully general.** `displaces` handles one CLOSED event claiming a slot from another, but a shifted holiday landing on a date another holiday already occupies as an *explicit, non-shiftable* date still needs to be modelled by hand: HKEX's 2022 Christmas ("the second weekday after Christmas Day" falling on a Sunday-Christmas year) is still an `explicit_dates` entry rather than something the shift/`displaces` machinery derives (see `modules/holidays/hk_christmas.yaml`).
- **The `PROJECTED` convention isn't uniform across packs.** Every calendar gets `PROJECTED` for free past `verified_through`. A few packs (Saudi's `eid_al_fitr`/`eid_al_adha`, Japan's equinox-day holidays) additionally mark specific future rows `status: PROJECTED` *within* the verified range because the date itself is a best-effort computation (an observed new moon, an astronomical equinox) rather than a published fact; most packs rely on `verified_through` alone. A contributor reading one pack's YAML for the convention may reasonably not realize the other exists.
- **No business-day-relative rule type.** There is no way to express "the last business day of the month" or "three business days before quarter end" — every rule type resolves to a calendar date or a fixed offset from one. This has not blocked any market so far, but it will for some settlement-style calendars.
- **Native chronology profiles have bounded meanings.** HEBREW uses a fixed arithmetic civil-date mapping and CHINESE_HK uses a modern fixed-UTC+08:00 profile. Neither implies sunset-based observance or an unbounded historical model; Buddhist and Hindu profiles remain future work.
- **Session structure is out of scope.** Open times, lunch breaks (relevant for several Asian markets), and anything about the shape of a trading session beyond "closed" or "early close at time T" are not modelled and are not currently planned.
- **Serverless query API, npm package, and an Excel/Sheets add-on are deliberately deferred.** The distribution tiers they'd fill (Tier 3/4 in the brainstorming below) are real ideas, just not where the leverage was this iteration.

## Where we're heading

### More markets, more coverage

**Earlier implementation** added LSE, Deutsche Börse (Xetra), TSX, the four Euronext cash markets, JPX and HKEX, taking the source dataset to eleven market calendars. The local candidate adds TASE plus three payment calendars. Priority areas going forward:

- **Remaining major global exchanges** — BSE/NSE, ASX, SGX, KRX
- **Regional and emerging markets** — TWSE, JSE, B3, BMV
- **Non-exchange business calendars** — banking holidays, settlement calendars, government calendars

Each new market validates and stress-tests the composition model — this round produced the `displaces` observance-priority primitive and `EARLY_CLOSE` shifting directly from trying to model GB-LSE and CA-TSX honestly instead of special-casing them. Markets with unusual rules (e.g., lunar-calendar-driven closures, ad-hoc government declarations) are still especially valuable for surfacing gaps in the spec — HKEX's Christmas-on-Sunday case (still an `explicit_dates` entry; see *Known limitations* above) is the next one waiting for a general solution.

### Richer chronology support

The chronology system now includes bounded Hebrew and modern Chinese profiles in the local source
candidate, alongside the original five profiles. These profiles support native date rules while
retaining their stated scope limits; they do not replace market-specific source evidence. Buddhist
and Hindu profiles remain future work. The codegen pipeline should continue to make new profiles
addable through explicit providers, fixtures and range contracts.

### A community data commons

The most valuable thing this project can become is a **canonical, open, version-controlled dataset** of global business-day calendars. Think of it as:

- A single place to answer "is this date a business day in market X?"
- A shared resource that firms, developers, and researchers can depend on instead of maintaining private spreadsheets
- A dataset with provenance — every holiday cites its source, every change is tracked

For this to work, we need:

- **Low-friction contribution workflows** — clear templates, good validation tooling, CI that catches mistakes before merge
- **Authoritative sourcing standards** — every holiday backed by a gazette notice, exchange circular, or official calendar
- **Broad coverage with high accuracy** — better to have 50 markets at 99.9% accuracy than 200 markets full of guesses

### Better query and integration surfaces

The query APIs and Java/Python client implementations exist in the source tree, including joint
calendars and date-only financial operations. Local package builds do not establish registry
availability. The API generator emits v1 and v2; deployed data and endpoints must be checked
independently. The source candidate's v2 assessment contract supplements the event-oriented v1
contract, so existing clients can stay on v1 while consumers evaluate v2.

### Tooling and developer experience

As the dataset grows, the toolchain needs to keep pace:

- ~~Interactive calendar explorer — a local or hosted UI for browsing calendars, comparing markets, and visualizing holiday overlaps~~ — **done**: the public site's compare pages overlay two markets with a T+N settlement helper, and `tools serve --compare-to blessed` gives contributors the same view locally before opening a PR.
- ~~Diff and changelog generation — automated summaries of what changed between releases, useful for compliance and audit~~ — **done**: `ci-diff` (PR-time), the site's changelog page (release-time), and `history` (as-of queries) cover this.
- **Smarter validation** — detect common data-entry errors (duplicate holidays, missing shift policies, gaps in coverage ranges). Partially done: `validate --strict` now catches unsourced events, `displaces` cycles/unknown keys, `DROP` on a CLOSED source, and two CLOSED events on one date; broader data-entry heuristics (gaps in coverage ranges) are still open.
- **Performance at scale** — ensure the generator and resolver remain fast as the number of calendars and date ranges grows. Not yet a problem at 14 calendars; worth watching as coverage grows toward the remaining G10/regional markets.

## Principles

These guide how we make decisions:

1. **Data is the product.** The YAML specs and generated artifacts are what matter. Tooling exists to serve the data, not the other way around.

2. **Correctness over completeness.** A calendar with one wrong date is worse than no calendar at all. We'd rather ship fewer markets with verified data than rush to cover everything.

3. **Composition over configuration.** The module system exists so that shared rules (e.g., "Christmas is December 25") are defined once and composed everywhere. Duplication is a bug.

4. **Provenance matters.** Every holiday should trace back to an authoritative source. Undocumented data erodes trust.

5. **Determinism is non-negotiable.** Given the same specs and date range, the output must always be identical. No network calls, no ambient state, no surprises.

6. **Simple contribution, rigorous review.** Adding a new market should be easy. Getting it wrong should be hard. The toolchain enforces this.

## What we're not trying to be

- **Not a trading system.** We provide calendar data, not trading logic.
- **Not a real-time service.** Calendars change infrequently. We optimize for correctness and auditability, not latency.
- **Not a calendar UI.** We may build visualization tools, but the core product is structured data, not a user-facing application.

---

## Brainstorming: How do we become THE authoritative source?

### The competitive landscape

The data exists today, but it's fragmented and locked up:

- **Bloomberg, Refinitiv, ICE** — accurate but expensive, proprietary, and bundled with terminals nobody wants to buy just for holiday data
- **Internal spreadsheets** — every firm maintains their own. They drift, they conflict, they're maintained by whoever drew the short straw
- **quantlib/QuantLib** — has holiday calendars baked into C++ code, but they're hard-coded, no versioning, no provenance, painful to update
- **trading_calendars / exchange_calendars (Python)** — similar story, holidays as code, community-maintained but spotty coverage and no formal sourcing

The gap: there is no **open, structured, version-controlled, source-cited** dataset of global business-day calendars. That's the position to own.

### What "authoritative" actually means

People won't switch from their spreadsheets because we have a nicer YAML format. They'll switch when:

1. **We're more accurate than what they maintain themselves.** This means aggressive verification — cross-referencing exchange circulars, gazette notices, and multiple independent sources. We should publish our accuracy methodology and make it auditable.

2. **We're faster to update.** When an exchange announces a surprise closure (e.g., a national day of mourning), we should have the update merged within hours, not weeks. This requires an active contributor community and streamlined PR workflows.

3. **We're easier to consume than to replicate.** If it takes 5 minutes to integrate our data and 5 days to build your own, the choice is obvious. This is about packaging and distribution (see below).

4. **We have institutional credibility.** This could come from adoption by a few visible firms, endorsement from exchange data teams, or academic citation. Early adopters matter enormously.

### Building trust at scale

- **Per-market accuracy scorecards** — publish coverage dates, known gaps, last-verified dates, and source links for every calendar. Make the quality visible.
- **Automated cross-validation** — compare our outputs against exchange_calendars, QuantLib, and any other public source. Flag discrepancies. Publish the comparison.
- **Errata and corrections log** — when we get something wrong, document it publicly. Transparency builds more trust than perfection.
- **Immutable release history** — the bitemporal model we already have is a huge differentiator. Firms care about "what did we think was correct on date X?" for audit and compliance.

### The contributor flywheel

Authoritative sources don't just have good data — they attract good contributors. The flywheel:

1. Cover a market accurately with good sources
2. Someone at a firm using that market discovers us, finds a minor error, submits a fix
3. They tell a colleague who covers a different market, that person contributes a new calendar
4. More coverage attracts more users attracts more contributors

To make this work:

- **"Adopt a market" program** — explicitly invite domain experts to own specific markets. Give them credit, give them review authority.
- **Contribution templates** — `./gradlew scaffold --market HKEX` that generates the YAML skeleton, test fixtures, and PR template
- **Validation that catches mistakes before review** — CI should reject holidays that fall on weekends without a shift policy, flag dates that don't match known references, warn about gaps in coverage
- **Fast feedback loops** — contributors should see generated outputs in PR comments, not after merge

---

## Brainstorming: Features and tooling for broader reach

### Who are the audiences beyond quant devs?

The "is this a business day?" question gets asked far beyond trading desks:

- **Operations and settlement teams** — need to know T+1/T+2 settlement dates across jurisdictions
- **Payroll and HR** — bank holiday calculations for compensation, PTO
- **Logistics and shipping** — port closures, customs office schedules
- **Legal and compliance** — contractual deadlines that reference "business days"
- **Retail and e-commerce** — delivery date estimation
- **Government and public sector** — agency schedules, filing deadlines

Each audience has different consumption patterns. A quant dev wants a Maven dependency. An ops team wants an Excel plugin or API. A logistics planner wants an iCal feed.

### Distribution tiers

Think of this in layers, from lowest friction to richest integration:

**Tier 0: Raw data (we have this)**
- CSV/JSON artifacts on GitHub releases
- Good for: developers who can wrangle files

**Tier 1: Package registries**
- Published to Maven Central, npm, PyPI, crates.io as versioned data packages
- Thin wrapper libraries: `isBusinessDay("US-NYSE", date)`, `nextBusinessDay(...)`, `businessDaysBetween(...)`
- Good for: application developers embedding calendar logic

**Tier 2: Static API / hosted data**
- A static site (GitHub Pages or similar) serving the JSON artifacts behind clean URLs
- `GET /v1/calendars/US-NYSE/2026.json`
- No server to maintain — just generated files behind a CDN
- Good for: lightweight integrations, scripts, non-Java/Python consumers

**Tier 3: Query service**
- A thin API layer for computed queries: "what are the next 5 business days after Feb 20 in HKEX?"
- Could be serverless (Lambda/CloudFlare Workers) to keep ops burden near zero
- Good for: ops teams, non-technical consumers, chatbots, internal tools

**Tier 4: Feeds and subscriptions**
- iCal feeds per market (subscribe in Outlook/Google Calendar)
- Webhook notifications when a calendar changes (surprise holiday announced)
- RSS/Atom feed for changelog
- Good for: anyone who just wants to see holidays on their calendar

### Developer experience features

- **`npx bdc-calendar query US-NYSE --date 2026-03-15`** — zero-install CLI for quick lookups
- **GitHub Action** — `uses: bdc/calendar-check@v1` that validates your app's assumptions about business days in CI
- **Excel/Google Sheets add-on** — `=IS_BUSINESS_DAY("US-NYSE", A1)` — this alone could drive massive adoption in ops/finance teams
- **Slack/Teams bot** — `/calendar US-NYSE next-holiday` — low-friction discovery

### Settlement and multi-market calculations

A killer feature that nobody does well in open source:

- **Cross-market settlement date calculation** — "T+2 where T is a business day in both US-NYSE and LSE"
- **Holiday overlap analysis** — "which days is market X open but market Y closed?"
- **Joint calendar generation** — compose arbitrary markets into a combined calendar

This is where the composition model really shines. Most existing tools can tell you holidays for one market. Answering questions across markets requires exactly the kind of structured data we're building.

---

## Brainstorming: Do we need a UI?

### The case for a UI

- **Discovery** — people find a website before they find a GitHub repo. A UI is how non-developers encounter the project.
- **Verification** — contributors need to see what their YAML changes produce. A visual calendar view is more intuitive than scanning CSV diffs.
- **Credibility** — a polished explorer signals that this is a real project, not a side hobby.
- **Cross-market comparison** — visualizing "when is the US open but Japan closed?" is much better as a calendar heatmap than a table.

### The case against a UI

- **Maintenance burden** — frontends rot faster than data. A React app from 2024 is already legacy.
- **Distraction** — the core value is the data and toolchain, not pixels. Every hour on UI is an hour not spent on market coverage.
- **Scope creep magnet** — once a UI exists, feature requests multiply. Filters, search, export, themes, mobile, accessibility...

### A middle path: generated static site

Build a **static site generated from the artifacts**, not a separate application:

- The build pipeline already produces JSON. Add a step that generates HTML pages from it.
- One page per market showing a year-view calendar grid with holidays highlighted.
- A comparison page that overlays two markets.
- A changelog page generated from release-history diffs.
- No server, no framework, no state management. Just HTML + CSS + minimal JS.
- Hosted on GitHub Pages. Updated automatically on each release.

This gives us discovery, verification, and credibility without the maintenance burden of a real application. If it outgrows static generation, we can add interactivity later.

### Contributor-facing UI vs. consumer-facing UI

These are different things:

- **Contributor UI** = "I edited a YAML file, show me what it produces." This could be a local dev server (`./gradlew serve`) that renders the resolved calendar. High value, low scope.
- **Consumer UI** = "I want to browse all markets and find holidays." This is the static site described above. Medium value, medium scope.
- **Product UI** = "I want to build workflows around business-day logic." This is a SaaS product and explicitly not what we're building (yet?).

The contributor UI is probably the highest-leverage investment. If contributing is pleasant, the data improves. If the data improves, everything else follows.

---

## Prioritized roadmap (draft)

Ranked by leverage — what most accelerates everything else. This roadmap is a draft and predates the
current local source candidate. Candidate implementation does not by itself complete a release gate.

### 0. Release and distribution state

The source candidate and package-version configuration may be built and tested locally, but remote
availability should only be stated after the corresponding release and distribution steps have
completed. Keep generated candidate artifacts, package versions, wire-schema versions and remote
publication status distinct.

**Done when:** a reviewed release has been built from prepared candidate artifacts and its published
channels have been verified directly.

### 1. Market coverage: the G10 exchanges — mostly done
**Implemented in the source dataset:** LSE, Deutsche Börse (Xetra), TSX, Euronext
Paris/Amsterdam/Brussels/Lisbon, JPX, and HKEX — 9 additional market calendars. Each is sourced
and cross-validated against exchange_calendars and/or QuantLib (see the
[Market status table](README.md#market-status)).

**Still open:** BSE/NSE, ASX, SGX, KRX from the original G10 list.

**Done when:** 10+ markets with full holiday coverage, sourced and cross-validated. *(Met — 11.)*

### 2. Contribution scaffolding and validation — done
**Shipped:** `scaffold --market <ID> --name <name> --timezone <tz> --mic <mic>` generates the
calendar YAML, a holiday group module, an example holiday, and a `sources/<MARKET>/README.md`
citation table, and appends the new calendar to `blessed/manifest.json` and the cross-validation
export list. `CONTRIBUTING.md` is a full "add a market in an afternoon" walkthrough. CI posts a
`ci-diff` PR comment plus a downloadable `site-preview` artifact showing the rendered calendars.
`GOVERNANCE.md` + `.github/CODEOWNERS` implement the "adopt a market" ownership model, with all 9
new markets already carrying an owner.

**Done when:** A knowledgeable contributor can add a new market in an afternoon without reading the
full spec. *(Met, by design — untested against an actual outside contributor yet.)*

### 3. Language-native client libraries (Tier 1 distribution) — partly done
**Implemented locally:** the Python package, Java core/data modules and optional Python MCP server.
Their implementation and configured software versions do not establish remote package publication.
TypeScript/npm remains deferred.

**Done when:** a package is published and a clean consumer environment can install it; the local
source package can be installed with `pip install ./python`.

### 4. Cross-validation and accuracy infrastructure — done
**Shipped:** `tools crossvalidate --all` compares every calendar with reference data against
exchange_calendars and (where available) QuantLib exports, writing
`blessed/<ID>/cross_validation.json`; `tools status --format markdown` renders the accuracy
scorecard reproduced in `README.md`'s Market status table (coverage, verified-through, closures,
early closes, projected count, sources cited, cross-validation result); disagreements are tracked
in a per-calendar `allowlist.csv` that requires a stated reason and fails when stale.

**Still open:** no nightly/scheduled CI job re-runs cross-validation independent of a PR touching
the calendar; no public errata log beyond the release changelog and git history.

**Done when:** Every market page shows a confidence score and a list of corroborating sources.
*(Met — the per-market site page and README table both show this.)*

### 5. Static site (consumer-facing) — implemented locally

`tools site` generates `/v1/` and `/v2/` API output, HTML pages, market comparisons, source
registers and changelog. Deployed content and API availability must be verified independently.

**Done when:** the deployed site is verified and consumers can browse a market and share a date permalink.

### 6. Contributor UI (local dev server) — done
**Shipped:** `tools serve --dir <site-dir>` is a zero-dependency static file server (JDK
`HttpServer`) for previewing site output; `tools site --blessed-dir generated --compare-to blessed
--out site-preview` renders a contributor's local `generate --include-specs` output with a
"Changes vs blessed" banner and a `/changes/` page, matching exactly what the CI `site-preview`
artifact will show on the PR.

**Done when:** Contributors can visually verify their changes without running the full generate
pipeline and inspecting CSVs. *(Met.)*

### 7. Additional chronologies — partially implemented

The local candidate includes bounded HEBREW and CHINESE_HK profiles and native-date rules. These do
not remove the need for authoritative date evidence: HKEX uses cited overrides where HKO dates differ
from the ICU profile, while TASE actual-day confidence remains UNKNOWN wherever a required scope is
incomplete. Buddhist and Hindu profiles remain future work.

**Done when:** supported profiles have independent conversion fixtures, explicit range contracts and
consumer parity; remaining market-specific confidence limits are documented.

### 8. Cross-market settlement and financial date operations — implemented locally
**Implemented:** the Query API's joint-calendar constructor (`is_business_day` true only when every
member trades; `verified_through`/`status` take the worst member's value), exposed through the CLI
(`query A,B --is-business-day`), the Python package (`get_joint_calendar`), financial adjustment/offset/month-end operations, and the site's compare
pages via a browser-side reimplementation (`site.js`) that is fixture-tested for parity against the
Java reference (`SettlementParityTest`).

**Done when:** You can ask "what's the T+2 settlement date for a trade on Feb 27 across NYSE and
LSE?" and get the right answer. *(Met — via CLI, Python, MCP tool, or the site's settlement form.)*

### 9. Feeds and API (Tier 2-4 distribution) — implemented locally

The local build generates v1 and v2 API outputs. Do not infer deployed contents from these files;
verify publication and hosting state independently.

**Still open:** webhook/RSS notification on calendar change, a Slack/Teams bot, a GitHub Action,
and an Excel/Sheets add-on — all still in the parking lot below.

**Done when:** published endpoints and feed contents have been verified independently, and non-developer
consumers can access their intended calendar data without touching code.

### Parking lot (not prioritized yet)

These are good ideas but don't have a clear slot yet:

- Serverless query API (Tier 3) — depends on demand signal
- npm/TypeScript package — no consumer has asked yet
- Excel/Google Sheets add-on — potentially huge reach but different skillset to build
- Webhook/RSS for calendar change notifications, Slack/Teams bot, GitHub Action
- Non-exchange calendars (banking, government, settlement) — natural extension but different sourcing challenges
- Commercial support model — premature until there's meaningful adoption

---

## Open questions

- **Licensing model** — *resolved*: Apache-2.0 for the toolchain (`LICENSE`), CC0 for the data (`DATA_LICENSE`), as stated in `python/README.md`'s provenance section. Whether that combination affects institutional adoption at scale is still open.
- **Governance as we scale** — partially addressed: `GOVERNANCE.md` and `.github/CODEOWNERS` give per-market ownership ("adopt a market") now, exercised for all 11 current markets. Whether that model holds at 50+ markets, or needs a steering committee or per-region maintainers, is untested.
- **Commercial sustainability**: If firms depend on this data, is there a model (support contracts, hosted API, early access to updates) that funds ongoing maintenance without compromising the open-source core?
- **Relationship with exchanges**: Should we engage exchange data teams directly? Some might contribute or endorse. Others might see us as competition to their data products.
- **Scope boundaries** — partially answered by this iteration's *Known limitations*: early-close times and shortened sessions are in scope and modelled (`close_time`, `shift_policy` for `EARLY_CLOSE`); full session/open times and lunch breaks are explicitly out of scope. Whether that line moves for markets where "half day" means something more structured (a different published session schedule, not just a close time) is still open.

## How to get involved

If any of this resonates, look at the open issues, pick a market you know well, or propose a chronology. The best contributions come from people with domain knowledge of the calendars they're adding.

- **Adding a market**: [CONTRIBUTING.md](CONTRIBUTING.md) walks through adding a market in an afternoon, from directory conventions and source citation through validation, goldens, cross-validation, and the release process.
- **Reporting a wrong date or a source change**: open an issue with [.github/ISSUE_TEMPLATE/data-correction.yml](.github/ISSUE_TEMPLATE/data-correction.yml) or [.github/ISSUE_TEMPLATE/source-update.yml](.github/ISSUE_TEMPLATE/source-update.yml).
- **Proposing a new market**: open an issue with [.github/ISSUE_TEMPLATE/new-market.yml](.github/ISSUE_TEMPLATE/new-market.yml) before writing YAML, especially for a market with unusual rules (lunar closures, ad-hoc government declarations) that might need a spec change first.
- **Adopting a market**: markets can have a named owner who reviews data PRs for that market, per [GOVERNANCE.md](GOVERNANCE.md#adopt-a-market). Say so on a new-market or data-correction issue if you want to take on ownership.
- **Code contributions**: see the "Code contributions" section of [CONTRIBUTING.md](CONTRIBUTING.md) for the Java toolchain conventions.
