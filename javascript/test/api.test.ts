import { test } from "node:test";
import assert from "node:assert/strict";
import * as bdc from "../src/index.ts";
import { WeekendPolicy } from "../src/weekend.ts";

/** A synthetic calendar whose unscheduled-exception scope is incomplete from `incompleteFrom`. */
function syntheticCalendar(incompleteFrom: string): bdc.SingleCalendar {
  const interval = (scope: bdc.CompletenessScope, from: string, to: string, quality: bdc.CoverageQuality): bdc.CoverageInterval => ({ scope, from, to, quality, evidenceIds: quality === "VERIFIED" ? ["x"] : [] });
  return new bdc.SingleCalendar({
    calendarId: "SYNTHETIC",
    metadata: {},
    eventsByDate: new Map(),
    detailsByDate: new Map(),
    weekendPolicy: WeekendPolicy.fromJson({ days: ["SATURDAY", "SUNDAY"] }),
    rangeFrom: "2020-01-01",
    rangeTo: "2021-12-31",
    verifiedThrough: "2021-12-31",
    coverageIntervals: [
      interval("SCHEDULED_CLOSURES", "2020-01-01", "2021-12-31", "VERIFIED"),
      interval("EARLY_CLOSES", "2020-01-01", "2021-12-31", "VERIFIED"),
      interval("UNSCHEDULED_EXCEPTIONS", "2020-01-01", bdc.asIso(incompleteFrom) === "2020-07-31" ? "2020-07-30" : "2020-07-30", "VERIFIED"),
      interval("UNSCHEDULED_EXCEPTIONS", incompleteFrom, "2021-12-31", "INCOMPLETE"),
    ],
    timezone: null,
    name: "Synthetic",
    kind: "market",
  });
}

test("ids, aliases and the data version", () => {
  assert.equal(bdc.listCalendars().length, 24);
  assert.equal(bdc.resolveId("bvmf"), "BR-B3");
  assert.equal(bdc.resolveId("xtai"), "TW-TWSE");
  assert.equal(bdc.resolveId("xjse"), "ZA-JSE");
  assert.equal(bdc.resolveId("xkrx"), "KR-KRX");
  assert.equal(bdc.resolveId("xses"), "SG-SGX");
  assert.ok(bdc.listCalendars().includes("AU-ASX"));
  assert.equal(bdc.resolveId("xasx"), "AU-ASX");
  assert.equal(bdc.resolveId("xnys"), "US-NYSE");
  assert.equal(bdc.resolveId("us_nyse"), "US-NYSE");
  assert.equal(bdc.listAliases().XLON, "GB-LSE");
  assert.throws(() => bdc.getCalendar("XX-NOWHERE"), bdc.CalendarNotFoundError);
  assert.match(bdc.dataVersion(), /^\d+\.\d+\.\d+$/);
  assert.equal(bdc.dataGitSha().length, 40);
});

test("the headline NYSE examples", () => {
  const nyse = bdc.getCalendar("XNYS");
  assert.equal(nyse.name, "NYSE Trading Calendar");
  assert.equal(nyse.timezone, "America/New_York");
  assert.equal(nyse.isBusinessDay("2021-12-31"), true);
  assert.equal(nyse.isBusinessDay(new Date(2021, 11, 31)), true, "a Date is taken as its local calendar date");
  assert.equal(nyse.closeTime("2025-07-03"), "13:00");
  assert.equal(nyse.isEarlyClose("2025-07-03"), true);
  assert.equal(nyse.closeTime("2025-07-04"), null);
  assert.equal(nyse.nextBusinessDay("2024-01-12"), "2024-01-16");
  assert.equal(nyse.addBusinessDays("2024-01-05", 1), "2024-01-08");
  assert.equal(nyse.businessDaysBetween("2024-01-01", "2024-12-31"), 252);
  assert.equal(nyse.status("2031-01-01"), "UNKNOWN");
  assert.throws(() => nyse.isBusinessDay("2031-01-01"), bdc.OutsideCoverageError);
  assert.equal(nyse.holidays(2024).length, 10);
  assert.equal(nyse.eventOn("2024-07-04")?.description, "Independence Day");
  assert.throws(() => nyse.isBusinessDay("2024-13-01"), TypeError);
});

test("question-scoped resolution", () => {
  const xetra = bdc.getCalendar("XETR");
  const day = xetra.assessment("2015-01-02");
  assert.equal(day.completeness.EARLY_CLOSES, "INCOMPLETE");
  assert.equal(day.state, "UNKNOWN");
  assert.equal(day.businessDayState, "OPEN");
  assert.equal(day.businessDayConfidence, "CONFIRMED", "Xetra's closures and exceptions are verified from 2010");
  assert.ok(bdc.isBusinessDayKnown(day));
  assert.deepEqual(bdc.incompleteScopes(day), ["EARLY_CLOSES"]);
  assert.deepEqual(bdc.incompleteScopes(day, "BUSINESS_DAY"), []);
  assert.equal(xetra.isBusinessDay("2015-01-02"), true);
  assert.equal(xetra.status("2015-01-02"), "UNKNOWN");
  assert.throws(() => xetra.closeTime("2015-01-02"), (e: any) => e instanceof bdc.UnresolvedDateError && e.incompleteScopes[0] === "EARLY_CLOSES");

  const synthetic = syntheticCalendar("2020-07-31");
  assert.equal(synthetic.assessment("2020-08-03").businessDayState, "UNKNOWN");
  assert.equal(synthetic.assessment("2020-07-30").businessDayState, "OPEN");
  assert.throws(() => synthetic.isBusinessDay("2020-08-03"), bdc.UnresolvedDateError);
});

test("joint calendars", () => {
  const joint = bdc.getJointCalendar(["US-NYSE", "GB-LSE"]) as bdc.JointCalendar;
  assert.equal(joint.calendarId, "US-NYSE+GB-LSE");
  assert.equal(joint.isBusinessDay("2024-05-27"), false, "Memorial Day and the UK spring bank holiday");
  assert.deepEqual(joint.closedMembers("2024-05-27").map((m) => m.calendarId), ["US-NYSE", "GB-LSE"]);
  assert.deepEqual(joint.closedMembers("2024-08-26").map((m) => m.calendarId), ["GB-LSE"], "UK summer bank holiday");
  assert.equal(joint.addBusinessDays("2024-05-24", 2), "2024-05-29");
  assert.deepEqual(joint.memberCloses("2024-12-24"), [
    { calendarId: "US-NYSE", timezone: "America/New_York", localTime: "13:00" },
    { calendarId: "GB-LSE", timezone: "Europe/London", localTime: "12:30" },
  ]);
  assert.equal(joint.eventsOn("2024-08-26")[0]?.sourceModule?.startsWith("GB-LSE/"), true);
  assert.equal(bdc.getJointCalendar(["US-NYSE"]), bdc.getCalendar("US-NYSE"));
  assert.throws(() => bdc.getJointCalendar(["IL-TASE", "US-NYSE"]).isBusinessDay("2024-01-02"), bdc.OutsideCoverageError);
});

test("weekend policy reconstruction", () => {
  const tase = bdc.getCalendar("IL-TASE");
  assert.ok(tase.weekendPolicy.isEffectiveDated());
  assert.equal(tase.eventsOn("2025-06-13")[0]?.type, "WEEKEND", "Friday under the old week");
  assert.equal(tase.eventsOn("2026-01-04")[0]?.type, "WEEKEND", "first non-trading Sunday");
  assert.equal(tase.isBusinessDay("2026-01-09"), true);
  assert.equal(tase.closeTime("2026-01-09"), "13:50");
});
