/**
 * Financial date operations with path confidence: adjustment conventions, business-day offsets,
 * month advancement, last business day of month, tenor addition, year fractions and schedule
 * generation. Every result carries the weakest business-day confidence of every date examined.
 */
import { addMonths, lastDayOfMonth, plusDays, sameMonth } from "./dates.ts";
import {
  dayCount as parseDayCount,
  dayCountDays,
  frequency as parseFrequency,
  roll,
  Tenor,
  yearFraction as calendarFreeYearFraction,
  type DayCount,
  type Frequency,
  type RollConvention,
  type StubConvention,
} from "./finance.ts";
import type { Confidence } from "./trust.ts";
import type { BusinessCalendar } from "./calendar.ts";
import { MAX_SEARCH_DAYS } from "./calendar.ts";

export type BusinessDayConvention = "UNADJUSTED" | "FOLLOWING" | "MODIFIED_FOLLOWING" | "PRECEDING" | "MODIFIED_PRECEDING";
export const BUSINESS_DAY_CONVENTIONS: readonly BusinessDayConvention[] = ["UNADJUSTED", "FOLLOWING", "MODIFIED_FOLLOWING", "PRECEDING", "MODIFIED_PRECEDING"];

export type DateOperation = "ADJUST" | "BUSINESS_DAY_OFFSET" | "ADVANCE_MONTHS" | "LAST_BUSINESS_DAY_OF_MONTH" | "ADD_TENOR";

export interface DateOperationResult {
  readonly originalDate: string;
  readonly resultDate: string;
  readonly operation: DateOperation;
  readonly convention: BusinessDayConvention | null;
  readonly businessDayOffset: number | null;
  readonly monthOffset: number | null;
  readonly preserveEndOfMonth: boolean;
  readonly effectiveConfidence: Confidence;
  readonly examinedDates: readonly string[];
  /** Canonical tenor spelling for `ADD_TENOR`; null for every other operation. */
  readonly tenor: string | null;
}

export interface YearFractionResult {
  readonly start: string;
  readonly end: string;
  readonly dayCount: DayCount;
  readonly days: number;
  readonly yearFraction: number;
  readonly effectiveConfidence: Confidence;
  readonly examinedDates: readonly string[];
}

export interface ScheduleDefinition {
  readonly start: string;
  readonly end: string;
  readonly frequency: string | Frequency;
  readonly stub?: StubConvention;
  readonly roll?: RollConvention;
  readonly convention?: string;
}

export interface SchedulePeriod {
  readonly unadjustedStart: string;
  readonly unadjustedEnd: string;
  readonly start: string;
  readonly end: string;
  readonly stub: boolean;
}

export interface Schedule {
  readonly definition: Required<ScheduleDefinition> & { frequency: Frequency; convention: BusinessDayConvention };
  readonly periods: readonly SchedulePeriod[];
  readonly effectiveConfidence: Confidence;
  readonly examinedDates: readonly string[];
  /** Adjusted boundary dates, first start through last end. */
  readonly dates: readonly string[];
  readonly unadjustedDates: readonly string[];
}

export interface MemberClose {
  readonly calendarId: string;
  readonly timezone: string | null;
  readonly localTime: string;
}

export function convention(value: string): BusinessDayConvention {
  const upper = String(value).toUpperCase() as BusinessDayConvention;
  if (!BUSINESS_DAY_CONVENTIONS.includes(upper)) throw new TypeError(`unknown business-day convention: ${value}`);
  return upper;
}

class Tracker {
  readonly examined: string[] = [];
  private readonly seen = new Set<string>();
  confidence: Confidence = "CONFIRMED";

  private readonly calendar: BusinessCalendar;

  constructor(calendar: BusinessCalendar) {
    this.calendar = calendar;
  }

  private include(date: string, require: boolean): void {
    if (!this.seen.has(date)) {
      this.seen.add(date);
      this.examined.push(date);
    }
    const assessment = this.calendar.assessment(date);
    if (assessment.businessDayConfidence === "UNKNOWN") this.confidence = "UNKNOWN";
    else if (assessment.businessDayConfidence === "PROJECTED" && this.confidence === "CONFIRMED") this.confidence = "PROJECTED";
    if (require) this.calendar.requireResolved(date, "BUSINESS_DAY");
  }

