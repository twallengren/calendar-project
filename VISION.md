# Vision

This document sketches the direction we see for this project — where it is today, where it could go, and the principles guiding that evolution.

## Where we are

A YAML-driven business-day calendar system that compiles declarative specs into deterministic CSV/JSON artifacts. What was a two-market proof of concept has grown into a small but real dataset with multiple distribution channels. Today it covers:

- **11 market calendars** (US-NYSE, SA-TADAWUL, GB-LSE, DE-XETRA, CA-TSX, the four Euronext cash markets — Paris, Amsterdam, Brussels, Lisbon — JP-JPX, and HK-HKEX) **plus 3 base calendars** (US-MARKET-BASE, US-CORP-IN-VISIBILITY, EU-EURONEXT). See the [Market status table](README.md#market-status) for coverage, verified-through dates, closure/early-close/projected counts, sources cited, and cross-validation results per calendar — regenerate it with `tools status --format markdown`.
- **Five chronologies** (ISO/Gregorian, tabular Hijri, Umm al-Qura, Julian, Persian) — unchanged in count since the last iteration; every new market this round used published/gazetted dates rather than a new chronology (see *Known limitations* below).
- **A composable module system** for holidays, effective-dated weekend policies, per-holiday observance rules, multi-day spans, and early-close times. Two new primitives came out of modelling GB-LSE and CA-TSX honestly instead of enumerating their consequences: `shift_policy` now also governs `EARLY_CLOSE` events (`DROP`, the default, and `PREVIOUS_AVAILABLE_BUSINESS_DAY`), and `displaces:` lets one `CLOSED` event claim priority over another when both want the same shifted slot (so Canada's "Boxing Day pushed by a Sunday Christmas" is one `displaces` declaration instead of sixteen calendar-level deltas). Full shift policy set: `NONE`, `NEAREST_WEEKDAY`, `NEXT_AVAILABLE_WEEKDAY`, `FORWARD_ONLY`, `NEXT_AVAILABLE_FROM_LAST_WEEKEND_DAY` for full closures.
- **Provenance**: every holiday cites a document under `sources/`, and `validate --strict` rejects an uncited event source. Every event carries a `CONFIRMED`/`PROJECTED` status, and calendars declare `verified_through` — dates after it are reported as projected regardless of what the row itself says.
- **Cross-validation** against exchange_calendars and QuantLib, with an explicit, reason-required allowlist of explained differences; nine of the eleven market calendars have at least one reference source (the four Euronext venues and SA-TADAWUL currently cross-validate against exchange_calendars only).
- **Bitemporal artifact versioning** with blessed outputs, release history, and as-of queries.
- **A CLI toolchain**, expanded well beyond validation/resolution/generation: `diff`/`ci-diff` for PR-time comparisons, `query` (single and **joint** calendars — "is this a business day in both A and B", T+N settlement across markets), `status` (the scorecard above), `crossvalidate`, `scaffold` (onboard a new market), `site`/`serve` (build and preview the public site), and `history`.
- **A published distribution surface**, not just a repo to clone: a static [`/v1/` JSON API](spec/SPEC.md#json-api-v1) and per-calendar `.ics` feeds, both served from GitHub Pages; the same data mirrored on jsDelivr (pinned per tag) and as loose files on every GitHub Release; a zero-dependency [`bdc-calendars`](python/README.md) Python package with an MCP server extra so an agent can query calendars over stdio (Java library modules — `bdc-calendar-core`/`bdc-calendar-data`, wrapping this same data for JVM consumers — are in progress on branch `wp2b`, not yet on `main`); and a browsable HTML site (year grids, permalinked dates, market comparison pages with a T+N settlement helper, rendered source registers, a changelog) generated from that same JSON API rather than from `blessed/` directly.
- **A contributor kit** meant to make "adding a market" a real afternoon task rather than folklore: `scaffold` generates the calendar YAML, a holiday group, an example module and the source citation table; `CONTRIBUTING.md` walks the rest end to end; PR/issue templates, `CODEOWNERS` and `GOVERNANCE.md` give per-market ownership somewhere to live; a `justfile` and pre-commit hook cut friction; and `tools site --compare-to blessed` plus `tools serve` let a contributor preview exactly what a reviewer's PR comment and site-preview artifact will show before opening the PR.

The architecture is sound: spec-driven, inheritance-based, with clean separation between data and tooling. The foundation is built for growth — this iteration mostly proved that by growing it.

### Known limitations

Real gaps, not modesty:

- **Holiday-on-holiday cascades aren't fully general.** `displaces` handles one CLOSED event claiming a slot from another, but a shifted holiday landing on a date another holiday already occupies as an *explicit, non-shiftable* date still needs to be modelled by hand: HKEX's 2022 Christmas ("the second weekday after Christmas Day" falling on a Sunday-Christmas year) is still an `explicit_dates` entry rather than something the shift/`displaces` machinery derives (see `modules/holidays/hk_christmas.yaml`).
- **The `PROJECTED` convention isn't uniform across packs.** Every calendar gets `PROJECTED` for free past `verified_through`. A few packs (Saudi's `eid_al_fitr`/`eid_al_adha`, Japan's equinox-day holidays) additionally mark specific future rows `status: PROJECTED` *within* the verified range because the date itself is a best-effort computation (an observed new moon, an astronomical equinox) rather than a published fact; most packs rely on `verified_through` alone. A contributor reading one pack's YAML for the convention may reasonably not realize the other exists.
- **No business-day-relative rule type.** There is no way to express "the last business day of the month" or "three business days before quarter end" — every rule type resolves to a calendar date or a fixed offset from one. This has not blocked any market so far, but it will for some settlement-style calendars.
- **No lunisolar chronologies.** HKEX and (when added) TASE-style markets are handled with published/gazetted date lists, not computed lunar or lunisolar arithmetic. Chinese, Hebrew, Buddhist and Hindu lunisolar support remains chronology work we have not needed to do yet.
- **Session structure is out of scope.** Open times, lunch breaks (relevant for several Asian markets), and anything about the shape of a trading session beyond "closed" or "early close at time T" are not modelled and are not currently planned.
- **Serverless query API, npm package, and an Excel/Sheets add-on are deliberately deferred.** The distribution tiers they'd fill (Tier 3/4 in the brainstorming below) are real ideas, just not where the leverage was this iteration.
- **Two maintainer-only setup steps stand between what's built and what's live**, listed in full under [Prioritized roadmap](#prioritized-roadmap-draft): enabling GitHub Pages, and registering the PyPI trusted publisher / Sonatype namespace for the packages that are otherwise ready to publish.

## Where we're heading

### More markets, more coverage

**Progress this iteration:** LSE, Deutsche Börse (Xetra), TSX, the four Euronext cash markets, JPX and HKEX all shipped, taking the previous two-market base to 11 market calendars. That is most of the original "G10 exchanges" target below. Priority areas going forward:

- **Remaining major global exchanges** — BSE/NSE, ASX, SGX, KRX
- **Regional and emerging markets** — TWSE, JSE, B3, BMV
- **Non-exchange business calendars** — banking holidays, settlement calendars, government calendars

Each new market validates and stress-tests the composition model — this round produced the `displaces` observance-priority primitive and `EARLY_CLOSE` shifting directly from trying to model GB-LSE and CA-TSX honestly instead of special-casing them. Markets with unusual rules (e.g., lunar-calendar-driven closures, ad-hoc government declarations) are still especially valuable for surfacing gaps in the spec — HKEX's Christmas-on-Sunday case (still an `explicit_dates` entry; see *Known limitations* above) is the next one waiting for a general solution.

### Richer chronology support

The chronology system is designed for extensibility but remains only lightly exercised: still five chronologies, same as before this iteration. HKEX was added using gazetted date lists rather than new chronology work, which is the right call for one market but defers the underlying problem. We want to:

- **Add Hebrew, Buddhist, Hindu, and Chinese lunisolar calendars** — critical for markets in Israel, Thailand, India, and East Asia. Still not started; this is now the highest-leverage chronology work because HKEX has made the "published dates, not computed lunar arithmetic" workaround visible as a real limitation rather than a hypothetical one.
- **Support observation-based calendars** where dates are declared by authority rather than computed by formula (the lookup-table mechanism exists but needs more real-world use)
- **Improve the codegen pipeline** so contributing a new chronology is as simple as writing a YAML file

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

**Progress this iteration:** this section is largely done for two of three registries. `bdc-calendars` is on PyPI's on-ramp (published via Trusted Publishing once a maintainer flips `PYPI_PUBLISH`); the JSON API, `.ics` feeds and jsDelivr mirroring are live. What remains:

- ~~Publish artifacts to package registries — Maven Central, npm, PyPI~~ — **PyPI**: package built, publish gated on a maintainer registering the trusted publisher (see roadmap). **Maven Central**: `bdc-calendar-core`/`bdc-calendar-data` exist on branch `wp2b`, not yet merged, and publishing is further gated on claiming the `io.github.twallengren` Sonatype namespace. **npm**: not started, deliberately deferred — no TypeScript/JS consumer has asked yet.
- ~~Provide a lightweight query API (or at least a static site) for ad-hoc lookups~~ — **done**: the `/v1/` JSON API and the generated HTML site both exist and are described under *Where we are*.
- ~~Support common integration patterns — iCal feeds, JSON API contracts, embeddable widgets~~ — iCal and the JSON API contract are done; embeddable widgets are not started.
- **Offer language-native libraries** that wrap the generated data with idiomatic APIs (e.g., `isBusinessDay(market, date)`) — done for Python (`is_business_day`, `next_business_day`, `add_business_days`, plus joint calendars and an MCP server); Java is in progress (`wp2b`); TypeScript is not started.

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

Ranked by leverage — what most accelerates everything else. Original priorities #1-#6, #8 and most
of #9 shipped this iteration; each is marked below with what actually landed. The unstarted work
(#7, part of #3, part of #9) and two blocking maintainer chores now lead the list.

### 0. Maintainer setup (blocking, not building)
**Why zeroth:** Two of the things below are fully built and merged but not live, purely because
they need a one-time action only a repository maintainer can take. Nothing else on this list
depends on these, but until they happen, "we ship a public JSON API" and "we publish to PyPI" are
aspirational, not true.

- **Enable GitHub Pages**: Settings → Pages → Source: GitHub Actions. Until this is flipped,
  `pages.yml` runs on every qualifying push and its deploy step fails harmlessly — nothing else in
  the release is affected, but the `/v1/` API and site are not reachable at the published URL.
- **Register a PyPI trusted publisher** for the `bdc-calendars` project (pointed at this repo and
  `release.yml`) and set the `PYPI_PUBLISH` repository variable to `true`. Until then,
  `publish-python` in `release.yml` is skipped and `pip install bdc-calendars` installs nothing,
  even though the package is built, tested, and parity-checked on every CI run.
- **Claim the Sonatype `io.github.twallengren` namespace** and set `MAVEN_PUBLISH`, once
  `bdc-calendar-core`/`bdc-calendar-data` (branch `wp2b`) merge — a prerequisite for #3 below, not
  yet actionable on `main`.

**Done when:** the Pages URL in `README.md` resolves, and `pip install bdc-calendars` on a clean
machine works without a manual wheel.

### 1. Market coverage: the G10 exchanges — mostly done
**Shipped:** LSE, Deutsche Börse (Xetra), TSX, Euronext Paris/Amsterdam/Brussels/Lisbon, JPX, and
HKEX — 9 new market calendars, taking total coverage from 2 to 11 markets plus 3 base calendars.
Each is sourced and cross-validated against exchange_calendars and/or QuantLib (see the
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
**Shipped:** the Python package (`bdc-calendars`) — zero runtime dependencies, exchange_calendars
alias compatibility, joint calendars, and an optional `bdc-calendars-mcp` MCP server so an AI agent
can query it over stdio. Blocked from PyPI only by the maintainer step in #0.

**Still open:** Java library modules (`bdc-calendar-core`/`bdc-calendar-data`) exist on branch
`wp2b` but have not merged to `main`; Maven Central publish additionally needs the Sonatype
namespace claim in #0. TypeScript/npm is not started — deliberately deferred, no demand signal yet.

**Done when:** `pip install bdc-calendars` gives you a working
`is_business_day("US-NYSE", date(2026, 7, 4))` → `False`. *(Met for Python once PyPI publishing is
turned on; not yet met for Java or TypeScript.)*

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

### 5. Static site (consumer-facing) — done
**Shipped:** `tools site` generates the full public site from the `/v1/` JSON API (not from
`blessed/` directly, so the site exercises the same contract external consumers depend on):
per-market pages, per-year month grids, per-date permalinks, market comparison pages with a T+N
settlement helper, rendered source registers, and a changelog. Deployed to GitHub Pages by
`pages.yml` — pending the maintainer step in #0.

**Done when:** You can browse any market's holidays in a browser and share a permalink to a
specific date. *(Met, pending Pages being switched on.)*

### 6. Contributor UI (local dev server) — done
**Shipped:** `tools serve --dir <site-dir>` is a zero-dependency static file server (JDK
`HttpServer`) for previewing site output; `tools site --blessed-dir generated --compare-to blessed
--out site-preview` renders a contributor's local `generate --include-specs` output with a
"Changes vs blessed" banner and a `/changes/` page, matching exactly what the CI `site-preview`
artifact will show on the PR.

**Done when:** Contributors can visually verify their changes without running the full generate
pipeline and inspecting CSVs. *(Met.)*

### 7. Additional chronologies — not started
**Why still not higher:** driven by market demand, same as before — but HKEX joining without a
lunisolar chronology (it uses published gazette dates instead; see *Known limitations*) means the
workaround is now visible in a shipped calendar rather than theoretical. This raises its priority
for the next iteration, especially if a Chinese or Israeli market is next.

- Hebrew (for TASE)
- Chinese lunisolar (for SSE, SZSE, and a more general HKEX lunar-holiday model)
- Buddhist (for SET)
- Hindu (for BSE/NSE Diwali, etc.)

**Done when:** Each chronology is addable via YAML + codegen with no manual Java. *(Not yet
started.)*

### 8. Cross-market settlement and joint calendars — done
**Shipped:** the Query API's joint-calendar constructor (`is_business_day` true only when every
member trades; `verified_through`/`status` take the worst member's value), exposed through the CLI
(`query A,B --is-business-day`), the Python package (`get_joint_calendar`), and the site's compare
pages via a browser-side reimplementation (`site.js`) that is fixture-tested for parity against the
Java reference (`SettlementParityTest`).

**Done when:** You can ask "what's the T+2 settlement date for a trade on Feb 27 across NYSE and
LSE?" and get the right answer. *(Met — via CLI, Python, MCP tool, or the site's settlement form.)*

### 9. Feeds and API (Tier 2-4 distribution) — mostly done
**Shipped:** the static `/v1/` JSON API on GitHub Pages, per-calendar `.ics` feeds
(`holidays.ics`/`holidays-recent.ics`), and jsDelivr mirroring pinned per release tag.

**Still open:** webhook/RSS notification on calendar change, a Slack/Teams bot, a GitHub Action,
and an Excel/Sheets add-on — all still in the parking lot below.

**Done when:** Non-developer consumers can access calendar data without touching code. *(Met for
the read path — Pages/jsDelivr/release assets/iCal subscription all require zero code — pending
the notification and chat-surface items.)*

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
