/**
 * The Query API: one published calendar (`SingleCalendar`) or several queried jointly
 * (`JointCalendar`). Semantics follow `spec/SPEC.md#query-api`; the Java `DateStream` and the
 * Python `BusinessCalendar` are the reference implementations and the parity fixtures hold all
 * three to the same answers.
 */
import { asIso, compareTime, plusDays, type DateLike } from "./dates.ts";
import { OutsideCoverageError, UnresolvedDateError } from "./errors.ts";
import { type CalendarData, loadCalendar, resolveId } from "./loader.ts";
import {
  COMPLETENESS_SCOPES,
  QUESTION_SCOPES,
  type CompletenessScope,
  type Confidence,
  type CoverageInterval,
  type CoverageQuality,
  type DateQuestion,
  type DayAssessment,
  type DayState,
  type Event,
  type EventDetails,
  type SpecialSession,
} from "./trust.ts";
import type { WeekendPolicy } from "./weekend.ts";
import * as operations from "./operations.ts";
import type { DateOperationResult, MemberClose, Schedule, ScheduleDefinition, YearFractionResult } from "./operations.ts";
import type { DayCount, Tenor } from "./finance.ts";

/** Bounded searches never walk further than this many days for a single business day. */
export const MAX_SEARCH_DAYS = 366;

export interface DateRange {
  readonly start: string;
  readonly end: string;
}

function scheduledState(events: readonly Event[]): DayState {
  if (events.some((e) => e.type === "CLOSED" || e.type === "WEEKEND")) return "CLOSED";
  if (events.some((e) => e.type === "EARLY_CLOSE")) return "EARLY_CLOSE";
  return "OPEN";
}

function worst(values: Confidence[]): Confidence {
  if (values.includes("UNKNOWN")) return "UNKNOWN";
  return values.includes("PROJECTED") ? "PROJECTED" : "CONFIRMED";
}

function confidence(
  completeness: Record<CompletenessScope, CoverageQuality>,
  question: DateQuestion,
  rawProjected: boolean,
  explicit: boolean,
  legacyProjected: boolean,
): Confidence {
  if (!explicit) return legacyProjected || rawProjected ? "PROJECTED" : "CONFIRMED";
  const qualities = QUESTION_SCOPES[question].map((scope) => completeness[scope]);
  if (qualities.includes("INCOMPLETE")) return "UNKNOWN";
  if (qualities.includes("PROJECTED") || rawProjected) return "PROJECTED";
  return "CONFIRMED";
}

export abstract class BusinessCalendar {
  abstract readonly calendarId: string;
  abstract readonly range: DateRange;
  abstract readonly verifiedThrough: string | null;
  abstract readonly coverageIntervals: readonly CoverageInterval[];
  abstract readonly timezone: string | null;

  abstract eventsOn(date: DateLike): Event[];
  abstract eventsInRange(start: DateLike, end: DateLike): Event[];
  abstract isBusinessDay(date: DateLike): boolean;

  /**
   * Whether trades are struck on this date: a business day, or a non-business day carrying a
   * `SPECIAL_SESSION` (Diwali Muhurat trading on an exchange holiday, say). Date arithmetic never
   * uses it. It needs the same scopes as `isBusinessDay`.
   */
  isTradingDay(date: DateLike): boolean {
    const day = asIso(date);
    if (this.isBusinessDay(day)) return true;
    return this.eventsOn(day).some((e) => e.type === "SPECIAL_SESSION");
  }

  /**
   * The special sessions held on a date, ordered by open time then close time; empty on most
   * dates. Times are local to the calendar's timezone. Session detail needs every scope, like
   * `closeTime`.
   */
  specialSessions(date: DateLike): SpecialSession[] {
    const day = asIso(date);
    this.requireResolved(day);
    return this.eventsOn(day)
      .filter((e) => e.type === "SPECIAL_SESSION")
      .sort((a, b) => compareTime(a.openTime!, b.openTime!) || compareTime(a.closeTime!, b.closeTime!))
      .map((e) => ({
        calendarId: this.calendarId,
        timezone: this.timezone,
        openTime: e.openTime!,
        closeTime: e.closeTime!,
        key: e.key,
        description: e.description,
        status: e.status,
      }));
  }

