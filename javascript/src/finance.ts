/**
 * Calendar-free financial date vocabulary: tenors, frequencies, stub and roll conventions, IMM
 * dates and day-count conventions (2006 ISDA Definitions §4.16). Mirrors Java `com.bdc.finance`.
 */
import {
  addMonths,
  daysBetween,
  dayOfMonth,
  fromParts,
  isLastDayOfMonth,
  lastDayOfMonth,
  lengthOfYear,
  month,
  parts,
  plusDays,
  weekday,
  year,
} from "./dates.ts";

export type TenorUnit = "D" | "W" | "M" | "Y" | "B";

/** A market tenor such as `3M`, `2W`, `1Y`, `10D` or `T+2` (`2B`). */
export class Tenor {
  readonly amount: number;
  readonly unit: TenorUnit;

  constructor(amount: number, unit: TenorUnit) {
    if (!Number.isInteger(amount)) throw new TypeError("tenor amount must be an integer");
    this.amount = amount;
    this.unit = unit;
  }

  static parse(text: string | Tenor): Tenor {
    if (text instanceof Tenor) return text;
    const value = String(text).trim().toUpperCase();
    const settlement = /^T([+-]\d{1,5})$/.exec(value);
    if (settlement) return new Tenor(Number(settlement[1]), "B");
    const match = /^([+-]?\d{1,5})([DWMYB])$/.exec(value);
    if (!match) throw new TypeError(`Not a tenor: '${text}' (expected e.g. 3M, 2W, 1Y, 10D, 2B or T+2)`);
    return new Tenor(Number(match[1]), match[2] as TenorUnit);
  }

  get isMonthBased(): boolean {
    return this.unit === "M" || this.unit === "Y";
  }

  get months(): number {
    if (this.unit === "M") return this.amount;
    if (this.unit === "Y") return this.amount * 12;
    throw new Error(`${this} is not month-based`);
  }

  get days(): number {
    if (this.unit === "D") return this.amount;
    if (this.unit === "W") return this.amount * 7;
    throw new Error(`${this} is not day-based`);
  }

  toString(): string {
    return `${this.amount}${this.unit}`;
  }
}

export type FrequencyName = "ANNUAL" | "SEMI_ANNUAL" | "QUARTERLY" | "BI_MONTHLY" | "MONTHLY" | "FOUR_WEEKLY" | "BI_WEEKLY" | "WEEKLY";

export interface Frequency {
  readonly name: FrequencyName;
  readonly tenor: Tenor;
  readonly periodsPerYear: number;
}

export const FREQUENCIES: Readonly<Record<FrequencyName, Frequency>> = {
  ANNUAL: { name: "ANNUAL", tenor: new Tenor(1, "Y"), periodsPerYear: 1 },
  SEMI_ANNUAL: { name: "SEMI_ANNUAL", tenor: new Tenor(6, "M"), periodsPerYear: 2 },
  QUARTERLY: { name: "QUARTERLY", tenor: new Tenor(3, "M"), periodsPerYear: 4 },
  BI_MONTHLY: { name: "BI_MONTHLY", tenor: new Tenor(2, "M"), periodsPerYear: 6 },
  MONTHLY: { name: "MONTHLY", tenor: new Tenor(1, "M"), periodsPerYear: 12 },
  FOUR_WEEKLY: { name: "FOUR_WEEKLY", tenor: new Tenor(4, "W"), periodsPerYear: 13 },
  BI_WEEKLY: { name: "BI_WEEKLY", tenor: new Tenor(2, "W"), periodsPerYear: 26 },
  WEEKLY: { name: "WEEKLY", tenor: new Tenor(1, "W"), periodsPerYear: 52 },
};

/** A frequency by name (`QUARTERLY`, case-insensitive) or by tenor (`3M`). */
export function frequency(value: string | Frequency): Frequency {
  if (typeof value !== "string") return value;
  const named = FREQUENCIES[value.toUpperCase() as FrequencyName];
  if (named) return named;
  const tenor = Tenor.parse(value);
  for (const candidate of Object.values(FREQUENCIES)) {
    if (candidate.tenor.amount === tenor.amount && candidate.tenor.unit === tenor.unit) return candidate;
  }
  throw new TypeError(`No standard frequency with tenor ${tenor}`);
}

export type StubConvention = "NONE" | "SHORT_FINAL" | "LONG_FINAL" | "SHORT_INITIAL" | "LONG_INITIAL";
export type RollConvention = "DAY_OF_MONTH" | "EOM" | "IMM";

/** The third Wednesday of the month. */
export function immDate(y: number, m: number): string {
  const first = fromParts(y, m, 1);
  const offset = (2 - weekday(first) + 7) % 7; // Wednesday is weekday 2
  return plusDays(first, offset + 14);
}

export function isImm(date: string): boolean {
  return date === immDate(year(date), month(date));
}

export function isQuarterlyImm(date: string): boolean {
  return isImm(date) && month(date) % 3 === 0;
}

/** The first IMM date strictly after `date`. */
export function nextImm(date: string): string {
  let candidate = immDate(year(date), month(date));
  if (candidate <= date) {
    const following = addMonths(date, 1);
    candidate = immDate(year(following), month(following));
  }
  return candidate;
}

export function nextQuarterlyImm(date: string): string {
  let candidate = nextImm(date);
  while (month(candidate) % 3 !== 0) candidate = nextImm(candidate);
  return candidate;
}