  identity(date: string): void {
    this.include(date, false);
  }

  requireResolved(date: string): void {
    this.include(date, true);
  }

  isBusinessDay(date: string): boolean {
    this.include(date, true);
    return this.calendar.isBusinessDay(date);
  }

  result(
    original: string,
    result: string,
    operation: DateOperation,
    conventionValue: BusinessDayConvention | null = null,
    businessDayOffset: number | null = null,
    monthOffset: number | null = null,
    preserveEndOfMonth = false,
    tenor: string | null = null,
  ): DateOperationResult {
    return {
      originalDate: original,
      resultDate: result,
      operation,
      convention: conventionValue,
      businessDayOffset,
      monthOffset,
      preserveEndOfMonth,
      effectiveConfidence: this.confidence,
      examinedDates: [...this.examined],
      tenor,
    };
  }
}

function search(calendar: BusinessCalendar, date: string, direction: 1 | -1, tracker: Tracker): string {
  let candidate = date;
  for (let i = 0; i < MAX_SEARCH_DAYS; i++) {
    if (tracker.isBusinessDay(candidate)) return candidate;
    candidate = plusDays(candidate, direction);
  }
  throw new Error(`No business day within ${MAX_SEARCH_DAYS} days of ${date}`);
}

function adjustWith(calendar: BusinessCalendar, date: string, conventionValue: BusinessDayConvention, tracker: Tracker): string {
  if (conventionValue === "UNADJUSTED") {
    tracker.identity(date);
    return date;
  }
  const firstDirection: 1 | -1 = conventionValue === "FOLLOWING" || conventionValue === "MODIFIED_FOLLOWING" ? 1 : -1;
  const first = search(calendar, date, firstDirection, tracker);
  const modified = conventionValue === "MODIFIED_FOLLOWING" || conventionValue === "MODIFIED_PRECEDING";
  return modified && !sameMonth(first, date) ? search(calendar, date, firstDirection === 1 ? -1 : 1, tracker) : first;
}

function lastBusinessDay(calendar: BusinessCalendar, date: string, tracker: Tracker): string {
  let candidate = lastDayOfMonth(date);
  while (sameMonth(candidate, date)) {
    if (tracker.isBusinessDay(candidate)) return candidate;
    candidate = plusDays(candidate, -1);
  }
  throw new Error(`No business day in ${date.slice(0, 7)}`);
}

export function adjustDetailed(calendar: BusinessCalendar, date: string, conventionValue: string): DateOperationResult {
  const c = convention(conventionValue);
  const tracker = new Tracker(calendar);
  const result = adjustWith(calendar, date, c, tracker);
  return tracker.result(date, result, "ADJUST", c);
}

export function businessDayOffsetDetailed(calendar: BusinessCalendar, date: string, offset: number): DateOperationResult {
  if (!Number.isInteger(offset)) throw new TypeError("offset must be an integer");
  const tracker = new Tracker(calendar);
  if (offset === 0) {
    tracker.identity(date);
    return tracker.result(date, date, "BUSINESS_DAY_OFFSET", null, 0);
  }
  let remaining = Math.abs(offset);
  const step = offset > 0 ? 1 : -1;
  let current = date;
  let guard = MAX_SEARCH_DAYS * remaining + MAX_SEARCH_DAYS;
  while (remaining > 0) {
    if (guard-- <= 0) throw new Error(`Could not find ${Math.abs(offset)} business days ${offset > 0 ? "after" : "before"} ${date}`);
    current = plusDays(current, step);
    if (tracker.isBusinessDay(current)) remaining--;
  }
  return tracker.result(date, current, "BUSINESS_DAY_OFFSET", null, offset);
}

