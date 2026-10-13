import { test } from "node:test";
import assert from "node:assert/strict";
import * as bdc from "../src/index.ts";

const close = (a: number, b: number) => assert.ok(Math.abs(a - b) < 1e-12, `${a} != ${b}`);

test("tenor parsing and units", () => {
  assert.deepEqual(bdc.Tenor.parse("3M"), new bdc.Tenor(3, "M"));
  assert.deepEqual(bdc.Tenor.parse(" -2w "), new bdc.Tenor(-2, "W"));
  assert.equal(String(bdc.Tenor.parse("T+2")), "2B");
  assert.equal(String(bdc.Tenor.parse("t-1")), "-1B");
  assert.equal(bdc.Tenor.parse("1y").months, 12);
  assert.equal(bdc.Tenor.parse("2W").days, 14);
  for (const bad of ["3X", "M", "", "T+", "3 M", "1.5Y"]) assert.throws(() => bdc.Tenor.parse(bad), bad);
  assert.throws(() => bdc.Tenor.parse("3M").days);
  assert.equal(bdc.frequency("3M"), bdc.FREQUENCIES.QUARTERLY);
  assert.equal(bdc.frequency("quarterly").periodsPerYear, 4);
});

test("IMM dates", () => {
  assert.equal(bdc.immDate(2024, 3), "2024-03-20");
  assert.equal(bdc.immDate(2024, 6), "2024-06-19");
  assert.ok(bdc.isImm("2024-03-20") && !bdc.isImm("2024-03-21"));
  assert.equal(bdc.nextImm("2024-03-20"), "2024-04-17");
  assert.equal(bdc.nextImm("2024-03-19"), "2024-03-20");
  assert.equal(bdc.nextQuarterlyImm("2024-03-20"), "2024-06-19");
});

test("day counts follow the 2006 ISDA definitions", () => {
  assert.equal(bdc.dayCountDays("ACT_360", "2024-01-31", "2024-02-29"), 29);
  assert.equal(bdc.yearFraction("ACT_360", "2024-01-31", "2024-02-29"), 29 / 360);
  assert.equal(bdc.yearFraction("ACT_365F", "2024-01-01", "2025-01-01"), 366 / 365);
  close(bdc.yearFraction("ACT_ACT_ISDA", "2023-11-15", "2024-02-15"), 47 / 365 + 45 / 366);
  close(bdc.yearFraction("ACT_ACT_ISDA", "2022-06-30", "2025-06-30"), 3.0);
  assert.equal(bdc.dayCountDays("THIRTY_360_BOND", "2024-02-29", "2024-03-31"), 32);
  assert.equal(bdc.dayCountDays("THIRTY_E_360", "2024-02-29", "2024-03-31"), 31);
  assert.equal(bdc.dayCountDays("THIRTY_E_360_ISDA", "2024-02-29", "2024-03-31"), 30);
  assert.equal(bdc.dayCountDays("THIRTY_360_BOND", "2024-01-31", "2024-03-31"), 60);
  assert.equal(bdc.yearFraction("THIRTY_360_BOND", "2023-01-15", "2023-04-15"), 0.25);
  close(bdc.yearFraction("THIRTY_E_360_ISDA", "2023-01-31", "2024-02-29", { endIsTermination: true }), 389 / 360);
  close(bdc.yearFraction("THIRTY_E_360_ISDA", "2023-01-31", "2024-02-29"), 390 / 360);
  close(bdc.yearFraction("ACT_ACT_ICMA", "2024-02-15", "2024-05-15", { periodStart: "2024-02-15", periodEnd: "2024-08-15", frequency: "SEMI_ANNUAL" }), 90 / (2 * 182));
  assert.throws(() => bdc.yearFraction("ACT_ACT_ICMA", "2024-02-15", "2024-05-15"));
  assert.throws(() => bdc.yearFraction("BUS_252", "2024-02-15", "2024-05-15"));
  assert.equal(bdc.dayCount("act/360"), "ACT_360");
  for (const convention of bdc.DAY_COUNTS) {
    if (!bdc.isCalendarFree(convention)) continue;
    close(bdc.yearFraction(convention, "2024-02-15", "2023-11-15"), -bdc.yearFraction(convention, "2023-11-15", "2024-02-15"));
  }
});

test("tenor addition on a real calendar", () => {
  const nyse = bdc.getCalendar("US-NYSE");
  assert.equal(nyse.addTenor("2024-01-31", "3M", "MODIFIED_FOLLOWING"), "2024-04-30");
  assert.equal(nyse.addTenor("2024-01-12", "T+2", "UNADJUSTED"), "2024-01-17", "MLK Monday is closed");
  assert.equal(nyse.addTenor("2024-07-03", "1D", "FOLLOWING"), "2024-07-05");
  assert.equal(nyse.addTenor("2024-07-03", "1D", "PRECEDING"), "2024-07-03");
  const result = nyse.addTenorDetailed("2024-07-03", "1D", "FOLLOWING");
  assert.equal(result.operation, "ADD_TENOR");
  assert.equal(result.tenor, "1D");
  assert.deepEqual(result.examinedDates, ["2024-07-04", "2024-07-05"]);
  const business = nyse.addTenorDetailed("2024-01-12", "T+2", "FOLLOWING");
  assert.equal(business.businessDayOffset, 2);
  assert.equal(business.convention, null);
  const months = nyse.addTenorDetailed("2024-01-31", "1M", "MODIFIED_FOLLOWING", true);
  assert.equal(months.monthOffset, 1);
  assert.equal(months.resultDate, "2024-02-29");
  assert.equal(nyse.adjustDetailed("2024-01-02", "FOLLOWING").tenor, null);
});