  /** The first of `eventsOn`, or null. */
  eventOn(date: DateLike): Event | null {
    return this.eventsOn(date)[0] ?? null;
  }

  // --- Navigation --------------------------------------------------------------------------

  /** The first business day strictly after `date`. */
  nextBusinessDay(date: DateLike): string {
    let candidate = plusDays(asIso(date), 1);
    for (let i = 0; i < MAX_SEARCH_DAYS; i++) {
      if (this.isBusinessDay(candidate)) return candidate;
      candidate = plusDays(candidate, 1);
    }
    throw new Error(`No business day within ${MAX_SEARCH_DAYS} days after ${asIso(date)}`);
  }

  /** The last business day strictly before `date`. */
  previousBusinessDay(date: DateLike): string {
    let candidate = plusDays(asIso(date), -1);
    for (let i = 0; i < MAX_SEARCH_DAYS; i++) {
      if (this.isBusinessDay(candidate)) return candidate;
      candidate = plusDays(candidate, -1);
    }
    throw new Error(`No business day within ${MAX_SEARCH_DAYS} days before ${asIso(date)}`);
  }

  prevBusinessDay(date: DateLike): string {
    return this.previousBusinessDay(date);
  }

  /**
   * The `n`-th business day from `date`; `n = 0` returns `date` unchanged, the starting date is
   * never counted, so T+1 from a Friday is the following Monday.
   */
  addBusinessDays(date: DateLike, n: number): string {
    const start = asIso(date);
    if (!Number.isInteger(n)) throw new TypeError("n must be an integer");
    if (n === 0) return start;
    let remaining = Math.abs(n);
    const step = n > 0 ? 1 : -1;
    let current = start;
    let guard = MAX_SEARCH_DAYS * remaining + MAX_SEARCH_DAYS;
    while (remaining > 0) {
      if (guard-- <= 0) throw new Error(`Could not find ${Math.abs(n)} business days ${n > 0 ? "after" : "before"} ${start}`);
      current = plusDays(current, step);
      if (this.isBusinessDay(current)) remaining--;
    }
    return current;
  }

  nthBusinessDay(date: DateLike, n: number): string {
    return this.addBusinessDays(date, n);
  }

  // --- Financial operations ----------------------------------------------------------------

  adjust(date: DateLike, convention: string): string {
    return this.adjustDetailed(date, convention).resultDate;
  }

  adjustDetailed(date: DateLike, convention: string): DateOperationResult {
    return operations.adjustDetailed(this, asIso(date), convention);
  }

  businessDayOffset(date: DateLike, offset: number): string {
    return this.businessDayOffsetDetailed(date, offset).resultDate;
  }

  businessDayOffsetDetailed(date: DateLike, offset: number): DateOperationResult {
    return operations.businessDayOffsetDetailed(this, asIso(date), offset);
  }

  advanceMonths(date: DateLike, months: number, convention: string, preserveEndOfMonth = false): string {
    return this.advanceMonthsDetailed(date, months, convention, preserveEndOfMonth).resultDate;
  }

  advanceMonthsDetailed(date: DateLike, months: number, convention: string, preserveEndOfMonth = false): DateOperationResult {
    return operations.advanceMonthsDetailed(this, asIso(date), months, convention, preserveEndOfMonth);
  }

  lastBusinessDayOfMonth(date: DateLike): string {
    return this.lastBusinessDayOfMonthDetailed(date).resultDate;
  }

  lastBusinessDayOfMonthDetailed(date: DateLike): DateOperationResult {
    return operations.lastBusinessDayOfMonthDetailed(this, asIso(date));
  }

