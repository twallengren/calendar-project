/**
 * SPECIAL_SESSION semantics on synthetic calendars, ported from the Java reference's
 * core/src/test/java/com/bdc/stream/SpecialSessionTest.java. No bundled calendar has a special
 * session yet, so the parity fixtures only exercise the empty case.
 */
import { test } from "node:test";
import assert from "node:assert/strict";
import { parseCsv } from "../src/csv.ts";
import { eventFromRow } from "../src/loader.ts";
import { WeekendPolicy } from "../src/weekend.ts";
import * as bdc from "../src/index.ts";

const HOLIDAY = "2024-11-01"; // Friday
const SATURDAY = "2024-03-02";
const SUNDAY = "2024-03-03";
const MONDAY = "2024-03-04";
const KOLKATA = "Asia/Kolkata";

function session(date: string, key: string, open: string, close: string): bdc.Event {
  return { date, type: "SPECIAL_SESSION", description: key, key, sourceModule: "module:fixture", observedFrom: null, closeTime: close, status: "CONFIRMED", openTime: open };
}

function closed(date: string, description: string): bdc.Event {
  return { date, type: "CLOSED", description, key: null, sourceModule: null, observedFrom: null, closeTime: null, status: "CONFIRMED", openTime: null };
}

/** A Saturday/Sunday-weekend calendar over 2024 holding the given non-weekend rows. */
function calendar(events: bdc.Event[], options: { id?: string; timezone?: string | null; intervals?: bdc.CoverageInterval[] } = {}): bdc.SingleCalendar {
  const eventsByDate = new Map<string, bdc.Event[]>();
  for (const event of events) eventsByDate.set(event.date, [...(eventsByDate.get(event.date) ?? []), event]);
  return new bdc.SingleCalendar({
    calendarId: options.id ?? "CAL",
    metadata: {},
    eventsByDate,
    detailsByDate: new Map(),
    weekendPolicy: WeekendPolicy.fromJson({ days: ["SATURDAY", "SUNDAY"] }),
    rangeFrom: "2024-01-01",
    rangeTo: "2024-12-31",
    verifiedThrough: null,
    coverageIntervals: options.intervals ?? [],
    timezone: options.timezone === undefined ? KOLKATA : options.timezone,
    name: options.id ?? "CAL",
    kind: "market",
  });
}

test("a session on a holiday makes a trading day but not a business day", () => {
  const cal = calendar([closed(HOLIDAY, "Festival"), session(HOLIDAY, "evening", "18:00", "19:00")]);
  assert.equal(cal.isBusinessDay(HOLIDAY), false);
  assert.equal(cal.isTradingDay(HOLIDAY), true);
  assert.deepEqual(cal.specialSessions(HOLIDAY), [
    { calendarId: "CAL", timezone: KOLKATA, openTime: "18:00", closeTime: "19:00", key: "evening", description: "evening", status: "CONFIRMED" },
  ]);
  // Date arithmetic never lands on it, and its close is not an early close.
  assert.equal(cal.nextBusinessDay("2024-10-31"), "2024-11-04");
  assert.equal(cal.addBusinessDays("2024-10-31", 1), "2024-11-04");
  assert.equal(cal.closeTime(HOLIDAY), null);
  assert.equal(cal.isEarlyClose(HOLIDAY), false);
  assert.deepEqual(cal.memberCloses(HOLIDAY), []);
  assert.equal(cal.assessment(HOLIDAY).scheduledState, "CLOSED");
  assert.equal(cal.assessment(HOLIDAY).businessDayState, "CLOSED");
});

test("a session on a weekend day is a trading day and coexists with the weekend row", () => {
  const cal = calendar([session(SATURDAY, "drill", "09:15", "12:30")]);
  assert.equal(cal.isBusinessDay(SATURDAY), false);
  assert.equal(cal.isTradingDay(SATURDAY), true);
  assert.equal(cal.isTradingDay(SUNDAY), false);
  assert.equal(cal.specialSessions(SATURDAY).length, 1);
  assert.deepEqual(cal.eventsOn(SATURDAY).map((e) => e.type).sort(), ["SPECIAL_SESSION", "WEEKEND"]);
});

test("ordinary days answer as before", () => {
  const cal = calendar([]);
  assert.equal(cal.isTradingDay(MONDAY), true);
  assert.equal(cal.isTradingDay(SATURDAY), false);
  assert.deepEqual(cal.specialSessions(MONDAY), []);
});

test("sessions are ordered by open time, then close time", () => {
  const cal = calendar([session(SATURDAY, "second", "11:30", "12:30"), session(SATURDAY, "first", "09:15", "10:00"), session(SATURDAY, "early-short", "09:15", "09:45")]);
  assert.deepEqual(cal.specialSessions(SATURDAY).map((s) => s.key), ["early-short", "first", "second"]);
});