test("BUS/252 counts business days with confidence", () => {
  const nyse = bdc.getCalendar("US-NYSE");
  const result = nyse.yearFractionDetailed("2024-01-01", "2024-02-01", "BUS_252");
  assert.equal(result.days, 21);
  assert.equal(result.yearFraction, 21 / 252);
  assert.equal(result.examinedDates.length, 31);
  assert.equal(nyse.yearFractionDetailed("2024-02-01", "2024-01-01", "BUS_252").days, -21);
  const act = nyse.yearFractionDetailed("2024-01-01", "2024-02-01", "ACT_360");
  assert.equal(act.days, 31);
  assert.deepEqual(act.examinedDates, []);
  assert.equal(act.effectiveConfidence, "CONFIRMED");
});

test("schedule generation", () => {
  const nyse = bdc.getCalendar("US-NYSE");
  const regular = nyse.generateSchedule({ start: "2024-01-16", end: "2025-01-16", frequency: "QUARTERLY" });
  assert.deepEqual(regular.dates, ["2024-01-16", "2024-04-16", "2024-07-16", "2024-10-16", "2025-01-16"]);
  assert.ok(regular.periods.every((p) => !p.stub));

  const monthly = nyse.generateSchedule({ start: "2024-01-31", end: "2024-07-31", frequency: "MONTHLY" });
  assert.deepEqual(monthly.unadjustedDates, ["2024-01-31", "2024-02-29", "2024-03-31", "2024-04-30", "2024-05-31", "2024-06-30", "2024-07-31"]);
  assert.deepEqual(monthly.dates, ["2024-01-31", "2024-02-29", "2024-03-28", "2024-04-30", "2024-05-31", "2024-06-28", "2024-07-31"], "Good Friday and a Sunday roll back");

  const base = { start: "2024-01-15", end: "2024-12-01", frequency: "QUARTERLY" };
  const shortFinal = nyse.generateSchedule(base);
  assert.deepEqual(shortFinal.unadjustedDates, ["2024-01-15", "2024-04-15", "2024-07-15", "2024-10-15", "2024-12-01"]);
  assert.equal(shortFinal.dates[0], "2024-01-16", "MLK Day");
  assert.deepEqual(shortFinal.periods.map((p) => p.stub), [false, false, false, true]);
  assert.deepEqual(nyse.generateSchedule({ ...base, stub: "LONG_FINAL" }).unadjustedDates, ["2024-01-15", "2024-04-15", "2024-07-15", "2024-12-01"]);
  const shortInitial = nyse.generateSchedule({ ...base, stub: "SHORT_INITIAL" });
  assert.deepEqual(shortInitial.unadjustedDates, ["2024-01-15", "2024-03-01", "2024-06-01", "2024-09-01", "2024-12-01"]);
  assert.deepEqual(shortInitial.periods.map((p) => p.stub), [true, false, false, false]);
  assert.deepEqual(nyse.generateSchedule({ ...base, stub: "LONG_INITIAL" }).unadjustedDates, ["2024-01-15", "2024-06-01", "2024-09-01", "2024-12-01"]);
  assert.throws(() => nyse.generateSchedule({ ...base, stub: "NONE" }));

  const eom = nyse.generateSchedule({ start: "2024-02-29", end: "2024-08-31", frequency: "QUARTERLY", roll: "EOM", convention: "UNADJUSTED" });
  assert.deepEqual(eom.dates, ["2024-02-29", "2024-05-31", "2024-08-31"]);
  const imm = nyse.generateSchedule({ start: "2024-03-20", end: "2024-09-18", frequency: "QUARTERLY", roll: "IMM" });
  assert.deepEqual(imm.unadjustedDates, ["2024-03-20", "2024-06-19", "2024-09-18"]);
  assert.deepEqual(imm.dates, ["2024-03-20", "2024-06-20", "2024-09-18"], "Juneteenth");
  assert.deepEqual(nyse.generateSchedule({ start: "2024-01-02", end: "2024-01-23", frequency: "WEEKLY" }).dates, ["2024-01-02", "2024-01-09", "2024-01-16", "2024-01-23"]);
  assert.throws(() => nyse.generateSchedule({ start: "2024-01-02", end: "2024-01-23", frequency: "WEEKLY", roll: "EOM" }));
  assert.throws(() => nyse.generateSchedule({ start: "2024-01-23", end: "2024-01-02", frequency: "WEEKLY" }));
  for (const stub of ["SHORT_FINAL", "LONG_FINAL", "SHORT_INITIAL", "LONG_INITIAL"] as const) {
    const single = nyse.generateSchedule({ start: "2024-01-16", end: "2024-02-15", frequency: "QUARTERLY", stub });
    assert.deepEqual(single.dates, ["2024-01-16", "2024-02-15"], stub);
    assert.ok(single.periods[0]!.stub);
  }
  assert.equal(nyse.generateSchedule({ start: "2028-01-18", end: "2029-01-18", frequency: "QUARTERLY" }).effectiveConfidence, "PROJECTED");

  const joint = bdc.getJointCalendar(["US-NYSE", "GB-LSE"]);
  assert.deepEqual(joint.generateSchedule({ start: "2024-05-24", end: "2024-08-24", frequency: "MONTHLY", convention: "FOLLOWING" }).dates, ["2024-05-24", "2024-06-24", "2024-07-24", "2024-08-27"]);
  const tsx = bdc.getCalendar("CA-TSX");
  assert.throws(() => tsx.generateSchedule({ start: "2001-09-11", end: "2002-03-11", frequency: "QUARTERLY" }), bdc.UnresolvedDateError);
  assert.throws(() => nyse.generateSchedule({ start: "2030-10-01", end: "2031-04-01", frequency: "QUARTERLY" }), bdc.OutsideCoverageError);
});