  /** Add a market tenor (`3M`, `2W`, `1Y`, `T+2`); business-day tenors ignore the convention. */
  addTenor(date: DateLike, tenor: string | Tenor, convention = "MODIFIED_FOLLOWING", preserveEndOfMonth = false): string {
    return this.addTenorDetailed(date, tenor, convention, preserveEndOfMonth).resultDate;
  }

  addTenorDetailed(date: DateLike, tenor: string | Tenor, convention = "MODIFIED_FOLLOWING", preserveEndOfMonth = false): DateOperationResult {
    return operations.addTenorDetailed(this, asIso(date), tenor, convention, preserveEndOfMonth);
  }

  /** Year fraction under a day-count convention; `BUS_252` counts this calendar's business days. */
  yearFraction(start: DateLike, end: DateLike, convention: string | DayCount): number {
    return this.yearFractionDetailed(start, end, convention).yearFraction;
  }

  yearFractionDetailed(start: DateLike, end: DateLike, convention: string | DayCount): YearFractionResult {
    return operations.yearFractionDetailed(this, asIso(start, "start"), asIso(end, "end"), convention);
  }

  /** Periodic coupon/payment/reset dates adjusted on this calendar, with path confidence. */
  generateSchedule(definition: ScheduleDefinition): Schedule {
    return operations.schedule(this, definition);
  }

  // --- Counting ----------------------------------------------------------------------------

  /** Business days in `[start, end]`, both endpoints included. */
  businessDaysBetween(start: DateLike, end: DateLike): number {
    const first = asIso(start, "start");
    const last = asIso(end, "end");
    if (first > last) throw new RangeError("from must not be after to");
    let count = 0;
    for (let day = first; day <= last; day = plusDays(day, 1)) if (this.isBusinessDay(day)) count++;
    return count;
  }

  businessDaysInRange(start: DateLike, end: DateLike): number {
    return this.businessDaysBetween(start, end);
  }

  eventCountInRange(start: DateLike, end: DateLike): number {
    const first = asIso(start, "start");
    const last = asIso(end, "end");
    if (first > last) throw new RangeError("from must not be after to");
    for (let day = first; day <= last; day = plusDays(day, 1)) this.requireResolved(day);
    return this.eventsInRange(first, last).length;
  }

  // --- Session detail ----------------------------------------------------------------------

  /** Local close time `HH:MM` of a shortened session, or null; the earliest when several apply. */
  closeTime(date: DateLike): string | null {
    const day = asIso(date);
    this.requireResolved(day);
    const times = this.eventsOn(day)
      .filter((e) => e.type === "EARLY_CLOSE" && e.closeTime)
      .map((e) => e.closeTime!);
    if (!times.length) return null;
    return times.reduce((a, b) => (compareTime(a, b) <= 0 ? a : b));
  }

  isEarlyClose(date: DateLike): boolean {
    return this.closeTime(date) !== null;
  }

  memberCloses(date: DateLike): MemberClose[] {
    const close = this.closeTime(date);
    return close ? [{ calendarId: this.calendarId, timezone: this.timezone, localTime: close }] : [];
  }

  /** How much the full session answer can be trusted; never throws. */
  status(date: DateLike): Confidence {
    const day = asIso(date);
    if (day < this.range.start || day > this.range.end) return "UNKNOWN";
    if (this.coverageIntervals.length) return this.assessment(day).effectiveConfidence;
    if (this.verifiedThrough && day > this.verifiedThrough) return "PROJECTED";
    return this.eventsOn(day).some((e) => e.status === "PROJECTED") ? "PROJECTED" : "CONFIRMED";
  }

  /** Raw retained provenance; `assessment` supplies effective confidence. */
  eventDetailsOn(date: DateLike): EventDetails[] {
    return this.eventsOn(date).map((event) => ({
      event,
      rawStatus: event.status,
      effectiveStatus: event.status,
      evidenceIds: [],
      nominalNativeDate: null,
      chronologyProfile: null,
      chronologyProvider: null,
      observationLineage: event.observedFrom ? [event.observedFrom, event.date] : [],
    }));
  }