export function advanceMonthsDetailed(
  calendar: BusinessCalendar,
  date: string,
  months: number,
  conventionValue: string,
  preserveEndOfMonth = false,
): DateOperationResult {
  if (!Number.isInteger(months)) throw new TypeError("months must be an integer");
  const c = convention(conventionValue);
  if (months === 0 && c === "UNADJUSTED" && !preserveEndOfMonth) {
    const identity = new Tracker(calendar);
    identity.identity(date);
    return identity.result(date, date, "ADVANCE_MONTHS", c, null, 0);
  }
  const tracker = new Tracker(calendar);
  tracker.requireResolved(date);
  const nominal = addMonths(date, months);
  let result: string;
  if (preserveEndOfMonth && date === lastBusinessDay(calendar, date, tracker)) {
    result = lastBusinessDay(calendar, nominal, tracker);
  } else if (c === "UNADJUSTED") {
    tracker.requireResolved(nominal);
    result = nominal;
  } else {
    result = adjustWith(calendar, nominal, c, tracker);
  }
  return tracker.result(date, result, "ADVANCE_MONTHS", c, null, months, preserveEndOfMonth);
}

export function lastBusinessDayOfMonthDetailed(calendar: BusinessCalendar, date: string): DateOperationResult {
  const tracker = new Tracker(calendar);
  tracker.requireResolved(date);
  const result = lastBusinessDay(calendar, date, tracker);
  return tracker.result(date, result, "LAST_BUSINESS_DAY_OF_MONTH");
}

/**
 * Add a market tenor. Day/week tenors shift calendar days then adjust; month/year tenors advance
 * months then adjust; business-day tenors (`T+n`) walk business days and ignore the convention.
 */
export function addTenorDetailed(
  calendar: BusinessCalendar,
  date: string,
  tenorValue: string | Tenor,
  conventionValue: string,
  preserveEndOfMonth = false,
): DateOperationResult {
  const tenor = Tenor.parse(tenorValue);
  const c = convention(conventionValue);
  let base: DateOperationResult;
  let conventionOut: BusinessDayConvention | null;
  if (tenor.unit === "B") {
    base = businessDayOffsetDetailed(calendar, date, tenor.amount);
    conventionOut = null;
  } else if (tenor.isMonthBased) {
    base = advanceMonthsDetailed(calendar, date, tenor.months, c, preserveEndOfMonth);
    conventionOut = c;
  } else {
    const tracker = new Tracker(calendar);
    const nominal = plusDays(date, tenor.days);
    const result = adjustWith(calendar, nominal, c, tracker);
    base = tracker.result(date, result, "ADD_TENOR", c);
    conventionOut = c;
  }
  return { ...base, operation: "ADD_TENOR", convention: conventionOut, tenor: tenor.toString() };
}

/** A year fraction; `BUS_252` counts the calendar's business days in `[start, end)`. */
export function yearFractionDetailed(calendar: BusinessCalendar, start: string, end: string, conventionValue: string | DayCount): YearFractionResult {
  const dc = parseDayCount(conventionValue);
  if (dc !== "BUS_252") {
    return {
      start,
      end,
      dayCount: dc,
      days: dayCountDays(dc, start, end),
      yearFraction: calendarFreeYearFraction(dc, start, end),
      effectiveConfidence: "CONFIRMED",
      examinedDates: [],
    };
  }
  if (end < start) {
    const forward = yearFractionDetailed(calendar, end, start, dc);
    return { ...forward, start, end, days: forward.days === 0 ? 0 : -forward.days, yearFraction: forward.yearFraction === 0 ? 0 : -forward.yearFraction };
  }
  const tracker = new Tracker(calendar);
  let count = 0;
  for (let day = start; day < end; day = plusDays(day, 1)) if (tracker.isBusinessDay(day)) count++;
  return { start, end, dayCount: dc, days: count, yearFraction: count / 252, effectiveConfidence: tracker.confidence, examinedDates: [...tracker.examined] };
}

const MAX_PERIODS = 10_000;

type ResolvedDefinition = Schedule["definition"];

