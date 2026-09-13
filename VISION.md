# Vision

This document sketches the direction we see for this project — where it is today, where it could go, and the principles guiding that evolution.

## Where we are

A YAML-driven business-day calendar system that compiles declarative specs into deterministic CSV/JSON artifacts. Today it covers:

- **Two markets** (US equities via NYSE, Saudi Arabia via Tadawul)
- **Five chronologies** (ISO/Gregorian, tabular Hijri, Umm al-Qura, Julian, Persian)
- **A composable module system** for holidays, effective-dated weekend policies, per-holiday observance rules, multi-day spans, and early-close times
- **Provenance**: every holiday cites a document under `sources/`, and every event carries a CONFIRMED/PROJECTED status
- **Cross-validation** against exchange_calendars and QuantLib, with an explicit allowlist of explained differences
- **Bitemporal artifact versioning** with blessed outputs, release history, and as-of queries
- **A CLI toolchain** for validation, resolution, generation, diffing, and querying

The architecture is sound: spec-driven, inheritance-based, with clean separation between data and tooling. The foundation is built for growth.

## Where we're heading

### More markets, more coverage

The near-term goal is broader geographic coverage. Priority areas:

- **Major global exchanges** — LSE, TSE, HKEX, Euronext, Deutsche Borse, BSE/NSE, ASX
- **Regional and emerging markets** — SGX, KRX, TWSE, JSE, B3, BMV
- **Non-exchange business calendars** — banking holidays, settlement calendars, government calendars

Each new market validates and stress-tests the composition model. Markets with unusual rules (e.g., lunar-calendar-driven closures, ad-hoc government declarations) are especially valuable for surfacing gaps in the spec.

### Richer chronology support

The chronology system is designed for extensibility but only lightly exercised. We want to:

- **Add Hebrew, Buddhist, Hindu, and Chinese lunisolar calendars** — critical for markets in Israel, Thailand, India, and East Asia
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

The CLI is useful for developers. But calendars are consumed by systems, not people. We want to:

- **Publish artifacts to package registries** — Maven Central, npm, PyPI — so consumers can depend on calendar data as a versioned library
- **Provide a lightweight query API** (or at least a static site) for ad-hoc lookups
- **Support common integration patterns** — iCal feeds, JSON API contracts, embeddable widgets
- **Offer language-native libraries** that wrap the generated data with idiomatic APIs (e.g., `isBusinessDay(market, date)` in Java, Python, TypeScript)

### Tooling and developer experience

As the dataset grows, the toolchain needs to keep pace:

- **Interactive calendar explorer** — a local or hosted UI for browsing calendars, comparing markets, and visualizing holiday overlaps
- **Diff and changelog generation** — automated summaries of what changed between releases, useful for compliance and audit
- **Smarter validation** — detect common data-entry errors (duplicate holidays, missing shift policies, gaps in coverage ranges)
- **Performance at scale** — ensure the generator and resolver remain fast as the number of calendars and date ranges grows

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

Ranked by leverage — what most accelerates everything else.

### 1. Market coverage: the G10 exchanges
**Why first:** Nothing else matters without data. Coverage is the product. Two markets is a proof of concept; twenty is a resource people depend on.

Target the markets with the most users and the most public reference data to validate against:
- LSE, TSE, HKEX, Euronext (Paris, Amsterdam), Deutsche Borse, ASX, SGX, KRX, BSE/NSE, TSX

Each market added is a forcing function on the spec — it'll surface missing rule types, chronology gaps, and edge cases. Do this first because it makes every later investment more valuable.

**Done when:** 10+ markets with full holiday coverage, sourced and cross-validated.

### 2. Contribution scaffolding and validation
**Why second:** Market coverage doesn't scale if every new market is a bespoke effort. We need the on-ramp.

- `./gradlew scaffold --market HKEX` generates YAML skeleton + test fixtures + PR template
- CI validation that rejects common mistakes (holidays on weekends without shift policy, unsourced dates, coverage gaps)
- PR preview comments showing generated calendar diff
- "Adopt a market" contributor program with clear ownership model

**Done when:** A knowledgeable contributor can add a new market in an afternoon without reading the full spec.

### 3. Language-native client libraries (Tier 1 distribution)
**Why third:** The biggest friction today is consumption. Even if the data is perfect, if integrating it takes a day of parsing CSVs, people won't bother.