  /** Actual, scheduled and completeness state of a date, in both views, without throwing. */
  assessment(date: DateLike): DayAssessment {
    const day = asIso(date);
    if (day < this.range.start || day > this.range.end) {
      return {
        date: day,
        state: "UNKNOWN",
        scheduledState: "UNKNOWN",
        effectiveConfidence: "UNKNOWN",
        completeness: { SCHEDULED_CLOSURES: "INCOMPLETE", EARLY_CLOSES: "INCOMPLETE", UNSCHEDULED_EXCEPTIONS: "INCOMPLETE" },
        evidenceIds: [],
        events: [],
        businessDayState: "UNKNOWN",
        businessDayConfidence: "UNKNOWN",
      };
    }
    const events = this.eventsOn(day);
    const scheduled = scheduledState(events);
    const explicit = this.coverageIntervals.length > 0;
    const completeness = {} as Record<CompletenessScope, CoverageQuality>;
    const evidence = new Set<string>();
    for (const scope of COMPLETENESS_SCOPES) {
      const matching = this.coverageIntervals.filter((i) => i.scope === scope && i.from <= day && day <= i.to);
      const qualities = matching.map((i) => i.quality);
      completeness[scope] = qualities.includes("INCOMPLETE")
        ? "INCOMPLETE"
        : qualities.includes("PROJECTED")
          ? "PROJECTED"
          : qualities.length
            ? qualities[0]!
            : explicit
              ? "INCOMPLETE"
              : "PROJECTED";
      for (const interval of matching) for (const id of interval.evidenceIds) evidence.add(id);
    }
    const legacyProjected = this.verifiedThrough !== null && day > this.verifiedThrough;
    const effective = confidence(completeness, "SESSION", events.some((e) => e.status === "PROJECTED"), explicit, legacyProjected);
    const businessDayConfidence = confidence(
      completeness,
      "BUSINESS_DAY",
      events.some((e) => e.status === "PROJECTED" && (e.type === "CLOSED" || e.type === "WEEKEND")),
      explicit,
      legacyProjected,
    );
    const details = this.eventDetailsOn(day).map((d) => ({ ...d, effectiveStatus: effective }));
    for (const detail of details) for (const id of detail.evidenceIds) evidence.add(id);
    return {
      date: day,
      state: effective === "UNKNOWN" ? "UNKNOWN" : scheduled,
      scheduledState: scheduled,
      effectiveConfidence: effective,
      completeness,
      evidenceIds: [...evidence].sort(),
      events: details,
      businessDayState: businessDayConfidence === "UNKNOWN" ? "UNKNOWN" : scheduled === "CLOSED" ? "CLOSED" : "OPEN",
      businessDayConfidence,
    };
  }

  // --- Convenience -------------------------------------------------------------------------

  /** Every CLOSED event in a calendar year. */
  holidays(year: number): Event[] {
    return this.eventsInRange(`${year}-01-01`, `${year}-12-31`).filter((e) => e.type === "CLOSED");
  }

  isSession(date: DateLike): boolean {
    return this.isBusinessDay(date);
  }

  sessionsInRange(start: DateLike, end: DateLike): string[] {
    const first = asIso(start, "start");
    const last = asIso(end, "end");
    if (first > last) throw new RangeError("from must not be after to");
    const sessions: string[] = [];
    for (let day = first; day <= last; day = plusDays(day, 1)) if (this.isBusinessDay(day)) sessions.push(day);
    return sessions;
  }

  // --- Internals ---------------------------------------------------------------------------

  checkRange(date: string): void {
    if (date < this.range.start || date > this.range.end) {
      throw new OutsideCoverageError(this.calendarId, date, this.range.start, this.range.end);
    }
  }

  /** Refuses an answer to `question` when a scope it depends on is incomplete on this date. */
  requireResolved(date: string, question: DateQuestion = "SESSION"): void {
    this.checkRange(date);
    const incomplete = this.incompleteScopes(date, question);
    if (incomplete.length) throw new UnresolvedDateError(this.calendarId, date, this.range.start, this.range.end, incomplete);
  }