function resolveDefinition(definition: ScheduleDefinition): ResolvedDefinition {
  const resolved: ResolvedDefinition = {
    start: definition.start,
    end: definition.end,
    frequency: parseFrequency(definition.frequency),
    stub: (definition.stub ?? "SHORT_FINAL").toUpperCase() as StubConvention,
    roll: (definition.roll ?? "DAY_OF_MONTH").toUpperCase() as RollConvention,
    convention: convention(definition.convention ?? "MODIFIED_FOLLOWING"),
  };
  if (!["NONE", "SHORT_FINAL", "LONG_FINAL", "SHORT_INITIAL", "LONG_INITIAL"].includes(resolved.stub)) throw new TypeError(`unknown stub convention: ${definition.stub}`);
  if (!["DAY_OF_MONTH", "EOM", "IMM"].includes(resolved.roll)) throw new TypeError(`unknown roll convention: ${definition.roll}`);
  if (resolved.start >= resolved.end) throw new RangeError(`schedule start ${resolved.start} must precede end ${resolved.end}`);
  if (!resolved.frequency.tenor.isMonthBased && resolved.roll !== "DAY_OF_MONTH") {
    throw new TypeError(`${resolved.roll} applies to month-based frequencies only, not ${resolved.frequency.name}`);
  }
  return resolved;
}

function rollFrom(definition: ResolvedDefinition, anchor: string, periods: number): string {
  const tenor = definition.frequency.tenor;
  return tenor.isMonthBased ? roll(definition.roll, anchor, tenor.months * periods) : plusDays(anchor, tenor.days * periods);
}

/** The unadjusted boundary dates of a schedule, start through end. */
export function unadjustedScheduleDates(input: ScheduleDefinition): string[] {
  const definition = resolveDefinition(input);
  const backward = definition.stub === "SHORT_INITIAL" || definition.stub === "LONG_INITIAL";
  const anchor = backward ? definition.end : definition.start;
  const limit = backward ? definition.start : definition.end;
  const direction = backward ? -1 : 1;
  const dates = [anchor];
  let rolled: string | null = null;
  for (let k = 1; k <= MAX_PERIODS; k++) {
    rolled = rollFrom(definition, anchor, direction * k);
    if (backward ? rolled <= limit : rolled >= limit) break;
    dates.push(rolled);
  }
  if (rolled === null || (backward ? rolled > limit : rolled < limit)) {
    throw new RangeError(`Schedule from ${definition.start} to ${definition.end} exceeds ${MAX_PERIODS} periods`);
  }
  if (rolled !== limit) {
    if (definition.stub === "NONE") {
      throw new RangeError(`Schedule end ${definition.end} is not on the ${definition.frequency.name} cycle from ${definition.start} and the stub convention is NONE`);
    }
    if ((definition.stub === "LONG_FINAL" || definition.stub === "LONG_INITIAL") && dates.length > 1) dates.pop();
  }
  dates.push(limit);
  if (backward) dates.reverse();
  return dates;
}

function stubIndex(definition: ResolvedDefinition, dates: string[]): number {
  if (dates.length < 2) return -1;
  const backward = definition.stub === "SHORT_INITIAL" || definition.stub === "LONG_INITIAL";
  const index = backward ? 0 : dates.length - 2;
  const periodStart = dates[index]!;
  const periodEnd = dates[index + 1]!;
  if (backward) return rollFrom(definition, periodEnd, -1) === periodStart ? -1 : index;
  return rollFrom(definition, periodStart, 1) === periodEnd ? -1 : index;
}

/** Generate a periodic schedule on `calendar` with path confidence. */
export function schedule(calendar: BusinessCalendar, input: ScheduleDefinition): Schedule {
  const definition = resolveDefinition(input);
  const unadjusted = unadjustedScheduleDates(definition);
  const tracker = new Tracker(calendar);
  const adjusted = unadjusted.map((date) => adjustWith(calendar, date, definition.convention, tracker));
  const stubAt = stubIndex(definition, unadjusted);
  const periods: SchedulePeriod[] = [];
  for (let i = 0; i + 1 < unadjusted.length; i++) {
    periods.push({ unadjustedStart: unadjusted[i]!, unadjustedEnd: unadjusted[i + 1]!, start: adjusted[i]!, end: adjusted[i + 1]!, stub: i === stubAt });
  }
  return {
    definition,
    periods,
    effectiveConfidence: tracker.confidence,
    examinedDates: [...tracker.examined],
    dates: periods.length ? [periods[0]!.start, ...periods.map((p) => p.end)] : [],
    unadjustedDates: periods.length ? [periods[0]!.unadjustedStart, ...periods.map((p) => p.unadjustedEnd)] : [],
  };
}