- Java library on Maven Central (natural — the toolchain is already Java)
- Python package on PyPI (biggest potential audience in finance/data)
- TypeScript/npm package (web and Node consumers)
- Each wraps the generated JSON with a thin idiomatic API: `isBusinessDay()`, `nextBusinessDay()`, `businessDaysBetween()`

**Done when:** `pip install bdc-calendars` gives you a working `is_business_day("US-NYSE", date(2026, 7, 4))` → `False`.

### 4. Cross-validation and accuracy infrastructure
**Why fourth:** Trust is what converts users into dependents. This is the moat.

- Automated comparison against exchange_calendars, QuantLib, and any other public source
- Per-market accuracy scorecards: coverage range, last-verified date, known gaps, source links
- Public errata log — when we're wrong, we say so
- Nightly CI job that flags any discrepancy with external sources

**Done when:** Every market page shows a confidence score and a list of corroborating sources.

### 5. Static site (consumer-facing)
**Why fifth:** Discovery. People Google "NYSE holidays 2027" — we should be the answer. Also makes the project legible to non-developers.

- Generated from artifacts, not a separate app
- Per-market calendar grid view with holidays highlighted
- Market comparison / overlap view
- Changelog generated from release-history diffs
- GitHub Pages, zero ops

**Done when:** You can browse any market's holidays in a browser and share a permalink to a specific date.

### 6. Contributor UI (local dev server)
**Why sixth:** Makes the contribution loop tighter. You edit YAML, you see the rendered calendar update. Lower priority than the scaffolding (#2) because scaffolding helps more people sooner.

- `./gradlew serve` launches a local web server
- Shows resolved calendar for any YAML spec
- Live-reloads on file change
- Side-by-side diff against blessed artifacts

**Done when:** Contributors can visually verify their changes without running the full generate pipeline and inspecting CSVs.

### 7. Additional chronologies
**Why seventh (not higher):** Important, but driven by market demand. We don't need Hebrew calendar support until we add TASE. We don't need Chinese lunisolar until we add SSE/SZSE. Let market coverage (#1) pull chronology work.

- Hebrew (for TASE)
- Chinese lunisolar (for SSE, SZSE, HKEX lunar holidays)
- Buddhist (for SET)
- Hindu (for BSE/NSE Diwali, etc.)

**Done when:** Each chronology is addable via YAML + codegen with no manual Java.

### 8. Cross-market settlement and joint calendars
**Why eighth:** This is the killer differentiator, but it needs a critical mass of markets to be useful. Once we have 10+ markets, this becomes the feature that makes us irreplaceable.

- Joint calendar composition: "business day in both US-NYSE and LSE"
- T+N settlement calculation across jurisdictions
- Holiday overlap analysis and visualization

**Done when:** You can ask "what's the T+2 settlement date for a trade on Feb 27 across NYSE and LSE?" and get the right answer.

### 9. Feeds and API (Tier 2-4 distribution)
**Why last:** Higher effort, more ops burden, and only valuable once the data and core consumption paths are solid.

- Static JSON API on a CDN (`/v1/calendars/US-NYSE/2026.json`)
- iCal feeds per market
- Webhook/RSS for calendar change notifications
- Slack/Teams bot, GitHub Action, Excel add-on

**Done when:** Non-developer consumers can access calendar data without touching code.

### Parking lot (not prioritized yet)

These are good ideas but don't have a clear slot yet:

- Serverless query API (Tier 3) — depends on demand signal
- Excel/Google Sheets add-on — potentially huge reach but different skillset to build
- Non-exchange calendars (banking, government, settlement) — natural extension but different sourcing challenges
- Commercial support model — premature until there's meaningful adoption

---

## Open questions

- **Licensing model**: MIT/Apache for the toolchain, but what about the data? CC-BY? ODbL? Does the license affect institutional adoption?
- **Governance as we scale**: The current maintainer model works for a small project. At 50+ markets with domain-expert maintainers, do we need a steering committee? Per-region maintainers?
- **Commercial sustainability**: If firms depend on this data, is there a model (support contracts, hosted API, early access to updates) that funds ongoing maintenance without compromising the open-source core?
- **Relationship with exchanges**: Should we engage exchange data teams directly? Some might contribute or endorse. Others might see us as competition to their data products.
- **Scope boundaries**: Where does "business-day calendar" end and "market data" begin? Do we include early-close times? Trading session hours? Half-days?

## How to get involved

If any of this resonates, look at the open issues, pick a market you know well, or propose a chronology. The best contributions come from people with domain knowledge of the calendars they're adding. See [CONTRIBUTING.md](CONTRIBUTING.md) for mechanics.