  /**
   * The scopes among `question`'s that are incomplete on `date`, from the coverage intervals
   * alone: equivalent to `incompleteScopes(assessment(date), question)` without materialising
   * the assessment, which keeps day-by-day walks cheap.
   */
  incompleteScopes(date: string, question: DateQuestion = "SESSION"): CompletenessScope[] {
    if (date < this.range.start || date > this.range.end) return [...QUESTION_SCOPES[question]];
    const intervals = this.coverageIntervals;
    if (!intervals.length) return [];
    const incomplete: CompletenessScope[] = [];
    for (const scope of QUESTION_SCOPES[question]) {
      let covered = false;
      for (const interval of intervals) {
        if (interval.scope === scope && interval.from <= date && date <= interval.to) {
          covered = true;
          if (interval.quality === "INCOMPLETE") {
            incomplete.push(scope);
            break;
          }
        }
      }
      if (!covered) incomplete.push(scope);
    }
    return incomplete;
  }
}

/** One published calendar, answered from the bundled data. */
export class SingleCalendar extends BusinessCalendar {
  readonly calendarId: string;
  readonly range: DateRange;
  readonly verifiedThrough: string | null;
  readonly coverageIntervals: readonly CoverageInterval[];
  readonly timezone: string | null;
  readonly name: string;
  readonly kind: string;
  readonly weekendPolicy: WeekendPolicy;
  private readonly data: CalendarData;

  constructor(data: CalendarData) {
    super();
    this.data = data;
    this.calendarId = data.calendarId;
    this.range = { start: data.rangeFrom, end: data.rangeTo };
    this.verifiedThrough = data.verifiedThrough;
    this.coverageIntervals = data.coverageIntervals;
    this.timezone = data.timezone;
    this.name = data.name;
    this.kind = data.kind;
    this.weekendPolicy = data.weekendPolicy;
  }

  /**
   * Weekend rows are the ones dropped from the shipped data. The artifact carries a WEEKEND row
   * on exactly the policy weekends that no closure already covers (a closure wins over the
   * weekend row where both apply, a NOTABLE sits alongside it), so this reproduces it row for row.
   */
  private eventsOnUnchecked(day: string): Event[] {
    const rows = [...(this.data.eventsByDate.get(day) ?? [])];
    if (this.weekendPolicy.isWeekend(day) && !rows.some((e) => e.type === "CLOSED")) {
      const name = ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"][
        (((Date.UTC(Number(day.slice(0, 4)), Number(day.slice(5, 7)) - 1, Number(day.slice(8, 10))) / 86_400_000 + 3) % 7) + 7) % 7
      ]!;
      rows.push({ date: day, type: "WEEKEND", description: name, key: "weekend", sourceModule: "weekend_policy", observedFrom: null, closeTime: null, status: "CONFIRMED", openTime: null });
    }
    return rows;
  }

  eventsOn(date: DateLike): Event[] {
    const day = asIso(date);
    this.checkRange(day);
    return this.eventsOnUnchecked(day);
  }

  eventsInRange(start: DateLike, end: DateLike): Event[] {
    const first = asIso(start, "start");
    const last = asIso(end, "end");
    if (first > last) throw new RangeError("from must not be after to");
    this.checkRange(first);
    this.checkRange(last);
    const events: Event[] = [];
    for (let day = first; day <= last; day = plusDays(day, 1)) events.push(...this.eventsOnUnchecked(day));
    return events;
  }

  isBusinessDay(date: DateLike): boolean {
    const day = asIso(date);
    this.checkRange(day);
    this.requireResolved(day, "BUSINESS_DAY");
    if (this.weekendPolicy.isWeekend(day)) return false;
    const rows = this.data.eventsByDate.get(day);
    return !rows || !rows.some((e) => e.type === "CLOSED");
  }

  override eventDetailsOn(date: DateLike): EventDetails[] {
    const day = asIso(date);
    this.checkRange(day);
    const details = this.data.detailsByDate.get(day);
    if (!details) return super.eventDetailsOn(day);
    const result: EventDetails[] = [...details];
    for (const event of this.eventsOnUnchecked(day)) {
      if (event.type === "WEEKEND") {
        result.push({ event, rawStatus: event.status, effectiveStatus: event.status, evidenceIds: [], nominalNativeDate: null, chronologyProfile: null, chronologyProvider: null, observationLineage: [] });
      }
    }
    return result;
  }
}