test("a trading day needs the business-day scopes; sessions need every scope", () => {
  const interval = (scope: bdc.CompletenessScope, quality: bdc.CoverageQuality): bdc.CoverageInterval => ({ scope, from: "2024-01-01", to: "2024-12-31", quality, evidenceIds: [] });
  const cal = calendar([closed(HOLIDAY, "Festival"), session(HOLIDAY, "evening", "18:00", "19:00")], {
    intervals: [interval("SCHEDULED_CLOSURES", "VERIFIED"), interval("UNSCHEDULED_EXCEPTIONS", "PROJECTED"), interval("EARLY_CLOSES", "INCOMPLETE")],
  });
  assert.equal(cal.isTradingDay(HOLIDAY), true);
  assert.throws(
    () => cal.specialSessions(HOLIDAY),
    (e: any) => e instanceof bdc.UnresolvedDateError && JSON.stringify(e.incompleteScopes) === JSON.stringify(["EARLY_CLOSES"]),
  );

  const unresolved = calendar([session(SATURDAY, "drill", "09:15", "12:30")], {
    intervals: [interval("SCHEDULED_CLOSURES", "VERIFIED"), interval("UNSCHEDULED_EXCEPTIONS", "INCOMPLETE"), interval("EARLY_CLOSES", "VERIFIED")],
  });
  assert.throws(() => unresolved.isTradingDay(SATURDAY), bdc.UnresolvedDateError);
});

test("outside coverage throws", () => {
  const cal = calendar([]);
  assert.throws(() => cal.isTradingDay("2025-01-01"), bdc.OutsideCoverageError);
  assert.throws(() => cal.specialSessions("2025-01-01"), bdc.OutsideCoverageError);
});

test("a joint trading day needs every member to trade", () => {
  const india = calendar([closed(HOLIDAY, "Festival"), session(HOLIDAY, "evening", "18:00", "19:00")]);
  const other = calendar([], { id: "OTHER", timezone: null });
  const shut = calendar([closed(HOLIDAY, "Shut")], { id: "SHUT", timezone: null });
  const satSession = calendar([session(SATURDAY, "drill", "09:15", "12:30")], { id: "SAT", timezone: "Asia/Tokyo" });

  assert.equal(new bdc.JointCalendar([india, other]).isTradingDay(HOLIDAY), true);
  assert.equal(new bdc.JointCalendar([india, other]).isBusinessDay(HOLIDAY), false);
  assert.equal(new bdc.JointCalendar([india, shut]).isTradingDay(HOLIDAY), false);
  assert.equal(new bdc.JointCalendar([india, satSession]).isTradingDay(SATURDAY), false, "only one member trades on the Saturday");

  const sessions = new bdc.JointCalendar([india, other]).specialSessions(HOLIDAY);
  assert.equal(sessions.length, 1);
  assert.equal(sessions[0]!.calendarId, "CAL");
  assert.equal(sessions[0]!.timezone, KOLKATA);

  const both = calendar([session(SATURDAY, "muhurat", "18:00", "19:00")]);
  assert.deepEqual(
    new bdc.JointCalendar([both, satSession]).specialSessions(SATURDAY).map((s) => [s.calendarId, s.timezone, s.openTime]),
    [
      ["CAL", KOLKATA, "18:00"],
      ["SAT", "Asia/Tokyo", "09:15"],
    ],
    "member order, not time order, each with its own identity",
  );
  assert.throws(() => new bdc.JointCalendar([india, other]).specialSessions("2025-01-01"), bdc.OutsideCoverageError);
});

test("event rows require session times only on SPECIAL_SESSION", () => {
  const row = (type: string, open: string, close: string) => ({ date: HOLIDAY, type, description: "x", key: "k", source_module: "m", observed_from: "", close_time: close, status: "CONFIRMED", open_time: open });
  assert.throws(() => eventFromRow(row("SPECIAL_SESSION", "", "")), TypeError);
  assert.throws(() => eventFromRow(row("SPECIAL_SESSION", "19:00", "18:00")), TypeError);
  assert.throws(() => eventFromRow(row("CLOSED", "12:00", "")), TypeError);
  assert.equal(eventFromRow(row("SPECIAL_SESSION", "18:00", "19:00")).openTime, "18:00");
});

test("the CSV reader reads open_time by header name and tolerates its absence", () => {
  const events = parseCsv(
    [
      "date,type,description,key,source_module,observed_from,close_time,status,open_time",
      "2024-11-01,CLOSED,Festival,festival,m,,,CONFIRMED,",
      "2024-11-01,SPECIAL_SESSION,Evening,evening,m,,19:00,CONFIRMED,18:00",
    ].join("\n"),
  ).map(eventFromRow);
  assert.equal(events[0]!.openTime, null);
  assert.equal(events[1]!.openTime, "18:00");
  assert.equal(events[1]!.closeTime, "19:00");

  const legacy = parseCsv(["date,type,description,key,source_module,observed_from,close_time,status", "2024-11-01,CLOSED,Festival,festival,m,,,CONFIRMED"].join("\n")).map(eventFromRow);
  assert.equal(legacy[0]!.openTime, null);
  assert.equal(legacy[0]!.type, "CLOSED");
});

test("bundled calendars: trading days follow business days when there are no special sessions", () => {
  const nyse = bdc.getCalendar("US-NYSE");
  assert.equal(nyse.isTradingDay("2024-07-04"), false);
  assert.equal(nyse.isTradingDay("2024-07-05"), true);
  assert.deepEqual(nyse.specialSessions("2024-07-04"), []);
  const joint = bdc.getJointCalendar(["US-NYSE", "GB-LSE"]);
  assert.equal(joint.isTradingDay("2024-08-26"), false);
  assert.deepEqual(joint.specialSessions("2024-08-26"), []);
});