/** The unadjusted date `months` months from `anchor` under a roll convention (clipping from the anchor). */
export function roll(convention: RollConvention, anchor: string, months: number): string {
  const shifted = addMonths(anchor, months);
  if (convention === "DAY_OF_MONTH") return shifted;
  if (convention === "EOM") return lastDayOfMonth(shifted);
  return immDate(year(shifted), month(shifted));
}

export type DayCount =
  | "ACT_360"
  | "ACT_365F"
  | "ACT_ACT_ISDA"
  | "ACT_ACT_ICMA"
  | "THIRTY_360_BOND"
  | "THIRTY_E_360"
  | "THIRTY_E_360_ISDA"
  | "BUS_252";

export const DAY_COUNTS: readonly DayCount[] = [
  "ACT_360",
  "ACT_365F",
  "ACT_ACT_ISDA",
  "ACT_ACT_ICMA",
  "THIRTY_360_BOND",
  "THIRTY_E_360",
  "THIRTY_E_360_ISDA",
  "BUS_252",
];

export function dayCount(value: string): DayCount {
  const normalised = value.toUpperCase().replace(/\//g, "_") as DayCount;
  if (!DAY_COUNTS.includes(normalised)) throw new TypeError(`unknown day-count convention: ${value}`);
  return normalised;
}

export function isCalendarFree(convention: DayCount): boolean {
  return convention !== "BUS_252" && convention !== "ACT_ACT_ICMA";
}

function thirty360(start: string, end: string, d1: number, d2: number): number {
  const [y1, m1] = parts(start);
  const [y2, m2] = parts(end);
  return 360 * (y2 - y1) + 30 * (m2 - m1) + (d2 - d1);
}

/** The convention's day count for `[start, end]` (negative when reversed). */
export function dayCountDays(convention: DayCount, start: string, end: string, endIsTermination = false): number {
  if (end < start) return -dayCountDays(convention, end, start, endIsTermination);
  switch (convention) {
    case "ACT_360":
    case "ACT_365F":
    case "ACT_ACT_ISDA":
    case "ACT_ACT_ICMA":
      return daysBetween(start, end);
    case "THIRTY_360_BOND": {
      const d1 = Math.min(dayOfMonth(start), 30);
      const d2 = dayOfMonth(end) === 31 && d1 === 30 ? 30 : dayOfMonth(end);
      return thirty360(start, end, d1, d2);
    }
    case "THIRTY_E_360":
      return thirty360(start, end, Math.min(dayOfMonth(start), 30), Math.min(dayOfMonth(end), 30));
    case "THIRTY_E_360_ISDA": {
      const d1 = isLastDayOfMonth(start) ? 30 : dayOfMonth(start);
      const keepFebruary = endIsTermination && month(end) === 2;
      const d2 = dayOfMonth(end) === 31 || (isLastDayOfMonth(end) && !keepFebruary) ? 30 : dayOfMonth(end);
      return thirty360(start, end, d1, d2);
    }
    case "BUS_252":
      throw new TypeError("BUS/252 needs a business calendar: use calendar.yearFraction");
  }
}

function actActIsda(start: string, end: string): number {
  if (end < start) return -actActIsda(end, start);
  if (year(start) === year(end)) return daysBetween(start, end) / lengthOfYear(year(start));
  let fraction = 0;
  let cursor = start;
  while (year(cursor) < year(end)) {
    const nextYear = fromParts(year(cursor) + 1, 1, 1);
    fraction += daysBetween(cursor, nextYear) / lengthOfYear(year(cursor));
    cursor = nextYear;
  }
  return fraction + daysBetween(cursor, end) / lengthOfYear(year(end));
}

export interface YearFractionOptions {
  /** 30E/360 ISDA only: whether `end` is the instrument's termination date. */
  endIsTermination?: boolean;
  /** ACT/ACT ICMA only: the regular (or notional) period the dates lie in. */
  periodStart?: string;
  periodEnd?: string;
  frequency?: string | Frequency;
}

/** The year fraction for `[start, end]` under a calendar-free convention. */
export function yearFraction(convention: DayCount, start: string, end: string, options: YearFractionOptions = {}): number {
  switch (convention) {
    case "ACT_360":
    case "THIRTY_360_BOND":
    case "THIRTY_E_360":
      return dayCountDays(convention, start, end) / 360;
    case "ACT_365F":
      return dayCountDays(convention, start, end) / 365;
    case "THIRTY_E_360_ISDA":
      return dayCountDays(convention, start, end, options.endIsTermination ?? false) / 360;
    case "ACT_ACT_ISDA":
      return actActIsda(start, end);
    case "ACT_ACT_ICMA": {
      if (!options.periodStart || !options.periodEnd || !options.frequency) {
        throw new TypeError("ACT/ACT ICMA needs periodStart, periodEnd and frequency");
      }
      const periodDays = daysBetween(options.periodStart, options.periodEnd);
      if (periodDays <= 0) throw new TypeError("ACT/ACT ICMA reference period must be non-empty");
      return daysBetween(start, end) / (frequency(options.frequency).periodsPerYear * periodDays);
    }
    case "BUS_252":
      throw new TypeError("BUS/252 needs a business calendar: use calendar.yearFraction");
  }
}