function qualify(member: BusinessCalendar, event: Event): Event {
  return { ...event, sourceModule: event.sourceModule === null ? member.calendarId : `${member.calendarId}/${event.sourceModule}` };
}

/** Several calendars queried as one: open only when every member trades. */
export class JointCalendar extends BusinessCalendar {
  readonly members: readonly BusinessCalendar[];
  readonly calendarId: string;
  readonly range: DateRange;

  constructor(members: BusinessCalendar[]) {
    super();
    if (!members.length) throw new TypeError("a joint calendar needs at least one member");
    this.members = [...members];
    this.calendarId = members.map((m) => m.calendarId).join("+");
    let start = "0000-01-01";
    let end = "9999-12-31";
    for (const member of members) {
      if (member.range.start > start) start = member.range.start;
      if (member.range.end < end) end = member.range.end;
    }
    if (start > end) {
      throw new TypeError(`Calendars ${this.calendarId} have no overlapping covered range: ${members.map((m) => `${m.calendarId} ${m.range.start}..${m.range.end}`).join(", ")}`);
    }
    this.range = { start, end };
  }

  get verifiedThrough(): string | null {
    const values = this.members.map((m) => m.verifiedThrough).filter((v): v is string => v !== null);
    return values.length ? values.reduce((a, b) => (a < b ? a : b)) : null;
  }

  get coverageIntervals(): readonly CoverageInterval[] {
    return this.members.flatMap((m) => m.coverageIntervals);
  }

  get timezone(): string | null {
    return null;
  }

  eventsOn(date: DateLike): Event[] {
    const day = asIso(date);
    this.checkRange(day);
    return this.members.flatMap((m) => m.eventsOn(day).map((e) => qualify(m, e)));
  }

  eventsInRange(start: DateLike, end: DateLike): Event[] {
    const first = asIso(start, "start");
    const last = asIso(end, "end");
    if (first > last) throw new RangeError("from must not be after to");
    this.checkRange(first);
    this.checkRange(last);
    const events = this.members.flatMap((m) => m.eventsInRange(first, last).map((e) => qualify(m, e)));
    return events.map((e, i) => [e, i] as const).sort((a, b) => (a[0].date < b[0].date ? -1 : a[0].date > b[0].date ? 1 : a[1] - b[1])).map(([e]) => e);
  }

  isBusinessDay(date: DateLike): boolean {
    const day = asIso(date);
    this.checkRange(day);
    this.requireResolved(day, "BUSINESS_DAY");
    return this.members.every((m) => m.isBusinessDay(day));
  }

  /** A joint trading day is a date on which every member trades. */
  override isTradingDay(date: DateLike): boolean {
    const day = asIso(date);
    this.checkRange(day);
    this.requireResolved(day, "BUSINESS_DAY");
    return this.members.every((m) => m.isTradingDay(day));
  }

  /** Every member's special sessions in member order, each with its own calendar and timezone. */
  override specialSessions(date: DateLike): SpecialSession[] {
    const day = asIso(date);
    this.checkRange(day);
    this.requireResolved(day);
    return this.members.flatMap((m) => m.specialSessions(day));
  }

  /** The members that do not trade on the given date, in member order. */
  closedMembers(date: DateLike): BusinessCalendar[] {
    return this.members.filter((m) => !m.isBusinessDay(date));
  }

