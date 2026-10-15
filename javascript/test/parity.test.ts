/**
 * Holds the TypeScript port to the answers of the Java reference implementation, replaying the
 * same fixtures as python/tests/test_parity.py (dumped by python/scripts/generate_parity_fixture.sh).
 */
import { test } from "node:test";
import assert from "node:assert/strict";
import { readdirSync, readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { addMonths, plusDays, min } from "../src/dates.ts";
import * as bdc from "../src/index.ts";

const FIXTURE_DIR = join(dirname(fileURLToPath(import.meta.url)), "..", "..", "python", "tests", "fixtures");
const CALENDARS = readdirSync(FIXTURE_DIR)
  .filter((n) => n.startsWith("parity_") && n.endsWith(".json"))
  .map((n) => n.slice(7, -5))
  .sort();

function load(id: string): any {
  return JSON.parse(readFileSync(join(FIXTURE_DIR, `parity_${id}.json`), "utf8"));
}

/** Eight fields, plus a ninth (open_time) only on rows that carry one (SPECIAL_SESSION). */
function eventTuple(e: bdc.Event): unknown[] {
  const tuple: unknown[] = [e.date, e.type, e.description, e.key, e.sourceModule, e.observedFrom, time(e.closeTime), e.status];
  if (e.openTime) tuple.push(time(e.openTime));
  return tuple;
}

function time(value: string | null): string | null {
  if (value === null) return null;
  return value.length === 8 && value.endsWith(":00") ? value.slice(0, 5) : value;
}

function fixtureEvent(row: unknown[]): unknown[] {
  const copy = [...row];
  if (copy[6]) copy[6] = time(copy[6] as string);
  if (copy[8]) copy[8] = time(copy[8] as string);
  return copy;
}

function sessionDict(s: bdc.SpecialSession): unknown {
  return {
    calendar_id: s.calendarId,
    timezone: s.timezone,
    open_time: time(s.openTime),
    close_time: time(s.closeTime),
    key: s.key,
    description: s.description,
    status: s.status,
  };
}

function normaliseExpectedSessions(expected: any): any {
  if (!Array.isArray(expected)) return expected;
  return expected.map((s) => ({ ...s, open_time: time(s.open_time), close_time: time(s.close_time) }));
}

/** Runs `call`; the fixture either holds a value or a typed error with the offending date. */
function assertQuery(call: () => unknown, expected: any, context: unknown): void {
  if (expected && typeof expected === "object" && "error" in expected) {
    assert.throws(
      call,
      (error: any) => {
        assert.equal(error.constructor.name, expected.error.replace("Error", "Error"), `${context}: error type`);
        assert.equal(error.date, expected.date, `${context}: error date`);
        return true;
      },
      `${context}: expected ${expected.error}`,
    );
  } else {
    assert.deepEqual(call(), expected, String(context));
  }
}

function assessmentDict(a: bdc.DayAssessment): unknown {
  return {
    date: a.date,
    state: a.state,
    scheduled_state: a.scheduledState,
    effective_confidence: a.effectiveConfidence,
    business_day_state: a.businessDayState,
    business_day_confidence: a.businessDayConfidence,
    completeness: a.completeness,
    evidence_ids: a.evidenceIds,
    events: a.events.map((d) => ({
      date: d.event.date,
      type: d.event.type,
      description: d.event.description,
      key: d.event.key,
      source_module: d.event.sourceModule,
      observed_from: d.event.observedFrom,
      close_time: time(d.event.closeTime),
      // the Java AssessmentEmitter writes open_time only when the event has one
      ...(d.event.openTime ? { open_time: time(d.event.openTime) } : {}),
      raw_status: d.rawStatus,
      effective_status: d.effectiveStatus,
      evidence_ids: d.evidenceIds,
      observation_lineage: d.observationLineage,
      nominal_native_date: d.nominalNativeDate
        ? { chronology_id: d.nominalNativeDate.chronologyId, year: d.nominalNativeDate.year, month_code: d.nominalNativeDate.monthCode, day: d.nominalNativeDate.day }
        : null,
      chronology_profile: d.chronologyProfile,
      chronology_provider: d.chronologyProvider,
    })),
  };
}

function operationDict(r: bdc.DateOperationResult): unknown {
  return {
    original_date: r.originalDate,
    result_date: r.resultDate,
    operation: r.operation,
    convention: r.convention,
    business_day_offset: r.businessDayOffset,
    month_offset: r.monthOffset,
    preserve_end_of_month: r.preserveEndOfMonth,
    effective_confidence: r.effectiveConfidence,
    examined_dates: r.examinedDates,
    tenor: r.tenor,
  };
}

function normaliseExpectedEvents(events: any[]): any[] {
  return events.map((e) => {
    const copy = { ...e };
    delete copy.status; // the Java emitter's extra key; Python strips it too
    copy.close_time = time(copy.close_time);
    if (copy.open_time) copy.open_time = time(copy.open_time);
    return copy;
  });
}

for (const id of CALENDARS) {
  const fixture = load(id);
  const calendar = bdc.getCalendar(id);

  test(`${id}: identity, bounds and data version`, () => {
    assert.equal(fixture.fixture_schema, 3, "fixture schema 3 records is_trading_day and special_sessions");
    assert.equal(calendar.calendarId, fixture.calendar_id);
    assert.deepEqual([calendar.range.start, calendar.range.end], fixture.range);
    assert.equal(calendar.verifiedThrough, fixture.verified_through);
    assert.equal(bdc.dataVersion(), fixture.version);
  });

  test(`${id}: every non-weekend event matches`, () => {
    const ours = calendar.eventsInRange(calendar.range.start, calendar.range.end).filter((e) => e.type !== "WEEKEND").map(eventTuple);
    const theirs = fixture.events.map(fixtureEvent);
    assert.equal(ours.length, theirs.length);
    assert.deepEqual(ours, theirs);
  });

  test(`${id}: sampled queries match`, () => {
    assert.ok(fixture.queries.length > 500, "fixture looks truncated");
    for (const q of fixture.queries) {
      const day: string = q.d;
      assertQuery(() => calendar.isBusinessDay(day), q.b, `${day} b`);
      assert.equal(calendar.status(day), q.s, `${day} status`);
      assertQuery(() => calendar.isEarlyClose(day), q.ec, `${day} ec`);
      assertQuery(() => time(calendar.closeTime(day)), typeof q.c === "object" && q.c !== null ? q.c : time(q.c), `${day} c`);
      assert.ok("td" in q && "ss" in q, `${day}: fixture lacks td/ss`);
      assertQuery(() => calendar.isTradingDay(day), q.td, `${day} td`);
      assertQuery(() => calendar.specialSessions(day).map(sessionDict), normaliseExpectedSessions(q.ss), `${day} ss`);
      if (q.assessment) {
        const expected = { ...q.assessment, events: normaliseExpectedEvents(q.assessment.events) };
        assert.deepEqual(assessmentDict(calendar.assessment(day)), expected, `${day} assessment`);
      }
      for (const [key, call] of [
        ["n", () => calendar.nextBusinessDay(day)],
        ["p", () => calendar.previousBusinessDay(day)],
        ["f5", () => calendar.addBusinessDays(day, 5)],
        ["b5", () => calendar.addBusinessDays(day, -5)],
      ] as const) {
        if (q[key] === null) assert.throws(call, `${day} ${key}: Java refused`);
        else assertQuery(call, q[key], `${day} ${key}`);
      }
      assertQuery(() => calendar.businessDaysBetween(day, q.we), q.cnt, `${day} cnt`);
      assert.deepEqual(calendar.eventsOn(day).map(eventTuple), q.ev.map(fixtureEvent), `${day} ev`);
    }
  });

  test(`${id}: financial operations match`, () => {
    for (const c of fixture.financial_queries) {
      const day: string = c.d;
      for (const convention of bdc.BUSINESS_DAY_CONVENTIONS) {
        assertQuery(() => operationDict(calendar.adjustDetailed(day, convention)), c["adjust_" + convention], `${day} adjust ${convention}`);
        assertQuery(() => operationDict(calendar.advanceMonthsDetailed(day, 1, convention, false)), c["months_" + convention], `${day} months ${convention}`);
      }
      for (const offset of [-5, 0, 5]) {
        assertQuery(() => operationDict(calendar.businessDayOffsetDetailed(day, offset)), c["offset_" + offset], `${day} offset ${offset}`);
      }
      assertQuery(() => operationDict(calendar.advanceMonthsDetailed(day, -1, "MODIFIED_FOLLOWING", true)), c.eom, `${day} eom`);
      assertQuery(() => operationDict(calendar.lastBusinessDayOfMonthDetailed(day)), c.last, `${day} last`);
      for (const tenor of ["3M", "T+2", "2W", "-1Y", "0D"]) {
        if (!("tenor_" + tenor in c)) continue;
        assertQuery(() => operationDict(calendar.addTenorDetailed(day, tenor, "MODIFIED_FOLLOWING")), c["tenor_" + tenor], `${day} tenor ${tenor}`);
      }
      for (const key of Object.keys(c)) {
        if (key.startsWith("yf_")) {
          const end = min(plusDays(day, 45), calendar.range.end);
          assertQuery(
            () => {
              const r = calendar.yearFractionDetailed(day, end, key.slice(3));
              const expectedFraction = c[key]?.year_fraction;
              const fraction = typeof expectedFraction === "number" && Math.abs(r.yearFraction - expectedFraction) < 1e-12 ? expectedFraction : r.yearFraction;
              return { start: r.start, end: r.end, day_count: r.dayCount, days: r.days, year_fraction: fraction, effective_confidence: r.effectiveConfidence, examined_dates: r.examinedDates };
            },
            c[key],
            `${day} ${key}`,
          );
        } else if (key.startsWith("schedule_")) {
          const end = min(addMonths(day, 10), calendar.range.end);
          const definition =
            key === "schedule_SHORT_FINAL"
              ? { start: day, end, frequency: "QUARTERLY", stub: "SHORT_FINAL" as const, roll: "DAY_OF_MONTH" as const, convention: "MODIFIED_FOLLOWING" }
              : { start: day, end, frequency: "MONTHLY", stub: "LONG_INITIAL" as const, roll: "EOM" as const, convention: "FOLLOWING" };
          assertQuery(
            () => {
              const s = calendar.generateSchedule(definition);
              return {
                periods: s.periods.map((p) => ({ unadjusted_start: p.unadjustedStart, unadjusted_end: p.unadjustedEnd, start: p.start, end: p.end, stub: p.stub })),
                effective_confidence: s.effectiveConfidence,
                examined_dates: s.examinedDates,
              };
            },
            c[key],
            `${day} ${key}`,
          );
        }
      }
    }
  });
}
