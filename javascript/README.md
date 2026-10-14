# bdc-calendars (JavaScript / TypeScript)

Business-day and trading calendars with provenance and confidence: the same Query API as the
Java `bdc-calendar-core` library and the Python `bdc-calendars` package, for Node.js and
TypeScript, with **zero runtime dependencies**. The calendar data is generated from the YAML
specs in [calendar-project](https://github.com/twallengren/calendar-project) and shipped inside
the package, so every answer is offline, deterministic and reproducible for a given version.

```ts
import { getCalendar, getJointCalendar } from "bdc-calendars";

const nyse = getCalendar("XNYS");                 // id, MIC or alias
nyse.isBusinessDay("2021-12-31");                 // true
nyse.closeTime("2025-07-03");                     // "13:00"  (local to nyse.timezone)
nyse.nextBusinessDay("2024-01-12");               // "2024-01-16" (MLK Day)
nyse.addTenor("2024-01-31", "3M");                // "2024-04-30" (MODIFIED_FOLLOWING)
nyse.yearFraction("2024-01-01", "2024-02-01", "BUS_252"); // 21/252

getJointCalendar(["US-NYSE", "GB-LSE"]).addTenor("2024-05-24", "T+2"); // "2024-05-29"

nyse.generateSchedule({ start: "2024-01-15", end: "2024-12-01", frequency: "QUARTERLY", stub: "SHORT_INITIAL" }).dates;
// ["2024-01-16", "2024-03-01", "2024-06-03", "2024-09-03", "2024-12-02"]
```

Dates are ISO `YYYY-MM-DD` strings; a `Date` is accepted as input and taken as its local
calendar date. Nothing here ever touches a timezone.

## Confidence, not just answers

Every calendar declares, per scope (scheduled closures, early closes, unscheduled exceptions),
how well its evidence covers each date. An operation only needs the scopes its question depends
on: **business-day questions** (`isBusinessDay`, navigation, counts, every financial operation)
need scheduled closures and unscheduled exceptions, because an early close is still a business
day; **session questions** (`closeTime`, `isEarlyClose`, `memberCloses`) also need early closes.

```ts
const xetra = getCalendar("XETR");
xetra.isBusinessDay("2015-01-02");                // true
xetra.assessment("2015-01-02").businessDayConfidence; // "CONFIRMED"
xetra.closeTime("2015-01-02");                    // throws UnresolvedDateError(["EARLY_CLOSES"])
nyse.status("2031-01-01");                        // "UNKNOWN" — the safe probe
nyse.isBusinessDay("2031-01-01");                 // throws OutsideCoverageError
```

Outside a calendar's covered range the absence of a closure means "not known", never "open".
`assessment(date)` never throws and reports the scheduled state, both confidence views, the
completeness of every scope and the evidence ids behind them. Detailed financial operations
(`addTenorDetailed`, `adjustDetailed`, `generateSchedule`, …) report the weakest business-day
confidence of every date they examined.

## API

| Member | Meaning |
|--------|---------|
| `getCalendar(id)`, `getJointCalendar([ids])`, `listCalendars()`, `listAliases()` | lookup by id, MIC (`XNYS`) or alias; a joint calendar is open only where every member is |
| `isBusinessDay`, `nextBusinessDay`, `previousBusinessDay`, `addBusinessDays(date, n)` | the business-day question and navigation (the start date is never counted) |
| `businessDaysBetween(a, b)`, `sessionsInRange(a, b)`, `holidays(year)` | counting and listing |
| `closeTime`, `isEarlyClose`, `memberCloses` | shortened sessions (`HH:MM`, local to `timezone`) |
| `status(date)`, `assessment(date)` | confidence probes; never throw |
| `adjust`, `businessDayOffset`, `advanceMonths`, `lastBusinessDayOfMonth` | financial date conventions (`UNADJUSTED`, `FOLLOWING`, `MODIFIED_FOLLOWING`, `PRECEDING`, `MODIFIED_PRECEDING`) |
| `addTenor(date, "3M" \| "2W" \| "1Y" \| "T+2", convention?)` | market tenors; business-day tenors ignore the convention |
| `yearFraction(start, end, dayCount)` | `ACT_360`, `ACT_365F`, `ACT_ACT_ISDA`, `THIRTY_360_BOND`, `THIRTY_E_360`, `THIRTY_E_360_ISDA` (2006 ISDA §4.16) or `BUS_252` on this calendar; `yearFraction()` from the module for ACT/ACT ICMA with a reference period |
| `generateSchedule({ start, end, frequency, stub?, roll?, convention? })` | periodic dates with `SHORT/LONG_FINAL`, `SHORT/LONG_INITIAL` or `NONE` stubs and `DAY_OF_MONTH`, `EOM` or `IMM` rolls |
| `immDate`, `nextImm`, `nextQuarterlyImm`, `Tenor`, `FREQUENCIES` | the calendar-free vocabulary |
| `dataVersion()`, `dataGitSha()` | the bundled dataset's release version and source commit |

The contract is [`spec/SPEC.md`](../spec/SPEC.md) in the source repository; `test/parity.test.ts`
replays the same fixtures as the Python port, dumped from the Java reference implementation, so
the three ports are held to identical answers for every sampled date, assessment and operation.

## Development

```bash
cd javascript
npm install                 # dev dependencies only (typescript, @types/node)
npm run sync-data           # copy python/bdc_calendars/data (derived from blessed/) into data/
npm test                    # node:test with native type stripping (Node 22.6+)
npm run build               # tsc -> dist/
```

Licence: Apache-2.0 for the code, CC0 for the data (see the repository's `LICENSE` and
`DATA_LICENSE`).