  override closeTime(date: DateLike): string | null {
    const day = asIso(date);
    this.checkRange(day);
    const times = this.members.map((m) => m.closeTime(day)).filter((t): t is string => t !== null);
    return times.length ? times.reduce((a, b) => (compareTime(a, b) <= 0 ? a : b)) : null;
  }

  override memberCloses(date: DateLike): MemberClose[] {
    const day = asIso(date);
    this.checkRange(day);
    this.requireResolved(day);
    const closes: MemberClose[] = [];
    for (const member of this.members) {
      const close = member.closeTime(day);
      if (close !== null) closes.push({ calendarId: member.calendarId, timezone: member.timezone, localTime: close });
    }
    return closes;
  }

  /** A date is unresolved for the joint when it is unresolved for any member. */
  override incompleteScopes(date: string, question: DateQuestion = "SESSION"): CompletenessScope[] {
    const incomplete: CompletenessScope[] = [];
    for (const member of this.members) {
      for (const scope of member.incompleteScopes(date, question)) if (!incomplete.includes(scope)) incomplete.push(scope);
    }
    return incomplete;
  }

  override status(date: DateLike): Confidence {
    return worst(this.members.map((m) => m.status(date)));
  }

  override assessment(date: DateLike): DayAssessment {
    const day = asIso(date);
    if (day < this.range.start || day > this.range.end) return super.assessment(day);
    const assessments = this.members.map((m) => m.assessment(day));
    const completeness = {} as Record<CompletenessScope, CoverageQuality>;
    for (const scope of COMPLETENESS_SCOPES) {
      const qualities = assessments.map((a) => a.completeness[scope]);
      completeness[scope] = qualities.includes("INCOMPLETE") ? "INCOMPLETE" : qualities.includes("PROJECTED") ? "PROJECTED" : "VERIFIED";
    }
    const evidence: string[] = [];
    for (const a of assessments) for (const id of a.evidenceIds) if (!evidence.includes(id)) evidence.push(id);
    const scheduled: DayState = assessments.some((a) => a.scheduledState === "CLOSED")
      ? "CLOSED"
      : assessments.some((a) => a.scheduledState === "EARLY_CLOSE")
        ? "EARLY_CLOSE"
        : "OPEN";
    const effective = worst(assessments.map((a) => a.effectiveConfidence));
    const businessDayConfidence = worst(assessments.map((a) => a.businessDayConfidence));
    const events: EventDetails[] = [];
    this.members.forEach((member, i) => {
      for (const detail of assessments[i]!.events) events.push({ ...detail, event: qualify(member, detail.event) });
    });
    return {
      date: day,
      state: effective === "UNKNOWN" ? "UNKNOWN" : scheduled,
      scheduledState: scheduled,
      effectiveConfidence: effective,
      completeness,
      evidenceIds: evidence,
      events,
      businessDayState: businessDayConfidence === "UNKNOWN" ? "UNKNOWN" : assessments.some((a) => a.businessDayState === "CLOSED") ? "CLOSED" : "OPEN",
      businessDayConfidence,
    };
  }
}

// --- Factories -------------------------------------------------------------------------------

const instances = new Map<string, SingleCalendar>();

/** The calendar with the given id, MIC or alias (`"US-NYSE"`, `"XNYS"`, `"xnys"`). */
export function getCalendar(calendarId: string): SingleCalendar {
  const canonical = resolveId(calendarId);
  let calendar = instances.get(canonical);
  if (!calendar) {
    calendar = new SingleCalendar(loadCalendar(canonical));
    instances.set(canonical, calendar);
  }
  return calendar;
}

/** The joint of several calendars: open only where every member is open; one id returns that calendar. */
export function getJointCalendar(calendarIds: readonly string[]): BusinessCalendar {
  if (!calendarIds.length) throw new TypeError("a joint calendar needs at least one member");
  const members = calendarIds.map(getCalendar);
  return members.length === 1 ? members[0]! : new JointCalendar(members);
}
